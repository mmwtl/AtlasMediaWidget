package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class MusicPlaceholderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    MusicPlaceholderView(Context context) {
        super(context);
        setContentDescription("Звуковая дорожка — обложка отсутствует");
        paint.setColor(Ui.ACCENT);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float stroke = Math.max(Ui.dp(getContext(), 3), width * 0.045f);
        paint.setStrokeWidth(stroke);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(52);

        float cy = height * 0.5f;
        float left = width * 0.08f;
        float step = width * 0.105f;
        float[] amplitudes = {0.12f, 0.25f, 0.39f, 0.2f, 0.46f, 0.31f, 0.16f, 0.27f, 0.1f};
        for (int i = 0; i < amplitudes.length; i++) {
            float x = left + step * i;
            float half = height * amplitudes[i];
            canvas.drawLine(x, cy - half, x, cy + half, paint);
        }

        paint.setAlpha(30);
        paint.setStrokeWidth(Math.max(Ui.dp(getContext(), 2), width * 0.025f));
        path.reset();
        path.moveTo(left, cy);
        for (int i = 1; i < amplitudes.length; i++) {
            float x = left + step * i;
            float y = cy + (i % 2 == 0 ? -1f : 1f) * height * amplitudes[i] * 0.55f;
            path.lineTo(x, y);
        }
        canvas.drawPath(path, paint);
    }
}
