package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.widget.ImageView;

final class TransportButton extends ImageView {
    enum Type { PREVIOUS, PLAY_PAUSE, NEXT }

    private final Type type;
    private boolean playing;

    TransportButton(Context context, Type type) {
        super(context);
        this.type = type;
        setScaleType(ScaleType.FIT_CENTER);
        setImageTintMode(PorterDuff.Mode.SRC_IN);
        setImageTintList(new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_pressed},
                        new int[]{}
                },
                new int[]{0x9EF5F5F5, 0xFF9DC2D2, Ui.PRIMARY}));
        if (type == Type.NEXT) setScaleX(-1f);
        setClickable(true);
        setFocusable(true);
        setContentDescription(switch (type) {
            case PREVIOUS -> "Предыдущий";
            case NEXT -> "Следующий";
            case PLAY_PAUSE -> "Воспроизведение или пауза";
        });
        updateIcon();
    }

    void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        updateIcon();
    }

    private void updateIcon() {
        if (type == Type.PLAY_PAUSE) {
            setImageResource(playing
                    ? R.drawable.ic_transport_pause : R.drawable.ic_transport_play);
        } else {
            setImageResource(R.drawable.ic_transport_previous);
        }
    }
}
