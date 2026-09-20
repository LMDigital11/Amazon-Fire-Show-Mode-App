package com.homeframe.showmode;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

public class PresenceDetector {

    public interface Listener {
        void onPresence();
    }

    private static final String TAG = "PresenceDetector";
    private static final int SAMPLE_W = 12;
    private static final int SAMPLE_H = 9;
    private static final int DIFF_THRESHOLD = 3;
    private static final int MIN_FRAMES_MOTION = 2;
    private static final long COOLDOWN_MS = 2500L;

    private final Listener listener;
    private final int cameraId;
    private HandlerThread thread;
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Camera camera;
    private boolean running;
    private byte[] prevLuma;
    private int streak;
    private long lastFire;

    public PresenceDetector(Context context, Listener listener) {
        this.listener = listener;
        this.cameraId = pickCamera(context);
    }

    private static int pickCamera(Context context) {
        int front = -1;
        try {
            int n = Camera.getNumberOfCameras();
            for (int i = 0; i < n; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    front = i;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        if (front >= 0) return front;
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                for (String id : cm.getCameraIdList()) {
                    if (id.contains("front") || id.contains("0")) return Integer.parseInt(id);
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new HandlerThread("presence-detector");
        thread.start();
        bg = new Handler(thread.getLooper());
        bg.post(new Runnable() {
            @Override
            public void run() {
                openCamera();
            }
        });
    }

    private void openCamera() {
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters p = camera.getParameters();
            Camera.Size size = chooseSize(p);
            p.setPreviewSize(size.width, size.height);
            p.setPreviewFormat(ImageFormat.NV21);
            try {
                camera.setParameters(p);
            } catch (Throwable ignored) {
            }
            prevLuma = null;
            streak = 0;
            camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera cam) {
                    analyze(data, size.width, size.height);
                }
            });
            int bufferSize = size.width * size.height
                    * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            int buffers = Math.max(2, Runtime.getRuntime().availableProcessors());
            for (int i = 0; i < buffers; i++) {
                camera.addCallbackBuffer(new byte[bufferSize]);
            }
            camera.startPreview();
        } catch (Throwable t) {
            Log.w(TAG, "Camera unavailable: " + t.getMessage());
            releaseCamera();
        }
    }

    private Camera.Size chooseSize(Camera.Parameters p) {
        Camera.Size best = null;
        for (Camera.Size s : p.getSupportedPreviewSizes()) {
            if (best == null) {
                best = s;
                continue;
            }
            if (s.width >= 320 && best.width > s.width) {
                best = s;
            } else if (s.width < 320 && s.width > best.width) {
                best = s;
            }
        }
        if (best == null) best = p.getPreviewSize();
        return best;
    }

    private void analyze(byte[] nv21, int w, int h) {
        if (!running) return;
        Camera cam = camera;
        if (cam == null) return;
        cam.addCallbackBuffer(nv21);

        byte[] cur = downSampleLuma(nv21, w, h);
        if (prevLuma == null) {
            prevLuma = cur;
            return;
        }
        float diff = meanAbsDiff(prevLuma, cur);
        prevLuma = cur;
        if (diff > DIFF_THRESHOLD) {
            streak++;
        } else {
            streak = 0;
        }
        if (streak >= MIN_FRAMES_MOTION) {
            streak = 0;
            long now = System.currentTimeMillis();
            if (now - lastFire >= COOLDOWN_MS) {
                lastFire = now;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) listener.onPresence();
                    }
                });
            }
        }
    }

    private byte[] downSampleLuma(byte[] nv21, int w, int h) {
        byte[] out = new byte[SAMPLE_W * SAMPLE_H];
        int y = 0;
        for (int row = 0; row < SAMPLE_H; row++) {
            int sy = (h - 1) * row / Math.max(1, SAMPLE_H - 1);
            int base = sy * w;
            for (int col = 0; col < SAMPLE_W; col++) {
                int sx = (w - 1) * col / Math.max(1, SAMPLE_W - 1);
                out[y++] = nv21[base + sx];
            }
        }
        return out;
    }

    private float meanAbsDiff(byte[] a, byte[] b) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
        }
        return sum / (float) a.length;
    }

    public void stop() {
        if (!running) return;
        running = false;
        if (bg != null) {
            bg.post(new Runnable() {
                @Override
                public void run() {
                    releaseCamera();
                }
            });
        }
        thread.quitSafely();
        thread = null;
        bg = null;
    }

    private void releaseCamera() {
        try {
            if (camera != null) {
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
                camera.release();
            }
        } catch (Throwable ignored) {
        }
        camera = null;
    }
}