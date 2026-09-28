package com.mmwtl.atlasmediawidget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.Map;

/**
 * ACTION_APPWIDGET_CONFIGURE as a short dialog over HOME: the look that matters when placing
 * the card, then confirm. MainActivity stays the full settings editor.
 */
public final class WidgetSetupActivity extends ScaledActivity {
    private Prefs prefs;
    private int widgetId;
    private boolean confirmed;
    private boolean reconfigure;
    private boolean changed;
    private int originalStyle;
    private final Map<CardStyle, CoverDimPreset> originalDim = new EnumMap<>(CardStyle.class);
    private FrameLayout previewHost;
    private final Map<CardStyle, TextView> styleSegments = new EnumMap<>(CardStyle.class);
    private final Map<CoverDimPreset, TextView> dimSegments = new EnumMap<>(CoverDimPreset.class);
    private Switch widgetModeSwitch;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, resultIntent());
        if (!AtlasMediaWidgetProvider.owns(this, widgetId)) {
            finish();
            return;
        }
        prefs = new Prefs(this);
        reconfigure = prefs.isWidgetConfigured(widgetId);
        originalStyle = prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue);
        for (CardStyle style : CardStyle.values()) {
            originalDim.put(style, prefs.coverDimPreset(style));
        }
        setContentView(buildContent());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        getWindow().setLayout(Math.min(screenWidth - Ui.dp(this, 32), Ui.dp(this, 560)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        refreshChoices();
    }

    @Override protected void onDestroy() {
        if (prefs != null && isFinishing() && !confirmed && changed) {
            prefs.putInt(Prefs.KEY_CARD_STYLE, originalStyle);
            for (Map.Entry<CardStyle, CoverDimPreset> entry : originalDim.entrySet()) {
                prefs.putCoverDimPreset(entry.getKey(), entry.getValue());
            }
            AtlasMediaWidgetProvider.refresh(this);
        }
        super.onDestroy();
    }

    private ScrollView buildContent() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.background(Ui.CARD, 16, this));
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 22));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(reconfigure ? "Настройка медиавиджета"
                : "Медиавиджет на главный экран", 20, Ui.PRIMARY, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = text("✕", 22, Ui.SECONDARY, Typeface.NORMAL);
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(Ui.dp(this, 48));
        close.setMinHeight(Ui.dp(this, 48));
        close.setContentDescription("Отмена");
        close.setOnClickListener(v -> finish());
        header.addView(close);
        card.addView(header, fullWrap(0));

        previewHost = new FrameLayout(this);
        previewHost.setBackground(Ui.background(Ui.NESTED, 8, this));
        previewHost.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        previewHost.setContentDescription("Предпросмотр медиавиджета, демо");
        card.addView(previewHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 160)));
        TextView demo = text("Предпросмотр · демо-трек", 12, Ui.SECONDARY, Typeface.NORMAL);
        demo.setGravity(Gravity.CENTER);
        card.addView(demo, fullWrap(6));

        card.addView(text("Компоновка", 15, Ui.SECONDARY, Typeface.BOLD), fullWrap(16));
        LinearLayout styles = segmentRow();
        for (CardStyle style : new CardStyle[]{CardStyle.COMPACT, CardStyle.SQUARE}) {
            TextView segment = Ui.segment(this,
                    style == CardStyle.COMPACT ? "Компактная" : "Просторная");
            segment.setOnClickListener(v -> {
                prefs.putInt(Prefs.KEY_CARD_STYLE, style.preferenceValue);
                changed = true;
                refreshChoices();
            });
            styleSegments.put(style, segment);
            addSegment(styles, segment);
        }
        card.addView(styles, fullWrap(8));

        card.addView(text("Затемнение обложки", 15, Ui.SECONDARY, Typeface.BOLD), fullWrap(16));
        LinearLayout dims = segmentRow();
        for (CoverDimPreset preset : CoverDimPreset.values()) {
            TextView segment = Ui.segment(this, preset.label);
            segment.setTextSize(12);
            segment.setPadding(Ui.dp(this, 2), segment.getPaddingTop(),
                    Ui.dp(this, 2), segment.getPaddingBottom());
            segment.setOnClickListener(v -> {
                prefs.putCoverDimPreset(currentStyle(), preset);
                changed = true;
                refreshChoices();
            });
            dimSegments.put(preset, segment);
            addSegment(dims, segment);
        }
        card.addView(dims, fullWrap(8));

        if (!prefs.isWidgetMode()) {
            widgetModeSwitch = new Switch(this);
            widgetModeSwitch.setText("Переключить приложение в режим «Виджет»");
            widgetModeSwitch.setTextColor(Ui.PRIMARY);
            widgetModeSwitch.setTextSize(15);
            widgetModeSwitch.setChecked(true);
            card.addView(widgetModeSwitch, fullWrap(18));
            card.addView(text("Сейчас выбран оверлей. Без переключения виджет будет неактивен.",
                    13, Ui.SECONDARY, Typeface.NORMAL), fullWrap(4));
        }

        card.addView(text("Оформление общее для всех виджетов. Текст, отступы и панель "
                        + "кнопок настраиваются в приложении.",
                13, Ui.SECONDARY, Typeface.NORMAL), fullWrap(16));

        Button add = Ui.button(this, reconfigure ? "Готово" : "Добавить");
        add.setTextSize(17);
        add.setTypeface(Typeface.DEFAULT_BOLD);
        add.setTextColor(Ui.ON_ACCENT);
        add.setBackground(Ui.background(Ui.ACCENT, 10, this));
        add.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        add.setOnClickListener(v -> confirm(false));
        card.addView(add, fullWrap(20));
        Button addAndOpen = Ui.button(this, reconfigure ? "Открыть все настройки"
                : "Добавить и открыть все настройки");
        addAndOpen.setOnClickListener(v -> confirm(true));
        card.addView(addAndOpen, fullWrap(10));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(card);
        return scroll;
    }

    private void confirm(boolean openSettings) {
        if (!AtlasMediaWidgetProvider.owns(this, widgetId)) {
            finish();
            return;
        }
        confirmed = true;
        prefs.setWidgetsConfigured(new int[]{widgetId}, true);
        if (widgetModeSwitch != null && widgetModeSwitch.isChecked()) prefs.setWidgetMode(true);
        AtlasMediaWidgetProvider.refresh(this);
        setResult(RESULT_OK, resultIntent());
        if (openSettings) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private void refreshChoices() {
        CardStyle style = currentStyle();
        for (Map.Entry<CardStyle, TextView> entry : styleSegments.entrySet()) {
            Ui.setSegmentSelected(this, entry.getValue(), entry.getKey() == style);
        }
        CoverDimPreset dim = prefs.coverDimPreset(style);
        for (Map.Entry<CoverDimPreset, TextView> entry : dimSegments.entrySet()) {
            Ui.setSegmentSelected(this, entry.getValue(), entry.getKey() == dim);
        }
        renderPreview();
    }

    private void renderPreview() {
        if (previewHost.getWidth() <= 0) {
            previewHost.post(this::renderPreview);
            return;
        }
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * .28f);
        WidgetPreview.renderWidget(getApplicationContext(), previewHost, prefs,
                AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId), maxHeight);
    }

    private CardStyle currentStyle() {
        return CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE,
                CardStyle.DEFAULT.preferenceValue));
    }

    private Intent resultIntent() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }

    private LinearLayout segmentRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void addSegment(LinearLayout row, TextView segment) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) params.leftMargin = Ui.dp(this, 6);
        row.addView(segment, params);
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = Ui.text(this, value, size, color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, topMarginDp);
        return params;
    }
}
