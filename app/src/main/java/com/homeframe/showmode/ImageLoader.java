package com.homeframe.showmode;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {

    public interface Callback {
        void onLoaded(String key, Bitmap bmp);
    }

    private static final int MAX_CACHE_BYTES = 64 * 1024 * 1024;

    private final ContentResolver resolver;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> cache;

    public ImageLoader(Context context) {
        this.resolver = context.getApplicationContext().getContentResolver();
        cache = new LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public void load(final String key, final Callback cb) {
        Bitmap cached = cache.get(key);
        if (cached != null && !cached.isRecycled()) {
            cb.onLoaded(key, cached);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp;
                try {
                    bmp = decode(key);
                } catch (Exception e) {
                    final Bitmap f = null;
                    main.post(new Runnable() {
                        public void run() {
                            cb.onLoaded(key, f);
                        }
                    });
                    return;
                }
                if (bmp != null) cache.put(key, bmp);
                final Bitmap result = bmp;
                main.post(new Runnable() {
                    public void run() {
                        cb.onLoaded(key, result);
                    }
                });
            }
        });
    }

    public void clear() {
        cache.evictAll();
    }

    private Bitmap decode(String key) throws Exception {
        Uri uri = Uri.parse(key);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        InputStream is1 = resolver.openInputStream(uri);
        if (is1 == null) return null;
        try {
            BitmapFactory.decodeStream(is1, null, opts);
        } finally {
            is1.close();
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

        int sample = 1;
        while (opts.outWidth / (sample * 2) >= 2048
                || opts.outHeight / (sample * 2) >= 2048) {
            sample *= 2;
        }

        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        InputStream is2 = resolver.openInputStream(uri);
        if (is2 == null) return null;
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeStream(is2, null, opts);
        } finally {
            is2.close();
        }
        if (bmp == null) return null;

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
            if (pfd != null) {
                try {
                    FileDescriptor fd = pfd.getFileDescriptor();
                    if (Build.VERSION.SDK_INT >= 24) {
                        orientation = new ExifInterface(fd)
                                .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                        ExifInterface.ORIENTATION_NORMAL);
                    } else {
                        InputStream is3 = new FileInputStream(fd);
                        try {
                            orientation = new ExifInterface(is3)
                                    .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                            ExifInterface.ORIENTATION_NORMAL);
                        } finally {
                            is3.close();
                        }
                    }
                } finally {
                    pfd.close();
                }
            }
        } catch (Exception ignored) {
        }

        Matrix m = null;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                m = new Matrix();
                m.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m = new Matrix();
                m.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m = new Matrix();
                m.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m = new Matrix();
                m.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m = new Matrix();
                m.postScale(1, -1);
                break;
            default:
                break;
        }
        if (m != null) {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        return bmp;
    }
}