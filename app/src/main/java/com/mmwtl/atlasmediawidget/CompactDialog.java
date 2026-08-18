package com.mmwtl.atlasmediawidget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/** Shared sizing and message formatting for dialogs shown by the app. */
final class CompactDialog {
    private static final float MAX_WIDTH_DP = 640f;
    private static final float SIDE_MARGIN_DP = 24f;
    private static final float MESSAGE_SIZE_SP = 12f;
    private static final float MESSAGE_LINE_SPACING_EXTRA_DP = 2f;
    private static final float MESSAGE_LINE_SPACING_MULTIPLIER = 1.08f;

    private CompactDialog() {}

    static AlertDialog show(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        dialog.show();
        apply(dialog);
        return dialog;
    }

    /** Applies the shared window rules to an already shown AlertDialog or custom Dialog. */
    static void apply(Dialog dialog) {
        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;
            apply(dialog, alertDialog.findViewById(android.R.id.message));
            return;
        }
        apply(dialog, null);
    }

    /** Applies the shared window rules and formats a custom dialog's message view if supplied. */
    static void apply(Dialog dialog, TextView messageView) {
        Window window = dialog.getWindow();
        if (window == null) return;

        Context context = dialog.getContext();
        WindowManager windowManager = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        int screenWidthPx = windowManager == null
                ? context.getResources().getDisplayMetrics().widthPixels
                : windowManager.getCurrentWindowMetrics().getBounds().width();
        int widthPx = calculateWidthPx(screenWidthPx,
                context.getResources().getDisplayMetrics().density);
        window.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT);

        if (messageView != null) {
            formatMessage(messageView, context);
        }
    }

    static int calculateWidthPx(int screenWidthPx, float density) {
        int maxWidthPx = Math.round(MAX_WIDTH_DP * density);
        int sideMarginPx = Math.round(SIDE_MARGIN_DP * density);
        int availableWidthPx = screenWidthPx - sideMarginPx * 2;
        return Math.max(1, Math.min(maxWidthPx, availableWidthPx));
    }

    private static void formatMessage(TextView messageView, Context context) {
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MESSAGE_SIZE_SP);
        messageView.setLineSpacing(
                Ui.dp(context, MESSAGE_LINE_SPACING_EXTRA_DP),
                MESSAGE_LINE_SPACING_MULTIPLIER);
        messageView.setHorizontallyScrolling(false);
        messageView.setSingleLine(false);
        messageView.setMaxLines(Integer.MAX_VALUE);
        messageView.setEllipsize(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            messageView.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE);
            messageView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }
    }
}
