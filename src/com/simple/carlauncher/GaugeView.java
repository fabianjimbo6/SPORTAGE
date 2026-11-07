package com.simple.carlauncher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Speedometer: 270 degree arc 0-240 km/h with a glowing red progress arc and a big number. */
public class GaugeView extends View {

    private static final float START = 135f, SWEEP = 270f;
    private static final int MAX = 240;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint prog = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final float d;
    private float shown, target;

    public GaugeView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(7 * d);
        track.setColor(0xFF2A2A31);
        zone.setStyle(Paint.Style.STROKE);
        zone.setStrokeWidth(7 * d);
        zone.setColor(0x66D6283A);
        prog.setStyle(Paint.Style.STROKE);
        prog.setStrokeWidth(7 * d);
        prog.setStrokeCap(Paint.Cap.ROUND);
        prog.setColor(0xFFFF3B4E);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(11 * d);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setColor(0xFFFF2A3F);
        glow.setMaskFilter(new BlurMaskFilter(9 * d, BlurMaskFilter.Blur.NORMAL));
        tick.setStrokeWidth(1.6f * d);
        tick.setColor(0xFFFFFFFF);
        label.setColor(0xFFE8E8EE);
        label.setTextSize(11 * d);
        label.setTextAlign(Paint.Align.CENTER);
        big.setColor(0xFFFFFFFF);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        unit.setColor(0xFFD6283A);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTextSize(13 * d);
        unit.setLetterSpacing(0.2f);
        unit.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
    }

    public void setSpeed(float kmh) {
        target = Math.max(0, Math.min(MAX, kmh));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        float cx = w / 2f, cy = h / 2f;
        float r = size / 2f - 14 * d;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        c.drawArc(oval, START, SWEEP, false, track);
        c.drawArc(oval, START + SWEEP * 170f / MAX, SWEEP * (MAX - 170f) / MAX, false, zone);

        shown += (target - shown) * 0.18f;
        if (Math.abs(target - shown) > 0.3f) postInvalidateOnAnimation();
        else shown = target;

        if (shown > 0.5f) {
            float sw = SWEEP * shown / MAX;
            c.drawArc(oval, START, sw, false, glow);
            c.drawArc(oval, START, sw, false, prog);
        }

        for (int v = 0; v <= MAX; v += 20) {
            double a = Math.toRadians(START + SWEEP * v / MAX);
            boolean major = v % 40 == 0;
            float len = (major ? 10 : 6) * d;
            float r1 = r - 12 * d, r2 = r1 - len;
            tick.setAlpha(major ? 230 : 110);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tick);
            if (major && v > 0) {
                float rl = r2 - 12 * d;
                c.drawText(String.valueOf(v), cx + (float) Math.cos(a) * rl,
                        cy + (float) Math.sin(a) * rl + label.getTextSize() * 0.35f, label);
            }
        }

        big.setTextSize(size * 0.30f);
        c.drawText(String.valueOf(Math.round(shown)), cx, cy + size * 0.08f, big);
        c.drawText("KM/H", cx, cy + size * 0.08f + 20 * d, unit);
    }
}
