package com.mmwtl.atlasmediawidget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** RGB picker with a live swatch; the result carries no alpha. */
final class ColorPickerDialog {
    interface Listener {
        void onColorSelected(int rgb);
    }

    private static final String[] CHANNELS = {"Красный", "Зелёный", "Синий"};

    private ColorPickerDialog() {}

    static void show(Context context, String title, int initialRgb, Listener listener) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(context, 24), Ui.dp(context, 10),
                Ui.dp(context, 24), Ui.dp(context, 4));

        View preview = new View(context);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 70));
        previewParams.bottomMargin = Ui.dp(context, 14);
        content.addView(preview, previewParams);

        int[] channels = {Color.red(initialRgb), Color.green(initialRgb), Color.blue(initialRgb)};
        TextView[] labels = new TextView[3];
        Runnable update = () -> {
            preview.setBackground(Ui.background(
                    Color.rgb(channels[0], channels[1], channels[2]), 8, context));
            for (int index = 0; index < 3; index++) {
                labels[index].setText(CHANNELS[index] + ": " + channels[index]);
            }
        };

        for (int index = 0; index < 3; index++) {
            int channel = index;
            labels[index] = Ui.text(context, "", 14, Ui.SECONDARY);
            content.addView(labels[index]);
            SeekBar slider = new SeekBar(context);
            slider.setMax(255);
            slider.setProgress(channels[index]);
            slider.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
            slider.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress,
                        boolean fromUser) {
                    channels[channel] = progress;
                    update.run();
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            content.addView(slider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        update.run();

        CompactDialog.show(new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", (dialog, which) -> listener.onColorSelected(
                        Color.rgb(channels[0], channels[1], channels[2]) & 0x00FFFFFF)));
    }
}
