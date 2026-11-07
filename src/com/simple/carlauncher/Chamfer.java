package com.simple.carlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Dark panel with two cut (chamfered) corners and a thin coloured outline: the Kia HUD look. */
final class Chamfer extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float cut;

    Chamfer(float cutPx, int fillColor, int strokeColor, float strokePx) {
        cut = cutPx;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(strokeColor);
        stroke.setStrokeWidth(strokePx);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        float s = stroke.getStrokeWidth() / 2f;
        float l = b.left + s, t = b.top + s, r = b.right - s, bt = b.bottom - s;
        path.reset();
        path.moveTo(l, t);
        path.lineTo(r - cut, t);
        path.lineTo(r, t + cut);
        path.lineTo(r, bt);
        path.lineTo(l + cut, bt);
        path.lineTo(l, bt - cut);
        path.close();
    }

    @Override
    public void draw(Canvas c) {
        c.drawPath(path, fill);
        c.drawPath(path, stroke);
    }

    @Override
    public void setAlpha(int a) {
        fill.setAlpha(a);
        stroke.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        stroke.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
