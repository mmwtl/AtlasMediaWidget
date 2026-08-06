package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class MusicPlaceholderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MusicPlaceholderView(Context context) {
        super(context);
        setContentDescription("Обложка отсутствует");
        paint.setColor(Ui.ACCENT);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float stroke = Math.max(Ui.dp(getContext(), 7), width * 0.09f);
        paint.setStrokeWidth(stroke);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAlpha(42);

        float leftStem = width * 0.38f;
        float rightStem = width * 0.74f;
        float top = height * 0.2f;
        float bottom = height * 0.68f;
        canvas.drawLine(leftStem, top + height * 0.12f, leftStem, bottom, paint);
        canvas.drawLine(rightStem, top, rightStem, bottom - height * 0.08f, paint);
        canvas.drawLine(leftStem, top + height * 0.12f, rightStem, top, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.rotate(-18f, leftStem - width * 0.08f, bottom + height * 0.08f);
        canvas.drawOval(leftStem - width * 0.2f, bottom - height * 0.02f,
                leftStem + width * 0.02f, bottom + height * 0.19f, paint);
        canvas.restore();
        canvas.save();
        canvas.rotate(-18f, rightStem - width * 0.08f, bottom);
        canvas.drawOval(rightStem - width * 0.2f, bottom - height * 0.1f,
                rightStem + width * 0.02f, bottom + height * 0.11f, paint);
        canvas.restore();
    }
}
