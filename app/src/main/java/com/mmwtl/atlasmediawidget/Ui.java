package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextColor(PRIMARY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(context, 16), dp(context, 10),
                dp(context, 16), dp(context, 10));
        button.setBackground(background(NESTED, 8, context));
        return button;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 18),
                dp(context, 20), dp(context, 18));
        card.setBackground(background(CARD, 8, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 12);
        card.setLayoutParams(params);
        return card;
    }

    static void applySystemBarInsets(View view) {
        if (Build.VERSION.SDK_INT < 35) return;
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            target.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + bars.bottom);
            return insets;
        });
        view.requestApplyInsets();
    }

    static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }
}
