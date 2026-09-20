package com.homeframe.showmode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class WeatherGlyphView extends View {

    public static final int G_SUN = 0;
    public static final int G_PARTLY = 1;
    public static final int G_CLOUD = 2;
    public static final int G_FOG = 3;
    public static final int G_RAIN = 4;
    public static final int G_SNOW = 5;
    public static final int G_THUNDER = 6;

    private int glyph = G_SUN;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public WeatherGlyphView(Context c) {
        this(c, null);
    }

    public WeatherGlyphView(Context c, AttributeSet a) {
        super(c, a);
        fill.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setWmoCode(int code) {
        glyph = map(code);
        invalidate();
    }

    public void setGlyph(int g) {
        glyph = g;
        invalidate();
    }

    public static int map(int code) {
        if (code == 0) return G_SUN;
        if (code == 1 || code == 2) return G_PARTLY;
        if (code == 3) return G_CLOUD;
        if (code == 45 || code == 48) return G_FOG;
        if (code == 51 || code == 53 || code == 55 || code == 56 || code == 57
                || code == 61 || code == 63 || code == 65 || code == 66 || code == 67
                || code == 80 || code == 81 || code == 82) return G_RAIN;
        if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) {
            return G_SNOW;
        }
        if (code >= 95) return G_THUNDER;
        return G_CLOUD;
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        switch (glyph) {
            case G_SUN:
                drawSun(c, s);
                break;
            case G_PARTLY:
                drawPartly(c, s);
                break;
            case G_CLOUD:
                drawCloud(c, s, s * 0.5f, s * 0.54f, s * 0.30f);
                break;
            case G_FOG:
                drawFog(c, s);
                break;
            case G_RAIN:
                drawCloud(c, s, s * 0.5f, s * 0.42f, s * 0.26f);
                drawRain(c, s, s * 0.5f, s * 0.52f);
                break;
            case G_SNOW:
                drawCloud(c, s, s * 0.5f, s * 0.42f, s * 0.26f);
                drawSnow(c, s, s * 0.5f, s * 0.58f);
                break;
            case G_THUNDER:
                drawCloud(c, s, s * 0.5f, s * 0.40f, s * 0.24f);
                drawBolt(c, s);
                break;
            default:
                drawSun(c, s);
                break;
        }
    }

    private void drawSun(Canvas c, float s) {
        float cx = s * 0.5f;
        float cy = s * 0.48f;
        float r = s * 0.17f;
        c.drawCircle(cx, cy, r, fill);
        stroke.setStrokeWidth(s * 0.045f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            c.drawLine(
                    cx + (float) Math.cos(a) * (r + s * 0.06f),
                    cy + (float) Math.sin(a) * (r + s * 0.06f),
                    cx + (float) Math.cos(a) * (r + s * 0.16f),
                    cy + (float) Math.sin(a) * (r + s * 0.16f),
                    stroke);
        }
    }

    private void drawCloud(Canvas c, float s, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx - r * 0.95f, cy + r * 0.32f, r * 0.62f, fill);
        c.drawCircle(cx + r * 0.95f, cy + r * 0.34f, r * 0.72f, fill);
        float botY = cy + r * 0.60f;
        path.reset();
        path.moveTo(cx - r * 1.65f, botY);
        path.lineTo(cx + r * 1.65f, botY);
        path.lineTo(cx + r * 1.65f, botY + r * 0.30f);
        path.lineTo(cx - r * 1.65f, botY + r * 0.30f);
        path.close();
        c.drawPath(path, fill);
    }

    private void drawRain(Canvas c, float s, float cx, float top) {
        stroke.setStrokeWidth(s * 0.042f);
        for (int i = -1; i <= 1; i++) {
            float x = cx + i * s * 0.15f;
            c.drawLine(x, top + s * 0.10f, x - s * 0.04f, top + s * 0.26f, stroke);
        }
    }

    private void drawSnow(Canvas c, float s, float cx, float top) {
        stroke.setStrokeWidth(s * 0.04f);
        for (int i = -1; i <= 1; i++) {
            float x = cx + i * s * 0.15f;
            c.drawCircle(x, top + s * 0.12f, Math.max(2f, s * 0.035f), fill);
        }
    }

    private void drawFog(Canvas c, float s) {
        stroke.setStrokeWidth(s * 0.035f);
        float base = s * 0.42f;
        for (int i = 0; i < 3; i++) {
            float y = base + i * s * 0.16f;
            float len = s * 0.52f - i * s * 0.06f;
            c.drawLine(s * 0.5f - len / 2, y, s * 0.5f + len / 2, y, stroke);
        }
    }

    private void drawBolt(Canvas c, float s) {
        path.reset();
        path.moveTo(s * 0.56f, s * 0.58f);
        path.lineTo(s * 0.40f, s * 0.74f);
        path.lineTo(s * 0.51f, s * 0.74f);
        path.lineTo(s * 0.44f, s * 0.92f);
        path.lineTo(s * 0.64f, s * 0.66f);
        path.lineTo(s * 0.52f, s * 0.66f);
        path.close();
        c.drawPath(path, fill);
    }

    private void drawPartly(Canvas c, float s) {
        drawSunSmall(c, s * 0.34f, s * 0.32f, s * 0.13f);
        drawCloud(c, s, s * 0.5f, s * 0.60f, s * 0.25f);
    }

    private void drawSunSmall(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, fill);
        stroke.setStrokeWidth(r * 0.26f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            c.drawLine(
                    cx + (float) Math.cos(a) * (r + r * 0.30f),
                    cy + (float) Math.sin(a) * (r + r * 0.30f),
                    cx + (float) Math.cos(a) * (r + r * 0.85f),
                    cy + (float) Math.sin(a) * (r + r * 0.85f),
                    stroke);
        }
    }
}