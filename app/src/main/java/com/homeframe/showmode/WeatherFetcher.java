package com.homeframe.showmode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WeatherFetcher {

    public static final long CACHE_MS = 30L * 60L * 1000L;

    public static WeatherData fetch(String city, boolean celsius) throws Exception {
        String[] loc = geocode(city);
        String unit = celsius ? "celsius" : "fahrenheit";
        String wind = celsius ? "kmh" : "mph";
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + loc[0]
                + "&longitude=" + loc[1]
                + "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m"
                + "&hourly=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=5"
                + "&temperature_unit=" + unit
                + "&wind_speed_unit=" + wind;
        String json = httpGet(url);
        WeatherData w = WeatherData.parse(json);
        w.city = loc[2];
        w.condition = conditionText(w.code);
        w.rawJson = json;
        return w;
    }

    public static String conditionText(int code) {
        if (code == 0) return "Clear";
        if (code == 1 || code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 55) return "Drizzle";
        if (code == 56 || code == 57) return "Freezing drizzle";
        if (code >= 61 && code <= 63) return "Light rain";
        if (code == 64 || code == 65) return "Heavy rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 71 && code <= 75) return "Snow";
        if (code == 77) return "Snow grains";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    private static String[] geocode(String city) throws Exception {
        String enc = URLEncoder.encode(city, "UTF-8");
        String json = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=" + enc
                + "&count=1&language=en&format=json");
        JSONObject o = new JSONObject(json);
        JSONArray results = o.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new IOException("City \"" + city + "\" wasn't found.");
        }
        JSONObject r = results.getJSONObject(0);
        String lat = r.getString("latitude");
        String lon = r.getString("longitude");
        String name = r.getString("name");
        String region = r.optString("admin1", "");
        String country = r.optString("country", "");
        String display = name;
        if (!region.isEmpty() && !region.equals(name)) display = name + ", " + region;
        else if (!country.isEmpty()) display = name + ", " + country;
        return new String[]{lat, lon, display};
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "ShowMode/1.0");
        int code;
        try {
            code = c.getResponseCode();
        } catch (IOException e) {
            c.disconnect();
            throw new IOException("No internet connection.");
        }
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) {
            c.disconnect();
            throw new IOException("Weather service error " + code);
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        if (code >= 400) throw new IOException("Weather service error " + code);
        return sb.toString();
    }

    private WeatherFetcher() {
    }
}