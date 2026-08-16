package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class SourceGlyphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private MediaSource.Id source = MediaSource.Id.UNKNOWN;

    SourceGlyphView(Context context) {
        super(context);
        paint.setColor(Ui.PRIMARY);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setSource(MediaSource.Id value) {
        source = value == null ? MediaSource.Id.UNKNOWN : value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (source.displayId()) {
            case BT -> drawBluetooth(canvas);
            case RADIO -> drawRadio(canvas);
            case USB -> drawUsb(canvas);
            case ONLINE -> drawOnline(canvas);
            case CPAA -> drawPhoneProjection(canvas);
            default -> drawNote(canvas);
        }
    }

    private void drawBluetooth(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float cx = width / 2f;
        float top = height * 0.16f;
        float bottom = height * 0.84f;
        float right = width * 0.78f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, width * 0.1f));
        path.reset();
        path.moveTo(cx, top);
        path.lineTo(right, height * 0.36f);
        path.lineTo(cx, height * 0.5f);
        path.lineTo(right, height * 0.68f);
        path.lineTo(cx, bottom);
        path.close();
        canvas.drawPath(path, paint);
        canvas.drawLine(width * 0.23f, height * 0.31f, right, height * 0.68f, paint);
        canvas.drawLine(width * 0.23f, height * 0.69f, right, height * 0.36f, paint);
    }

    private void drawNote(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, width * 0.1f));
        canvas.drawLine(width * 0.47f, height * 0.22f,
                width * 0.47f, height * 0.66f, paint);
        canvas.drawLine(width * 0.47f, height * 0.22f,
                width * 0.76f, height * 0.15f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(width * 0.18f, height * 0.59f,
                width * 0.5f, height * 0.85f, paint);
    }

    private void drawRadio(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.085f));
        canvas.drawRoundRect(w * 0.13f, h * 0.34f, w * 0.87f, h * 0.84f,
                w * 0.1f, w * 0.1f, paint);
        canvas.drawLine(w * 0.28f, h * 0.34f, w * 0.72f, h * 0.09f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(w * 0.35f, h * 0.59f, w * 0.12f, paint);
        canvas.drawRoundRect(w * 0.55f, h * 0.52f, w * 0.76f, h * 0.58f,
                w * 0.03f, w * 0.03f, paint);
        canvas.drawRoundRect(w * 0.55f, h * 0.65f, w * 0.76f, h * 0.71f,
                w * 0.03f, w * 0.03f, paint);
    }

    private void drawUsb(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.09f));
        canvas.drawLine(cx, h * 0.78f, cx, h * 0.2f, paint);
        canvas.drawLine(cx, h * 0.45f, w * 0.27f, h * 0.61f, paint);
        canvas.drawLine(cx, h * 0.38f, w * 0.72f, h * 0.28f, paint);
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(cx, h * 0.08f);
        path.lineTo(w * 0.39f, h * 0.24f);
        path.lineTo(w * 0.61f, h * 0.24f);
        path.close();
        canvas.drawPath(path, paint);
        canvas.drawCircle(w * 0.27f, h * 0.65f, w * 0.08f, paint);
        canvas.drawRect(w * 0.66f, h * 0.2f, w * 0.8f, h * 0.34f, paint);
        canvas.drawCircle(cx, h * 0.82f, w * 0.1f, paint);
    }

    private void drawOnline(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.54f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.075f));
        canvas.drawCircle(cx, cy, w * 0.34f, paint);
        canvas.drawOval(w * 0.34f, h * 0.2f, w * 0.66f, h * 0.88f, paint);
        canvas.drawLine(w * 0.18f, cy, w * 0.82f, cy, paint);
        canvas.drawArc(w * 0.2f, h * 0.32f, w * 0.8f, h * 0.76f, 200f, 140f, false, paint);
        canvas.drawArc(w * 0.2f, h * 0.32f, w * 0.8f, h * 0.76f, 20f, 140f, false, paint);
    }

    private void drawPhoneProjection(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.075f));
        canvas.drawRoundRect(w * 0.18f, h * 0.09f, w * 0.64f, h * 0.91f,
                w * 0.09f, w * 0.09f, paint);
        canvas.drawLine(w * 0.35f, h * 0.18f, w * 0.47f, h * 0.18f, paint);
        canvas.drawCircle(w * 0.41f, h * 0.8f, w * 0.025f, paint);
        canvas.drawArc(w * 0.48f, h * 0.27f, w * 0.83f, h * 0.67f,
                -70f, 140f, false, paint);
        canvas.drawArc(w * 0.55f, h * 0.35f, w * 0.75f, h * 0.59f,
                -70f, 140f, false, paint);
    }
}
