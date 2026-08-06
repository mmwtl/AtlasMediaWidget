package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class TransportButton extends View {
    enum Type { PREVIOUS, PLAY_PAUSE, NEXT }

    private final Type type;
    private final boolean prominent;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean playing;

    TransportButton(Context context, Type type, boolean prominent) {
        super(context);
        this.type = type;
        this.prominent = prominent;
        setClickable(true);
        setFocusable(true);
        setContentDescription(switch (type) {
            case PREVIOUS -> "Предыдущий";
            case NEXT -> "Следующий";
            case PLAY_PAUSE -> "Воспроизведение или пауза";
        });
    }

    void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        invalidate();
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float alpha = isEnabled() ? 1f : 0.34f;
        int background = prominent ? Ui.ACCENT : 0xD9333333;
        if (isPressed() && isEnabled()) background = prominent ? 0xFF91A8B3 : 0xFF465158;
        paint.setColor(background);
        paint.setAlpha(Math.round(255 * alpha));
        canvas.drawRoundRect(0, 0, width, height,
                prominent ? width / 2f : Ui.dp(getContext(), 18),
                prominent ? height / 2f : Ui.dp(getContext(), 18), paint);

        paint.setColor(prominent ? Ui.BACKGROUND : Ui.PRIMARY);
        paint.setAlpha(Math.round(255 * alpha));
        float cx = width / 2f;
        float cy = height / 2f;
        float unit = Math.min(width, height) * 0.22f;
        if (type == Type.PLAY_PAUSE) {
            if (playing) drawPause(canvas, cx, cy, unit);
            else drawTriangle(canvas, cx - unit * 0.15f, cy, unit, true);
        } else if (type == Type.PREVIOUS) {
            drawBar(canvas, cx - unit * 1.15f, cy, unit);
            drawTriangle(canvas, cx - unit * 0.28f, cy, unit * 0.82f, false);
            drawTriangle(canvas, cx + unit * 0.62f, cy, unit * 0.82f, false);
        } else {
            drawTriangle(canvas, cx - unit * 0.62f, cy, unit * 0.82f, true);
            drawTriangle(canvas, cx + unit * 0.28f, cy, unit * 0.82f, true);
            drawBar(canvas, cx + unit * 1.15f, cy, unit);
        }
    }

    private void drawTriangle(Canvas canvas, float cx, float cy, float unit, boolean right) {
        path.reset();
        float direction = right ? 1f : -1f;
        path.moveTo(cx + direction * unit, cy);
        path.lineTo(cx - direction * unit * 0.72f, cy - unit);
        path.lineTo(cx - direction * unit * 0.72f, cy + unit);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawPause(Canvas canvas, float cx, float cy, float unit) {
        float halfHeight = unit;
        float halfWidth = unit * 0.28f;
        float gap = unit * 0.3f;
        canvas.drawRoundRect(cx - gap - halfWidth, cy - halfHeight,
                cx - gap + halfWidth, cy + halfHeight, halfWidth, halfWidth, paint);
        canvas.drawRoundRect(cx + gap - halfWidth, cy - halfHeight,
                cx + gap + halfWidth, cy + halfHeight, halfWidth, halfWidth, paint);
    }

    private void drawBar(Canvas canvas, float cx, float cy, float unit) {
        float halfWidth = Math.max(2f, unit * 0.16f);
        canvas.drawRoundRect(cx - halfWidth, cy - unit, cx + halfWidth, cy + unit,
                halfWidth, halfWidth, paint);
    }
}
