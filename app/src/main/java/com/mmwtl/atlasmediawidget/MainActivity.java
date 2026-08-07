package com.mmwtl.atlasmediawidget;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int MIN_WIDTH_DP = 360;
    private static final int MAX_WIDTH_DP = 900;
    private static final int MIN_HEIGHT_DP = 220;
    private static final int MAX_HEIGHT_DP = 900;
    private Prefs prefs;
    private TextView permissionStatus;
    private TextView bridgeStatus;
    private Button serviceButton;
    private Switch autoStart;
    private RadioButton compactStyle;
    private RadioButton squareStyle;
    private TextView sizeValue;
    private SeekBar widthSize;
    private SeekBar heightSize;
    private TextView textGapValue;
    private SeekBar textGap;
    private TextView controlPanelHeightValue;
    private SeekBar controlPanelHeight;
    private TextView controlIconScaleValue;
    private SeekBar controlIconScale;
    private TextView controlSpreadValue;
    private SeekBar controlSpread;
    private boolean refreshingStyle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(buildContent());
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 28), Ui.dp(this, 38), Ui.dp(this, 28), Ui.dp(this, 38));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Atlas Media Widget", 30, Ui.PRIMARY, Typeface.BOLD);
        root.addView(title);
        TextView intro = text(
                "Медиакарточка поверх главного экрана. Состояние и команды приходят из "
                        + "GInputBridge mediaapi v1.",
                17, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams introParams = fullWrap();
        introParams.topMargin = Ui.dp(this, 10);
        root.addView(intro, introParams);

        LinearLayout accessCard = card();
        LinearLayout.LayoutParams cardParams = fullWrap();
        cardParams.topMargin = Ui.dp(this, 28);
        root.addView(accessCard, cardParams);
        accessCard.addView(text("Разрешения", 20, Ui.PRIMARY, Typeface.BOLD));
        permissionStatus = text("", 15, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(this, 10);
        accessCard.addView(permissionStatus, statusParams);
        Button overlay = actionButton("Разрешить поверх окон");
        overlay.setOnClickListener(v -> openOverlaySettings());
        accessCard.addView(overlay, buttonParams());
        Button usage = actionButton("Разрешить историю использования");
        usage.setOnClickListener(v -> openUsageSettings());
        accessCard.addView(usage, buttonParams());

        LinearLayout bridgeCard = card();
        LinearLayout.LayoutParams bridgeParams = fullWrap();
        bridgeParams.topMargin = Ui.dp(this, 18);
        root.addView(bridgeCard, bridgeParams);
        bridgeCard.addView(text("GInputBridge", 20, Ui.PRIMARY, Typeface.BOLD));
        bridgeStatus = text("", 15, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams bridgeStatusParams = fullWrap();
        bridgeStatusParams.topMargin = Ui.dp(this, 10);
        bridgeCard.addView(bridgeStatus, bridgeStatusParams);
        Button openBridge = actionButton("Открыть GInputBridge");
        openBridge.setOnClickListener(v -> openGInputBridge());
        bridgeCard.addView(openBridge, buttonParams());

        LinearLayout serviceCard = card();
        LinearLayout.LayoutParams serviceParams = fullWrap();
        serviceParams.topMargin = Ui.dp(this, 18);
        root.addView(serviceCard, serviceParams);
        serviceCard.addView(text("Виджет", 20, Ui.PRIMARY, Typeface.BOLD));

        TextView styleTitle = text("Формат карточки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams styleTitleParams = fullWrap();
        styleTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(styleTitle, styleTitleParams);
        RadioGroup styleGroup = new RadioGroup(this);
        styleGroup.setOrientation(RadioGroup.HORIZONTAL);
        compactStyle = styleButton(CardStyle.COMPACT.label);
        squareStyle = styleButton(CardStyle.SQUARE.label);
        styleGroup.addView(compactStyle, new RadioGroup.LayoutParams(0,
                RadioGroup.LayoutParams.WRAP_CONTENT, 1f));
        styleGroup.addView(squareStyle, new RadioGroup.LayoutParams(0,
                RadioGroup.LayoutParams.WRAP_CONTENT, 1f));
        styleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (refreshingStyle) return;
            CardStyle selected = checkedId == compactStyle.getId()
                    ? CardStyle.COMPACT : CardStyle.SQUARE;
            prefs.putInt(Prefs.KEY_CARD_STYLE, selected.preferenceValue);
            refreshSizeControls(selected);
            if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                OverlayService.refreshStyle(this);
            }
        });
        serviceCard.addView(styleGroup, fullWrap());

        TextView sizeTitle = text("Размер карточки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams sizeTitleParams = fullWrap();
        sizeTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(sizeTitle, sizeTitleParams);
        sizeValue = text("", 18, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams sizeValueParams = fullWrap();
        sizeValueParams.topMargin = Ui.dp(this, 6);
        serviceCard.addView(sizeValue, sizeValueParams);
        serviceCard.addView(text("Ширина", 14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        widthSize = sizeSeekBar(MIN_WIDTH_DP, MAX_WIDTH_DP);
        serviceCard.addView(widthSize, fullWrap());
        serviceCard.addView(text("Высота", 14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        heightSize = sizeSeekBar(MIN_HEIGHT_DP, MAX_HEIGHT_DP);
        serviceCard.addView(heightSize, fullWrap());
        SeekBar.OnSeekBarChangeListener sizeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) updateSizeLabel();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (refreshingStyle) return;
                CardStyle current = currentStyle();
                prefs.putCardSize(current, widthSize.getProgress(), heightSize.getProgress());
                updateSizeLabel();
                if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                    OverlayService.refreshStyle(MainActivity.this);
                }
            }
        };
        widthSize.setOnSeekBarChangeListener(sizeListener);
        heightSize.setOnSeekBarChangeListener(sizeListener);

        serviceCard.addView(text("Дополнительный отступ текста от верхней строки",
                14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        textGapValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        serviceCard.addView(textGapValue, fullWrap());
        textGap = sizeSeekBar(0, Prefs.MAX_TEXT_GAP_DP);
        textGap.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateTextGapLabel();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (refreshingStyle) return;
                prefs.putTextGap(currentStyle(), textGap.getProgress());
                if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                    OverlayService.refreshStyle(MainActivity.this);
                }
            }
        });
        serviceCard.addView(textGap, fullWrap());

        TextView controlsTitle = text("Панель управления", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams controlsTitleParams = fullWrap();
        controlsTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(controlsTitle, controlsTitleParams);

        serviceCard.addView(text("Высота нижней панели", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlPanelHeightValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        serviceCard.addView(controlPanelHeightValue, fullWrap());
        controlPanelHeight = sizeSeekBar(Prefs.MIN_CONTROL_PANEL_HEIGHT_DP,
                Prefs.MAX_CONTROL_PANEL_HEIGHT_DP);
        serviceCard.addView(controlPanelHeight, fullWrap());

        serviceCard.addView(text("Размер иконок", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlIconScaleValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        serviceCard.addView(controlIconScaleValue, fullWrap());
        controlIconScale = sizeSeekBar(Prefs.MIN_CONTROL_ICON_SCALE_PERCENT,
                Prefs.MAX_CONTROL_ICON_SCALE_PERCENT);
        serviceCard.addView(controlIconScale, fullWrap());

        serviceCard.addView(text("Разбежка боковых иконок от центра", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlSpreadValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        serviceCard.addView(controlSpreadValue, fullWrap());
        controlSpread = sizeSeekBar(Prefs.MIN_CONTROL_SPREAD_PERCENT,
                Prefs.MAX_CONTROL_SPREAD_PERCENT);
        SeekBar.OnSeekBarChangeListener controlListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateControlLabels();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (refreshingStyle) return;
                CardStyle current = currentStyle();
                prefs.putControlLayout(current, controlPanelHeight.getProgress(),
                        controlIconScale.getProgress(), controlSpread.getProgress());
                if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                    OverlayService.refreshStyle(MainActivity.this);
                }
            }
        };
        controlPanelHeight.setOnSeekBarChangeListener(controlListener);
        controlIconScale.setOnSeekBarChangeListener(controlListener);
        controlSpread.setOnSeekBarChangeListener(controlListener);
        serviceCard.addView(controlSpread, fullWrap());

        Button resetControls = actionButton("Вернуть панель по умолчанию");
        resetControls.setOnClickListener(v -> {
            CardStyle current = currentStyle();
            prefs.putControlLayout(current, current.defaultControlPanelHeightDp,
                    Prefs.DEFAULT_CONTROL_ICON_SCALE_PERCENT,
                    Prefs.DEFAULT_CONTROL_SPREAD_PERCENT);
            refreshSizeControls(current);
            if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                OverlayService.refreshStyle(this);
            }
        });
        serviceCard.addView(resetControls, buttonParams());

        Button resetSize = actionButton("Вернуть размер по умолчанию");
        resetSize.setOnClickListener(v -> {
            CardStyle current = currentStyle();
            prefs.putCardSize(current, current.defaultWidthDp, current.defaultHeightDp);
            refreshSizeControls(current);
            if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                OverlayService.refreshStyle(this);
            }
        });
        serviceCard.addView(resetSize, buttonParams());

        serviceButton = actionButton("Запустить");
        serviceButton.setOnClickListener(v -> toggleService());
        serviceCard.addView(serviceButton, buttonParams());
        autoStart = new Switch(this);
        autoStart.setText("Автозапуск после загрузки ГУ");
        autoStart.setTextColor(Ui.PRIMARY);
        autoStart.setTextSize(16);
        autoStart.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        autoStart.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
        });
        LinearLayout.LayoutParams switchParams = fullWrap();
        switchParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(autoStart, switchParams);

        TextView note = text(
                "Карточка отображается только когда HOME находится на переднем плане. "
                        + "Перетаскивание выполняется за кнопку ⋮ в правом верхнем углу. "
                        + "Нажатие на свободную область открывает активный медиаисточник.",
                14, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams noteParams = fullWrap();
        noteParams.topMargin = Ui.dp(this, 24);
        root.addView(note, noteParams);
        return scroll;
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean usage = ForegroundAppDetector.hasUsageAccess(this);
        permissionStatus.setText("Поверх окон: " + yesNo(overlay)
                + "\nИстория использования: " + yesNo(usage));
        permissionStatus.setTextColor(overlay && usage ? Ui.ACCENT : Ui.ERROR);
        boolean bridgeInstalled = isPackageInstalled(MediaBridgeContract.SERVICE_PACKAGE);
        bridgeStatus.setText(bridgeInstalled
                ? "Пакет установлен. Требуется ветка mediaapi с protocol v1."
                : "Пакет com.salat.gbinder не найден.");
        bridgeStatus.setTextColor(bridgeInstalled ? Ui.ACCENT : Ui.ERROR);
        boolean enabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        serviceButton.setText(enabled ? "Остановить" : "Запустить");
        serviceButton.setEnabled(enabled || overlay && usage);
        autoStart.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        CardStyle style = CardStyle.fromPreference(
                prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue));
        refreshingStyle = true;
        compactStyle.setChecked(style == CardStyle.COMPACT);
        squareStyle.setChecked(style == CardStyle.SQUARE);
        refreshSizeControls(style);
        refreshingStyle = false;
        requestNotificationPermissionIfNeeded();
    }

    private void toggleService() {
        boolean enabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        if (enabled) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            OverlayService.stop(this);
        } else if (Settings.canDrawOverlays(this) && ForegroundAppDetector.hasUsageAccess(this)) {
            try {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
                OverlayService.start(this);
            } catch (RuntimeException error) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                AppLog.warn("Cannot start overlay service", error);
                Toast.makeText(this, "Не удалось запустить виджет", Toast.LENGTH_LONG).show();
            }
        }
        refresh();
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void openUsageSettings() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void openGInputBridge() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(MediaBridgeContract.SERVICE_PACKAGE);
        if (launch == null) {
            Toast.makeText(this, "GInputBridge не установлен", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(launch);
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        card.setBackground(Ui.background(Ui.CARD, 20, this));
        return card;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Ui.PRIMARY);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.NESTED));
        return button;
    }

    private RadioButton styleButton(String label) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Ui.PRIMARY);
        button.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        button.setPadding(0, Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        return button;
    }

    private SeekBar sizeSeekBar(int min, int max) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMin(min);
        seekBar.setMax(max);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        return seekBar;
    }

    private CardStyle currentStyle() {
        return CardStyle.fromPreference(
                prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue));
    }

    private void refreshSizeControls(CardStyle style) {
        if (widthSize == null || heightSize == null || textGap == null
                || controlPanelHeight == null || controlIconScale == null
                || controlSpread == null) return;
        boolean previous = refreshingStyle;
        refreshingStyle = true;
        widthSize.setProgress(clamp(prefs.cardWidthDp(style), MIN_WIDTH_DP, MAX_WIDTH_DP));
        heightSize.setProgress(clamp(prefs.cardHeightDp(style), MIN_HEIGHT_DP, MAX_HEIGHT_DP));
        textGap.setProgress(prefs.textGapDp(style));
        controlPanelHeight.setProgress(prefs.controlPanelHeightDp(style));
        controlIconScale.setProgress(prefs.controlIconScalePercent(style));
        controlSpread.setProgress(prefs.controlSpreadPercent(style));
        updateSizeLabel();
        updateTextGapLabel();
        updateControlLabels();
        refreshingStyle = previous;
    }

    private void updateSizeLabel() {
        sizeValue.setText(widthSize.getProgress() + " × " + heightSize.getProgress() + " dp");
    }

    private void updateTextGapLabel() {
        textGapValue.setText("+" + textGap.getProgress() + " dp");
    }

    private void updateControlLabels() {
        controlPanelHeightValue.setText(controlPanelHeight.getProgress() + " dp");
        controlIconScaleValue.setText(controlIconScale.getProgress() + " %");
        controlSpreadValue.setText(controlSpread.getProgress() + " % ширины");
    }

    private LinearLayout.LayoutParams labelParams() {
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, 10);
        return params;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, 12);
        return params;
    }

    private static String yesNo(boolean value) {
        return value ? "разрешено" : "не разрешено";
    }
}
