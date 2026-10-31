package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Icons drawn as vectors, as in the home screen mockups: outlined gear, apps grid, sliders, TV and HDMI port. Each one
 * fits a square of `size` centered on (cx, cy).
 */
final class Icons {
    private Icons() {}

    private static final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path(), path2 = new Path();
    private static final RectF r = new RectF();

    static {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    /** Draws the icon for a shortcut or input symbol; false when there is no vector version (use Symbols). */
    static boolean draw(Canvas c, String symbol, float cx, float cy, float size, int color) {
        switch (symbol == null ? "" : symbol) {
            case "settings": gear(c, cx, cy, size, color); return true;
            case "apps": grid(c, cx, cy, size, color); return true;
            case "tv_settings": sliders(c, cx, cy, size, color); return true;
            case "tv": tv(c, cx, cy, size, color); return true;
            case "hdmi": hdmi(c, cx, cy, size, color); return true;
            default: return false;
        }
    }

    /** Outlined gear (eight teeth, a hole in the middle). */
    static void gear(Canvas c, float cx, float cy, float size, int color) {
        float u = size / 24f;
        path.rewind();
        path.addCircle(cx, cy, 7.2f * u, Path.Direction.CW);
        for (int i = 0; i < 8; i++) {
            path2.rewind();
            r.set(cx - 1.9f * u, cy - 10.6f * u, cx + 1.9f * u, cy - 5.5f * u);
            path2.addRoundRect(r, 1f * u, 1f * u, Path.Direction.CW);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRotate(i * 45, cx, cy);
            path2.transform(m);
            path.op(path2, Path.Op.UNION);
        }
        stroke.setColor(color);
        stroke.setStrokeWidth(1.9f * u);
        c.drawPath(path, stroke);
        c.drawCircle(cx, cy, 3.2f * u, stroke);
    }

    /** Apps: a 3 × 3 grid of rounded squares. */
    static void grid(Canvas c, float cx, float cy, float size, int color) {
        float u = size / 24f, cell = 5.4f * u, gap = 1.9f * u, start = -(3 * cell + 2 * gap) / 2;
        fill.setColor(color);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                float l = cx + start + x * (cell + gap), t = cy + start + y * (cell + gap);
                r.set(l, t, l + cell, t + cell);
                c.drawRoundRect(r, 1.3f * u, 1.3f * u, fill);
            }
        }
    }

    /** TV settings: three sliders with round knobs. */
    static void sliders(Canvas c, float cx, float cy, float size, int color) {
        float u = size / 24f, l = cx - 10 * u, rt = cx + 10 * u, kr = 2.3f * u;
        float[] ys = {-6.5f, 0, 6.5f}, ks = {3.5f, -3.5f, 1.5f};
        stroke.setColor(color);
        stroke.setStrokeWidth(1.9f * u);
        for (int i = 0; i < 3; i++) {
            float y = cy + ys[i] * u, k = cx + ks[i] * u;
            c.drawLine(l, y, k - kr, y, stroke);
            c.drawLine(k + kr, y, rt, y, stroke);
            c.drawCircle(k, y, kr, stroke);
        }
    }

    /** A TV: the screen outlined and its stand. */
    static void tv(Canvas c, float cx, float cy, float size, int color) {
        float u = size / 24f;
        stroke.setColor(color);
        stroke.setStrokeWidth(2f * u);
        r.set(cx - 10.5f * u, cy - 8 * u, cx + 10.5f * u, cy + 5.5f * u);
        c.drawRoundRect(r, 2.2f * u, 2.2f * u, stroke);
        c.drawLine(cx - 4 * u, cy + 9.5f * u, cx + 4 * u, cy + 9.5f * u, stroke);
        c.drawLine(cx, cy + 5.5f * u, cx, cy + 9.5f * u, stroke);
    }

    /** An HDMI port: the wide plug outline with its row of pins. */
    static void hdmi(Canvas c, float cx, float cy, float size, int color) {
        float u = size / 24f;
        path.rewind();
        path.moveTo(cx - 11 * u, cy - 4.5f * u);
        path.lineTo(cx + 11 * u, cy - 4.5f * u);
        path.lineTo(cx + 11 * u, cy + 0.5f * u);
        path.lineTo(cx + 8 * u, cy + 4.5f * u);
        path.lineTo(cx - 8 * u, cy + 4.5f * u);
        path.lineTo(cx - 11 * u, cy + 0.5f * u);
        path.close();
        stroke.setColor(color);
        stroke.setStrokeWidth(1.9f * u);
        c.drawPath(path, stroke);
        fill.setColor(color);
        for (int i = 0; i < 6; i++) {
            float x = cx - 6.25f * u + i * 2.5f * u;
            r.set(x - 0.8f * u, cy - 1.4f * u, x + 0.8f * u, cy + 0.4f * u);
            c.drawRect(r, fill);
        }
    }
}
