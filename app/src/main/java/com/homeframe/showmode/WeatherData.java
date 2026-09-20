package com.homeframe.showmode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeatherData {

    public static class Day {
        public int code;
        public double hi;
        public double lo;
        public String label;
    }

    public static class Hour {
        public int code;
        public double temp;
        public String label;
    }

    public double temp;
    public int code;
    public String city;
    public String condition;
    public int humidity;
    public double windSpeed;
    public String rawJson;

    public final List<Day> days = new ArrayList<>();
    public final List<Hour> hours = new ArrayList<>();

    public static WeatherData parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        WeatherData w = new WeatherData();
        JSONObject current = o.optJSONObject("current");
        if (current == null) current = o.optJSONObject("current_weather");
        if (current != null) {
            w.temp = current.optDouble("temperature_2m", current.optDouble("temperature", Double.NaN));
            w.code = current.optInt("weather_code", current.optInt("weathercode", 0));
            w.humidity = current.optInt("relative_humidity_2m", -1);
            w.windSpeed = current.optDouble("wind_speed_10m", current.optDouble("windspeed", Double.NaN));
        }
        JSONObject daily = o.optJSONObject("daily");
        if (daily != null) {
            JSONArray t = daily.optJSONArray("time");
            JSONArray c = daily.optJSONArray("weather_code");
            if (c == null) c = daily.optJSONArray("weathercode");
            JSONArray hi = daily.optJSONArray("temperature_2m_max");
            JSONArray lo = daily.optJSONArray("temperature_2m_min");
            int n = t == null ? 0 : t.length();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.getDefault());
            for (int i = 0; i < n; i++) {
                Day d = new Day();
                if (c != null && i < c.length()) d.code = c.optInt(i);
                if (hi != null && i < hi.length()) d.hi = hi.optDouble(i);
                if (lo != null && i < lo.length()) d.lo = lo.optDouble(i);
                try {
                    Date date = sdf.parse(t.optString(i));
                    d.label = out.format(date);
                } catch (Exception e) {
                    d.label = "";
                }
                w.days.add(d);
            }
        }
        JSONObject hourly = o.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray t = hourly.optJSONArray("time");
            JSONArray c = hourly.optJSONArray("weather_code");
            JSONArray tmp = hourly.optJSONArray("temperature_2m");
            int n = t == null ? 0 : t.length();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            Calendar cal = Calendar.getInstance();
            int nowHour = cal.get(Calendar.HOUR_OF_DAY);
            int todayDoY = cal.get(Calendar.DAY_OF_YEAR);
            boolean started = false;
            boolean first = true;
            SimpleDateFormat hourFmt = new SimpleDateFormat("h a", Locale.getDefault());
            for (int i = 0; i < n; i++) {
                Hour h = new Hour();
                if (c != null && i < c.length()) h.code = c.optInt(i);
                if (tmp != null && i < tmp.length()) h.temp = tmp.optDouble(i);
                try {
                    cal.setTime(sdf.parse(t.optString(i)));
                } catch (Exception e) {
                    continue;
                }
                if (cal.get(Calendar.DAY_OF_YEAR) != todayDoY) continue;
                int hh = cal.get(Calendar.HOUR_OF_DAY);
                if (!started) {
                    if (hh < nowHour) continue;
                    started = true;
                }
                h.label = first ? "Now" : hourFmt.format(cal.getTime());
                first = false;
                w.hours.add(h);
                if (w.hours.size() >= 6) break;
            }
        }
        return w;
    }
}