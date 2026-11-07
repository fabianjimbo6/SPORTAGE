package com.simple.carlauncher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Decorative map preview for the navigation card: dark grid, glowing blue route and an arrow. */
public class NavPreview extends View {

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint route = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final float d;

    public NavPreview(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        grid.setColor(0x2A4A90FF);
        grid.setStrokeWidth(1f * d);
        route.setStyle(Paint.Style.STROKE);
        route.setStrokeWidth(4 * d);
        route.setStrokeJoin(Paint.Join.ROUND);
        route.setStrokeCap(Paint.Cap.ROUND);
        route.setColor(0xFF4A90FF);
        glow.set(route);
        glow.setStrokeWidth(8 * d);
        glow.setMaskFilter(new BlurMaskFilter(6 * d, BlurMaskFilter.Blur.NORMAL));
        arrow.setStyle(Paint.Style.FILL);
        arrow.setColor(0xFF4A90FF);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float step = 30 * d;
        for (float x = step / 2; x < w; x += step) c.drawLine(x, 0, x, h, grid);
        for (float y = step / 2; y < h; y += step) c.drawLine(0, y, w, y, grid);

        p.reset();
        p.moveTo(w * 0.34f, h * 0.95f);
        p.lineTo(w * 0.34f, h * 0.55f);
        p.lineTo(w * 0.66f, h * 0.55f);
        p.lineTo(w * 0.66f, h * 0.22f);
        c.drawPath(p, glow);
        c.drawPath(p, route);

        float ax = w * 0.34f, ay = h * 0.80f, s = 9 * d;
        p.reset();
        p.moveTo(ax, ay - s * 1.5f);
        p.lineTo(ax + s, ay + s);
        p.lineTo(ax, ay + s * 0.4f);
        p.lineTo(ax - s, ay + s);
        p.close();
        c.drawPath(p, arrow);
    }
}
