package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class AudioWaveformView extends View {
    private static final float[] LEVELS = {0.24f, 0.52f, 0.78f, 0.43f, 1f,
            0.68f, 0.36f, 0.84f, 0.58f, 0.3f, 0.5f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    AudioWaveformView(Context context) {
        super(context);
        setContentDescription("Обложка отсутствует");
        paint.setColor(Ui.ACCENT);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2f;
        float gap = width / (LEVELS.length + 3f);
        float stroke = Math.max(Ui.dp(getContext(), 4), gap * 0.28f);
        float startX = (width - gap * (LEVELS.length - 1)) / 2f;
        paint.setStrokeWidth(stroke);
        paint.setAlpha(62);
        canvas.drawLine(startX - gap, centerY, startX + gap * LEVELS.length, centerY, paint);
        paint.setAlpha(88);
        for (int index = 0; index < LEVELS.length; index++) {
            float half = height * LEVELS[index] * 0.34f;
            float x = startX + gap * index;
            canvas.drawLine(x, centerY - half, x, centerY + half, paint);
        }
    }
}
