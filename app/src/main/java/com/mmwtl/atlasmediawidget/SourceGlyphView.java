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
        if (source == MediaSource.Id.BT) drawBluetooth(canvas);
        else drawNote(canvas);
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
}
