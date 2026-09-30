package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Read-only tuning dial: band ticks with a marker at the current frequency. */
final class RadioScaleView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float trackHeight;
    private final float unit;
    private RadioBandScale scale;

    RadioScaleView(Context context, float uiScale, int thicknessDp) {
        super(context);
        unit = Ui.dp(context, 1) * uiScale;
        trackHeight = Math.max(1f, thicknessDp * unit);
        track.setColor(0x553C4148);
        tick.setStrokeWidth(Math.max(1f, unit));
        tick.setStrokeCap(Paint.Cap.ROUND);
        marker.setColor(0xFF83AFC2);
        setPadding(Math.round(6 * unit), 0, Math.round(6 * unit), 0);
    }

    void setScale(RadioBandScale value) {
        scale = value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (scale == null) return;
        float left = getPaddingLeft();
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width <= 0f) return;
        float center = getHeight() / 2f;
        canvas.drawRoundRect(left, center - trackHeight / 2f, left + width,
                center + trackHeight / 2f, trackHeight / 2f, trackHeight / 2f, track);
        for (int value = scale.firstTick(); value <= scale.max; value += scale.minorStep) {
            boolean major = scale.isMajor(value);
            float half = (major ? 6f : 3.5f) * unit;
            float x = left + width * scale.fractionOf(value);
            tick.setColor(major ? 0x99D4D4D4 : 0x4DD4D4D4);
            canvas.drawLine(x, center - half, x, center + half, tick);
        }
        float x = left + width * scale.fraction();
        float halfWidth = 2f * unit;
        float halfHeight = 10f * unit;
        canvas.drawRoundRect(x - halfWidth, center - halfHeight, x + halfWidth,
                center + halfHeight, halfWidth, halfWidth, marker);
    }
}
