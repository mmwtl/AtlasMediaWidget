package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

final class Ui {
    static final int BACKGROUND = Color.rgb(23, 23, 23);
    static final int CARD = Color.rgb(38, 38, 38);
    static final int NESTED = Color.rgb(51, 51, 51);
    static final int PRIMARY = Color.rgb(245, 245, 245);
    static final int SECONDARY = Color.rgb(212, 212, 212);
    static final int ACCENT = Color.rgb(120, 147, 160);
    static final int ERROR = Color.rgb(217, 130, 130);

    private Ui() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }
}
