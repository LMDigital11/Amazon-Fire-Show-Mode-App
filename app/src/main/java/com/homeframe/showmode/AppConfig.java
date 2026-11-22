package com.homeframe.showmode;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;

public final class AppConfig {

    private static final String PREFS = "showmode";
    private static final String KEY_PHOTOS = "photos";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_SPEED = "speed_s";
    private static final String KEY_FREQ = "weather_freq";
    private static final String KEY_DUR = "weather_dur_s";
    private static final String KEY_CITY = "city";
    private static final String KEY_UNIT_C = "use_celsius";
    private static final String KEY_WX_JSON = "wx_json";
    private static final String KEY_WX_TIME = "wx_time";
    private static final String KEY_BLANK_MIN = "blank_min";
    private static final String KEY_MOTION_WAKE = "motion_wake";
    private static final String KEY_SLEEP_ENABLED = "sleep_enabled";
    private static final String KEY_SLEEP_START = "sleep_start";
    private static final String KEY_SLEEP_END = "sleep_end";
    private static final String KEY_UPDATE_URL = "update_url";

    public static final int SPEED_MIN = 5;
    public static final int SPEED_MAX = 120;
    public static final int FREQ_MIN = 1;
    public static final int FREQ_MAX = 10;
    public static final int DUR_MIN = 5;
    public static final int DUR_MAX = 30;
    public static final int BLANK_MIN = 1;
    public static final int BLANK_MAX = 120;
    public static final int DEFAULT_SPEED = 20;
    public static final int DEFAULT_FREQ = 4;
    public static final int DEFAULT_DUR = 10;
    public static final int DEFAULT_BLANK_MIN = 30;
    public static final int DEFAULT_SLEEP_START = 22 * 60;
    public static final int DEFAULT_SLEEP_END = 7 * 60;

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<String> getPhotoUris(Context c) {
        String s = p(c).getString(KEY_PHOTOS, "");
        List<String> out = new ArrayList<>();
        if (!s.isEmpty()) {
            for (String part : s.split("\u0001", -1)) {
                if (!part.isEmpty()) out.add(part);
            }
        }
        return out;
    }

    public static void setPhotos(Context c, List<String> uris) {
        StringBuilder sb = new StringBuilder();
        for (String u : uris) {
            if (sb.length() > 0) sb.append('\u0001');
            sb.append(u);
        }
        p(c).edit().putString(KEY_PHOTOS, sb.toString()).apply();
    }

    public static List<String> getFolders(Context c) {
        return split(p(c).getString(KEY_FOLDERS, ""));
    }

    public static void setFolders(Context c, List<String> folders) {
        StringBuilder sb = new StringBuilder();
        for (String f : folders) {
            if (sb.length() > 0) sb.append('\u0001');
            sb.append(f);
        }
        p(c).edit().putString(KEY_FOLDERS, sb.toString()).apply();
    }

    public static List<String> getAllPhotoUris(Context c) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.addAll(getPhotoUris(c));
        for (String f : getFolders(c)) {
            out.addAll(FolderScanner.collectImages(c, f));
        }
        return new ArrayList<>(out);
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (String part : s.split("\u0001", -1)) {
                if (!part.isEmpty()) out.add(part);
            }
        }
        return out;
    }

    public static int getSpeedSeconds(Context c) {
        return p(c).getInt(KEY_SPEED, DEFAULT_SPEED);
    }

    public static void setSpeedSeconds(Context c, int v) {
        p(c).edit().putInt(KEY_SPEED, v).apply();
    }

    public static int getWeatherFreq(Context c) {
        return p(c).getInt(KEY_FREQ, DEFAULT_FREQ);
    }

    public static void setWeatherFreq(Context c, int v) {
        p(c).edit().putInt(KEY_FREQ, v).apply();
    }

    public static int getWeatherDuration(Context c) {
        return p(c).getInt(KEY_DUR, DEFAULT_DUR);
    }

    public static void setWeatherDuration(Context c, int v) {
        p(c).edit().putInt(KEY_DUR, v).apply();
    }

    public static String getCity(Context c) {
        return p(c).getString(KEY_CITY, "");
    }

    public static void setCity(Context c, String v) {
        p(c).edit().putString(KEY_CITY, v == null ? "" : v.trim()).apply();
    }

    public static boolean isCelsius(Context c) {
        return p(c).getBoolean(KEY_UNIT_C, true);
    }

    public static void setCelsius(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_UNIT_C, v).apply();
    }

    public static String getWeatherJson(Context c) {
        return p(c).getString(KEY_WX_JSON, null);
    }

    public static void setWeatherJson(Context c, String json) {
        p(c).edit().putString(KEY_WX_JSON, json)
                .putLong(KEY_WX_TIME, System.currentTimeMillis()).apply();
    }

public static long getWeatherTime(Context c) {
        return p(c).getLong(KEY_WX_TIME, 0);
    }

    public static int getBlankMinutes(Context c) {
        int v = p(c).getInt(KEY_BLANK_MIN, DEFAULT_BLANK_MIN);
        if (v < BLANK_MIN) v = BLANK_MIN;
        if (v > BLANK_MAX) v = BLANK_MAX;
        return v;
    }

    public static void setBlankMinutes(Context c, int v) {
        p(c).edit().putInt(KEY_BLANK_MIN, v).apply();
    }

    public static boolean isMotionWake(Context c) {
        return p(c).getBoolean(KEY_MOTION_WAKE, true);
    }

    public static void setMotionWake(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_MOTION_WAKE, v).apply();
    }

    public static boolean isSleepEnabled(Context c) {
        return p(c).getBoolean(KEY_SLEEP_ENABLED, false);
    }

    public static void setSleepEnabled(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_SLEEP_ENABLED, v).apply();
    }

    public static int getSleepStart(Context c) {
        return p(c).getInt(KEY_SLEEP_START, DEFAULT_SLEEP_START);
    }

    public static void setSleepStart(Context c, int minutes) {
        p(c).edit().putInt(KEY_SLEEP_START, minutes).apply();
    }

    public static int getSleepEnd(Context c) {
        return p(c).getInt(KEY_SLEEP_END, DEFAULT_SLEEP_END);
    }

    public static void setSleepEnd(Context c, int minutes) {
        p(c).edit().putInt(KEY_SLEEP_END, minutes).apply();
    }

    public static final String DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/LMDigital11/Amazon-Fire-Show-Mode-App/main/version.json";

    public static String getUpdateUrl(Context c) {
        String v = p(c).getString(KEY_UPDATE_URL, "");
        return v.isEmpty() ? DEFAULT_UPDATE_URL : v;
    }

    public static void setUpdateUrl(Context c, String v) {
        p(c).edit().putString(KEY_UPDATE_URL, v == null ? "" : v.trim()).apply();
    }

    public static boolean isInSleepWindow(Context c) {
        if (!isSleepEnabled(c)) return false;
        int now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        int start = getSleepStart(c);
        int end = getSleepEnd(c);
        if (start == end) return false;
        if (start < end) return now >= start && now < end;
        return now >= start || now < end;
    }

    private AppConfig() {
    }
}