package com.homeframe.showmode;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements ImageLoader.Callback, SensorEventListener {

    private static final int CONFIG_REQUEST = 1001;
    private static final long CROSSFADE_MS = 700;
    private static final long IDLE_TICK_MS = 15_000L;
    private static final long SLEEP_WAKE_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private ImageView imageA, imageB;
    private TextView clockTime, clockDate, clockTemp, weatherTemp, weatherCity, weatherDesc,
            weatherExtra, weatherStatus, hintText;
    private FrameLayout photoContainer, weatherPage;
    private View clockBox, emptyState, btnOpenConfig, blankCover;
    private LinearLayout forecastRow, hourlyRow;
    private WeatherGlyphView glyphCurrent;

    private ImageLoader imageLoader;
    private final List<String> photoUris = new ArrayList<>();
    private int photoIndex = -1;
    private ImageView showing;
    private String pendingKey;
    private int countSinceWeather;
    private boolean paused = true;
    private boolean swipeOpening;
    private boolean ignoringGesture;
    private boolean edgeStart;
    private float downX, downY;
    private float edgeZonePx;
    private int tapCount;
    private long lastTapTime;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastActivity;
    private long lastMotion;
    private boolean blanked;
    private boolean sleepWake;

    private GestureDetector gesture;

    private PresenceDetector presence;

    private final Runnable progress = new Runnable() {
        @Override
        public void run() {
            nextStep();
        }
    };

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            if (!paused) {
                updateClock();
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private final Runnable idleTick = new Runnable() {
        @Override
        public void run() {
            if (paused) return;
            long now = System.currentTimeMillis();
            if (AppConfig.isInSleepWindow(getBaseContext())) {
                if (sleepWake) {
                    if (now - lastActivity >= SLEEP_WAKE_MS) {
                        sleepWake = false;
                        blankScreen();
                    }
                } else if (!blanked) {
                    blankScreen();
                }
            } else {
                if (sleepWake) sleepWake = false;
                long blankMs = AppConfig.getBlankMinutes(getBaseContext()) * 60_000L;
                if (!blanked) {
                    if (now - lastActivity >= blankMs) blankScreen();
                } else if (AppConfig.isMotionWake(getBaseContext())) {
                    if (now - lastMotion < 20_000L) wakeScreen();
                }
            }
            handler.postDelayed(this, IDLE_TICK_MS);
        }
    };

    private final Runnable closeWeather = new Runnable() {
        @Override
        public void run() {
            if (paused) return;
            weatherPage.setVisibility(View.GONE);
            hideHint();
            photoContainer.setVisibility(View.VISIBLE);
            clockBox.setVisibility(View.VISIBLE);
            handler.post(progress);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        photoContainer = findViewById(R.id.photoContainer);
        imageA = findViewById(R.id.imageA);
        imageB = findViewById(R.id.imageB);
        clockBox = findViewById(R.id.clockBox);
        clockTime = findViewById(R.id.clockTime);
        clockDate = findViewById(R.id.clockDate);
        clockTemp = findViewById(R.id.clockTemp);
        emptyState = findViewById(R.id.emptyState);
        btnOpenConfig = findViewById(R.id.btnOpenConfig);
        hintText = findViewById(R.id.hintText);
        weatherPage = findViewById(R.id.weatherPage);
        glyphCurrent = findViewById(R.id.glyphCurrent);
        weatherTemp = findViewById(R.id.weatherTemp);
        weatherCity = findViewById(R.id.weatherCity);
        weatherDesc = findViewById(R.id.weatherDesc);
        weatherExtra = findViewById(R.id.weatherExtra);
        weatherStatus = findViewById(R.id.weatherStatus);
        forecastRow = findViewById(R.id.forecastRow);
        hourlyRow = findViewById(R.id.hourlyRow);
        blankCover = findViewById(R.id.blankCover);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        imageLoader = new ImageLoader(this);

        gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                if (ignoringGesture) return false;
                if (e1.getX() >= root.getWidth() - edgeZonePx && e2.getX() < e1.getX()) {
                    openConfig();
                    return true;
                }
                return false;
            }
        });

        ViewConfiguration vc = ViewConfiguration.get(this);
        final float touchSlop = vc.getScaledTouchSlop();

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                gesture.onTouchEvent(ev);
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = ev.getX();
                        downY = ev.getY();
                        edgeStart = ev.getX() >= root.getWidth() - edgeZonePx;
                        if (blanked) {
                            ignoringGesture = true;
                            if (AppConfig.isInSleepWindow(MainActivity.this)) {
                                sleepWake = true;
                                lastActivity = System.currentTimeMillis();
                            }
                            wakeScreen();
                        } else {
                            markActivity();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (ignoringGesture) {
                            ignoringGesture = false;
                            return false;
                        }
                        float dx = ev.getX() - downX;
                        float dy = ev.getY() - downY;
                        if (edgeStart && dx < -Math.max(dp(50), touchSlop * 5)
                                && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                            openConfig();
                        }
                        break;
                    default:
                        break;
                }
                return false;
            }
        });

        btnOpenConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openConfig();
            }
        });

        clockBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime > 700) tapCount = 0;
                lastTapTime = now;
                tapCount++;
                if (tapCount >= 3) {
                    tapCount = 0;
                    openConfig();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        swipeOpening = false;
        ignoringGesture = false;
        paused = false;
        markActivity();
        wakeScreen();
        registerListener();
        requestCameraPermissionIfNeeded();
        imageLoader.clear();
        edgeZonePx = dp(90);
        startShow();
        handler.removeCallbacks(clock);
        handler.post(clock);
        handler.removeCallbacks(idleTick);
        handler.postDelayed(idleTick, IDLE_TICK_MS);
        hideSystemUi();
        primeWeather();
    }

    @Override
    protected void onPause() {
        paused = true;
        handler.removeCallbacksAndMessages(null);
        unregisterListener();
        stopPresence();
        if (blanked) {
            blanked = false;
            blankCover.setVisibility(View.GONE);
        }
        WindowManager.LayoutParams a = getWindow().getAttributes();
        if (a.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            getWindow().setAttributes(a);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (imageLoader != null) imageLoader.clear();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void registerListener() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void unregisterListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double mag = Math.sqrt(x * x + y * y + z * z);
        if (Math.abs(mag - SensorManager.GRAVITY_EARTH) > 1.2) {
            long now = System.currentTimeMillis();
            lastMotion = now;
            if (blanked && !sleepWake && !AppConfig.isInSleepWindow(this)
                    && AppConfig.isMotionWake(this)) {
                wakeScreen();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void markActivity() {
        lastActivity = System.currentTimeMillis();
    }

    private void blankScreen() {
        if (blanked) return;
        blanked = true;
        blankCover.setVisibility(View.VISIBLE);
        WindowManager.LayoutParams a = getWindow().getAttributes();
        a.screenBrightness = 0f;
        getWindow().setAttributes(a);
        startPresence();
    }

    private void wakeScreen() {
        if (!blanked) return;
        blanked = false;
        blankCover.setVisibility(View.GONE);
        WindowManager.LayoutParams a = getWindow().getAttributes();
        a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(a);
        markActivity();
        stopPresence();
    }

    private void requestCameraPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 200);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startPresence() {
        if (!AppConfig.isMotionWake(this) || presence != null) return;
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        PresenceDetector d = new PresenceDetector(this, new PresenceDetector.Listener() {
            @Override
            public void onPresence() {
                long now = System.currentTimeMillis();
                lastMotion = now;
                if (blanked && !AppConfig.isInSleepWindow(MainActivity.this)) {
                    wakeScreen();
                }
            }
        });
        presence = d;
        d.start();
    }

    private void stopPresence() {
        if (presence == null) return;
        presence.stop();
        presence = null;
    }

    private void openConfig() {
        if (swipeOpening) return;
        swipeOpening = true;
        try {
            startActivityForResult(new Intent(this, ConfigActivity.class), CONFIG_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void startShow() {
        handler.removeCallbacks(progress);
        photoUris.clear();
        photoUris.addAll(AppConfig.getAllPhotoUris(this));

        weatherPage.setVisibility(View.GONE);
        photoContainer.setVisibility(View.VISIBLE);
        clockBox.setVisibility(View.VISIBLE);

        showing = null;
        pendingKey = null;
        imageA.setImageDrawable(null);
        imageB.setImageDrawable(null);
        imageA.setVisibility(View.VISIBLE);
        imageB.setVisibility(View.GONE);

        if (photoUris.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            photoContainer.setVisibility(View.GONE);
            return;
        }
        emptyState.setVisibility(View.GONE);

        photoIndex = (int) (Math.random() * photoUris.size());
        countSinceWeather = 0;
        showHint();
        nextStep();
    }

    private void showHint() {
        hintText.setVisibility(View.VISIBLE);
        hintText.animate().alpha(1f).setDuration(400).start();
        Runnable fade = new Runnable() {
            @Override
            public void run() {
                hintText.animate().alpha(0f).setDuration(800).start();
            }
        };
        handler.removeCallbacksAndMessages(hintText.getTag());
        hintText.setTag(fade);
        handler.postDelayed(fade, 7000L);
    }

    private void hideHint() {
        hintText.animate().alpha(0f).setDuration(200).start();
    }

    private void nextStep() {
        if (paused || photoUris.isEmpty()) return;
        hideHint();

        photoIndex = (photoIndex + 1) % photoUris.size();
        String key = photoUris.get(photoIndex);
        String nextKey = photoUris.get((photoIndex + 1) % photoUris.size());
        crossfadeTo(key);
        imageLoader.load(nextKey, this);

        countSinceWeather++;
        if (countSinceWeather >= AppConfig.getWeatherFreq(this)) {
            countSinceWeather = 0;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    openWeather();
                }
            }, AppConfig.getSpeedSeconds(this) * 1000L);
        } else {
            handler.postDelayed(progress, AppConfig.getSpeedSeconds(this) * 1000L);
        }
    }

    private void crossfadeTo(String key) {
        ImageView to = (showing == imageA) ? imageB : imageA;
        pendingKey = key;
        to.setVisibility(View.VISIBLE);
        to.setAlpha(0f);
        imageLoader.load(key, this);
    }

    @Override
    public void onLoaded(String key, Bitmap bmp) {
        if (paused) return;
        if (!key.equals(pendingKey)) return;
        if (bmp == null) {
            removeBadPhoto(key);
            return;
        }
        ImageView to = (showing == imageA) ? imageB : imageA;
        ImageView from = showing;

        to.setImageBitmap(bmp);
        to.setAlpha(0f);
        to.animate().alpha(1f).setDuration(CROSSFADE_MS).start();

        if (from != null) {
            final ImageView old = from;
            old.animate().alpha(0f).setDuration(CROSSFADE_MS)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            old.setAlpha(1f);
                            old.setImageDrawable(null);
                            old.setVisibility(View.GONE);
                        }
                    }).start();
        }
        showing = to;
    }

    private void removeBadPhoto(String key) {
        int idx = photoUris.indexOf(key);
        if (idx >= 0) {
            photoUris.remove(idx);
            AppConfig.setPhotos(this, photoUris);
            if (photoUris.size() > 0) photoIndex = photoIndex % photoUris.size();
        }
        if (photoUris.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            photoContainer.setVisibility(View.GONE);
            return;
        }
        handler.post(progress);
    }

    private void openWeather() {
        if (paused) return;
        clockBox.setVisibility(View.GONE);
        hideHint();
        photoContainer.setVisibility(View.GONE);
        weatherPage.setVisibility(View.VISIBLE);
        refreshWeather();
        handler.postDelayed(closeWeather, AppConfig.getWeatherDuration(this) * 1000L);
    }

    private void primeWeather() {
        final String city = AppConfig.getCity(this);
        if (city.isEmpty()) return;
        long age = System.currentTimeMillis() - AppConfig.getWeatherTime(this);
        String cached = AppConfig.getWeatherJson(this);
        if (cached != null && age < WeatherFetcher.CACHE_MS) {
            try {
                WeatherData w = WeatherData.parse(cached);
                setClockTemp((int) Math.round(w.temp));
            } catch (Exception ignored) {
            }
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean celsius = AppConfig.isCelsius(MainActivity.this);
                    final WeatherData w = WeatherFetcher.fetch(city, celsius);
                    AppConfig.setWeatherJson(MainActivity.this, w.rawJson);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setClockTemp((int) Math.round(w.temp));
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void refreshWeather() {
        final String city = AppConfig.getCity(this);
        final boolean celsius = AppConfig.isCelsius(this);
        long age = System.currentTimeMillis() - AppConfig.getWeatherTime(this);
        String cached = AppConfig.getWeatherJson(this);

        if (city.isEmpty()) {
            weatherStatus.setVisibility(View.VISIBLE);
            weatherStatus.setText("Set your city in Settings to see the weather.");
            hideForecast();
            return;
        }

        if (cached != null && age < WeatherFetcher.CACHE_MS) {
            try {
                applyWeather(WeatherData.parse(cached), capitalize(city));
            } catch (Exception ignored) {
            }
            return;
        }

        weatherStatus.setVisibility(View.VISIBLE);
        weatherStatus.setText("Loading weather\u2026");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final WeatherData w = WeatherFetcher.fetch(city, celsius);
                    AppConfig.setWeatherJson(MainActivity.this, w.rawJson);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            applyWeather(w, w.city);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            weatherStatus.setVisibility(View.VISIBLE);
                            weatherStatus.setText("Weather unavailable\n" + e.getMessage());
                            hideForecast();
                        }
                    });
                }
            }
        }).start();
    }

    private void applyWeather(WeatherData w, String cityName) {
        weatherStatus.setVisibility(View.GONE);
        glyphCurrent.setWmoCode(w.code);
        weatherTemp.setText(String.format(Locale.getDefault(), "%d\u00b0", Math.round(w.temp)));
        weatherCity.setText(cityName);
        weatherDesc.setText(w.condition);
        setClockTemp((int) Math.round(w.temp));
        StringBuilder extra = new StringBuilder();
        if (w.humidity >= 0) extra.append("Humidity ").append(w.humidity).append("%");
        if (!Double.isNaN(w.windSpeed)) {
            if (extra.length() > 0) extra.append("  \u00b7  ");
            extra.append("Wind ").append(Math.round(w.windSpeed));
            extra.append(AppConfig.isCelsius(this) ? " km/h" : " mph");
        }
        weatherExtra.setText(extra.toString());

        hourlyRow.removeAllViews();
        int nh = Math.min(w.hours.size(), 6);
        for (int i = 0; i < nh; i++) {
            WeatherData.Hour h = w.hours.get(i);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(lp);

            TextView label = new TextView(this);
            label.setText(h.label);
            label.setTextColor(0xCCFFFFFF);
            label.setTextSize(15);
            col.addView(label);

            WeatherGlyphView g = new WeatherGlyphView(this);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(40));
            glp.topMargin = dp(4);
            g.setLayoutParams(glp);
            g.setWmoCode(h.code);
            col.addView(g);

            TextView temp = new TextView(this);
            temp.setText(String.format(Locale.getDefault(), "%d\u00b0", Math.round(h.temp)));
            temp.setTextColor(0xE6FFFFFF);
            temp.setTextSize(16);
            col.addView(temp);

            hourlyRow.addView(col);
        }
        hourlyRow.setVisibility(nh > 0 ? View.VISIBLE : View.GONE);

        forecastRow.removeAllViews();
        int n = Math.min(w.days.size(), 5);
        for (int i = 0; i < n; i++) {
            WeatherData.Day d = w.days.get(i);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(lp);

            TextView day = new TextView(this);
            day.setText(i == 0 ? "Today" : d.label);
            day.setTextColor(0xCCFFFFFF);
            day.setTextSize(15);
            col.addView(day);

            WeatherGlyphView g = new WeatherGlyphView(this);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    dp(48), dp(48));
            glp.topMargin = dp(6);
            g.setLayoutParams(glp);
            g.setWmoCode(d.code);
            col.addView(g);

            TextView temp = new TextView(this);
            temp.setText(String.format(Locale.getDefault(), "%d\u00b0  %d\u00b0",
                    Math.round(d.hi), Math.round(d.lo)));
            temp.setTextColor(0xE6FFFFFF);
            temp.setTextSize(15);
            col.addView(temp);

            forecastRow.addView(col);
        }
    }

    private void setClockTemp(int temp) {
        String t = String.format(Locale.getDefault(), "%d\u00b0", temp);
        if (clockTemp != null) clockTemp.setText(t);
    }

    private void hideForecast() {
        forecastRow.removeAllViews();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        boolean use24 = android.text.format.DateFormat.is24HourFormat(this);
        String pat = use24 ? "HH:mm" : "h:mm";
        clockTime.setText(new SimpleDateFormat(pat, Locale.getDefault()).format(cal.getTime()));
        clockDate.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(cal.getTime()));
    }
}