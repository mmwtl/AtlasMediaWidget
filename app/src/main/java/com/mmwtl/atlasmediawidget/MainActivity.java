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
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

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
    private TextView controlBottomInsetValue;
    private SeekBar controlBottomInset;
    private FrameLayout previewHost;
    private LabeledSeek topInsetSetting;
    private LabeledSeek contentInsetSetting;
    private LabeledSeek topRowTextSetting;
    private LabeledSeek titleTextSetting;
    private LabeledSeek subtitleTextSetting;
    private LabeledSeek subtitleGapSetting;
    private LabeledSeek timeTextSetting;
    private LabeledSeek progressGapSetting;
    private LabeledSeek progressThicknessSetting;
    private boolean refreshingStyle;

    private final MediaCardView.Listener previewListener = new MediaCardView.Listener() {
        @Override public boolean onDragTouch(View view, MotionEvent event) { return true; }
        @Override public void onCommand(String command) {}
        @Override public void onSeek(long positionMs) {}
        @Override public void onSource(MediaSource.Id source) {}
        @Override public void onOpenSource() {}
    };

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

        TextView previewTitle = text("Предпросмотр", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams previewTitleParams = fullWrap();
        previewTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(previewTitle, previewTitleParams);
        previewHost = new FrameLayout(this);
        previewHost.setBackground(Ui.background(Ui.NESTED, 18, this));
        previewHost.setPadding(Ui.dp(this, 8), Ui.dp(this, 8),
                Ui.dp(this, 8), Ui.dp(this, 8));
        previewHost.setContentDescription("Предпросмотр медиакарточки");
        LinearLayout.LayoutParams previewParams = fullWrap();
        previewParams.topMargin = Ui.dp(this, 8);
        previewParams.height = Ui.dp(this, 1);
        serviceCard.addView(previewHost, previewParams);

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
                if (fromUser) {
                    updateSizeLabel();
                    renderPreview();
                }
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

        serviceCard.addView(text("Сдвиг блока текста по вертикали",
                14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        textGapValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        serviceCard.addView(textGapValue, fullWrap());
        textGap = sizeSeekBar(Prefs.MIN_TEXT_GAP_DP, Prefs.MAX_TEXT_GAP_DP);
        textGap.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateTextGapLabel();
                if (fromUser) renderPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (refreshingStyle) return;
                saveAppearance();
            }
        });
        serviceCard.addView(textGap, fullWrap());

        TextView typographyTitle = text("Текст и отступы", 15,
                Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams typographyTitleParams = fullWrap();
        typographyTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(typographyTitle, typographyTitleParams);

        topInsetSetting = addLabeledSeek(serviceCard, "Отступ верхней строки",
                Prefs.MIN_TOP_INSET_DP, Prefs.MAX_TOP_INSET_DP);
        contentInsetSetting = addLabeledSeek(serviceCard, "Боковой отступ контента",
                Prefs.MIN_CONTENT_INSET_DP, Prefs.MAX_CONTENT_INSET_DP);
        topRowTextSetting = addLabeledSeek(serviceCard, "Размер текста верхней строки",
                Prefs.MIN_TOP_ROW_TEXT_SIZE_SP, Prefs.MAX_TOP_ROW_TEXT_SIZE_SP);
        titleTextSetting = addLabeledSeek(serviceCard, "Размер названия",
                Prefs.MIN_TITLE_TEXT_SIZE_SP, Prefs.MAX_TITLE_TEXT_SIZE_SP);
        subtitleTextSetting = addLabeledSeek(serviceCard, "Размер исполнителя и альбома",
                Prefs.MIN_SUBTITLE_TEXT_SIZE_SP, Prefs.MAX_SUBTITLE_TEXT_SIZE_SP);
        subtitleGapSetting = addLabeledSeek(serviceCard, "Отступ подзаголовка",
                0, Prefs.MAX_SUBTITLE_GAP_DP);
        timeTextSetting = addLabeledSeek(serviceCard, "Размер времени",
                Prefs.MIN_TIME_TEXT_SIZE_SP, Prefs.MAX_TIME_TEXT_SIZE_SP);
        progressGapSetting = addLabeledSeek(serviceCard, "Отступ прогресса от панели",
                0, Prefs.MAX_PROGRESS_GAP_DP);
        progressThicknessSetting = addLabeledSeek(serviceCard, "Толщина линии прогресса",
                Prefs.MIN_PROGRESS_THICKNESS_DP, Prefs.MAX_PROGRESS_THICKNESS_DP);
        SeekBar.OnSeekBarChangeListener appearanceListener =
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        updateAppearanceLabels();
                        if (fromUser) renderPreview();
                    }

                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override public void onStopTrackingTouch(SeekBar seekBar) {
                        if (!refreshingStyle) saveAppearance();
                    }
                };
        bind(appearanceListener, topInsetSetting, contentInsetSetting, topRowTextSetting,
                titleTextSetting, subtitleTextSetting, subtitleGapSetting, timeTextSetting,
                progressGapSetting, progressThicknessSetting);

        Button resetAppearance = actionButton("Вернуть текст и отступы по умолчанию");
        resetAppearance.setOnClickListener(v -> {
            CardStyle current = currentStyle();
            WidgetAppearance defaults = WidgetAppearance.defaults(current);
            WidgetAppearance existing = currentAppearance();
            prefs.putAppearance(current, new WidgetAppearance(
                    defaults.textGapDp,
                    existing.controlPanelHeightDp,
                    existing.controlIconScalePercent,
                    existing.controlSpreadPercent,
                    existing.controlBottomInsetDp,
                    defaults.topInsetDp,
                    defaults.contentInsetDp,
                    defaults.topRowTextSizeSp,
                    defaults.titleTextSizeSp,
                    defaults.subtitleTextSizeSp,
                    defaults.subtitleGapDp,
                    defaults.timeTextSizeSp,
                    defaults.progressGapDp,
                    defaults.progressThicknessDp));
            refreshSizeControls(current);
            refreshOverlayIfRunning();
        });
        serviceCard.addView(resetAppearance, buttonParams());

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
                if (fromUser) renderPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (refreshingStyle) return;
                saveAppearance();
            }
        };
        controlPanelHeight.setOnSeekBarChangeListener(controlListener);
        controlIconScale.setOnSeekBarChangeListener(controlListener);
        controlSpread.setOnSeekBarChangeListener(controlListener);
        serviceCard.addView(controlSpread, fullWrap());

        serviceCard.addView(text("Дополнительный отступ иконок от нижней границы", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlBottomInsetValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        serviceCard.addView(controlBottomInsetValue, fullWrap());
        controlBottomInset = sizeSeekBar(0, Prefs.MAX_CONTROL_BOTTOM_INSET_DP);
        controlBottomInset.setOnSeekBarChangeListener(controlListener);
        serviceCard.addView(controlBottomInset, fullWrap());

        Button resetControls = actionButton("Вернуть панель по умолчанию");
        resetControls.setOnClickListener(v -> {
            CardStyle current = currentStyle();
            prefs.putControlLayout(current, current.defaultControlPanelHeightDp,
                    Prefs.DEFAULT_CONTROL_ICON_SCALE_PERCENT,
                    Prefs.DEFAULT_CONTROL_SPREAD_PERCENT, 0);
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
                || controlSpread == null || controlBottomInset == null
                || topInsetSetting == null) return;
        boolean previous = refreshingStyle;
        refreshingStyle = true;
        WidgetAppearance appearance = prefs.appearance(style);
        widthSize.setProgress(clamp(prefs.cardWidthDp(style), MIN_WIDTH_DP, MAX_WIDTH_DP));
        heightSize.setProgress(clamp(prefs.cardHeightDp(style), MIN_HEIGHT_DP, MAX_HEIGHT_DP));
        textGap.setProgress(appearance.textGapDp);
        controlPanelHeight.setProgress(appearance.controlPanelHeightDp);
        controlIconScale.setProgress(appearance.controlIconScalePercent);
        controlSpread.setProgress(appearance.controlSpreadPercent);
        controlBottomInset.setProgress(appearance.controlBottomInsetDp);
        topInsetSetting.seek.setProgress(appearance.topInsetDp);
        contentInsetSetting.seek.setProgress(appearance.contentInsetDp);
        topRowTextSetting.seek.setProgress(appearance.topRowTextSizeSp);
        titleTextSetting.seek.setProgress(appearance.titleTextSizeSp);
        subtitleTextSetting.seek.setProgress(appearance.subtitleTextSizeSp);
        subtitleGapSetting.seek.setProgress(appearance.subtitleGapDp);
        timeTextSetting.seek.setProgress(appearance.timeTextSizeSp);
        progressGapSetting.seek.setProgress(appearance.progressGapDp);
        progressThicknessSetting.seek.setProgress(appearance.progressThicknessDp);
        updateSizeLabel();
        updateTextGapLabel();
        updateControlLabels();
        updateAppearanceLabels();
        refreshingStyle = previous;
        renderPreview();
    }

    private void updateSizeLabel() {
        sizeValue.setText(widthSize.getProgress() + " × " + heightSize.getProgress() + " dp");
    }

    private void updateTextGapLabel() {
        int value = textGap.getProgress();
        textGapValue.setText((value > 0 ? "+" : "") + value + " dp");
    }

    private void updateControlLabels() {
        controlPanelHeightValue.setText(controlPanelHeight.getProgress() + " dp");
        controlIconScaleValue.setText(controlIconScale.getProgress() + " %");
        controlSpreadValue.setText(controlSpread.getProgress() + " % ширины");
        controlBottomInsetValue.setText(controlBottomInset.getProgress() + " dp");
    }

    private void updateAppearanceLabels() {
        topInsetSetting.value.setText(topInsetSetting.seek.getProgress() + " dp");
        contentInsetSetting.value.setText(contentInsetSetting.seek.getProgress() + " dp");
        topRowTextSetting.value.setText(topRowTextSetting.seek.getProgress() + " sp");
        titleTextSetting.value.setText(titleTextSetting.seek.getProgress() + " sp");
        subtitleTextSetting.value.setText(subtitleTextSetting.seek.getProgress() + " sp");
        subtitleGapSetting.value.setText(subtitleGapSetting.seek.getProgress() + " dp");
        timeTextSetting.value.setText(timeTextSetting.seek.getProgress() + " sp");
        progressGapSetting.value.setText(progressGapSetting.seek.getProgress() + " dp");
        progressThicknessSetting.value.setText(
                progressThicknessSetting.seek.getProgress() + " dp");
    }

    private WidgetAppearance currentAppearance() {
        if (topInsetSetting == null) return prefs.appearance(currentStyle());
        return new WidgetAppearance(
                textGap.getProgress(),
                controlPanelHeight.getProgress(),
                controlIconScale.getProgress(),
                controlSpread.getProgress(),
                controlBottomInset.getProgress(),
                topInsetSetting.seek.getProgress(),
                contentInsetSetting.seek.getProgress(),
                topRowTextSetting.seek.getProgress(),
                titleTextSetting.seek.getProgress(),
                subtitleTextSetting.seek.getProgress(),
                subtitleGapSetting.seek.getProgress(),
                timeTextSetting.seek.getProgress(),
                progressGapSetting.seek.getProgress(),
                progressThicknessSetting.seek.getProgress());
    }

    private void saveAppearance() {
        prefs.putAppearance(currentStyle(), currentAppearance());
        renderPreview();
        refreshOverlayIfRunning();
    }

    private void refreshOverlayIfRunning() {
        if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
            OverlayService.refreshStyle(this);
        }
    }

    private LabeledSeek addLabeledSeek(LinearLayout parent, String label, int min, int max) {
        parent.addView(text(label, 14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        TextView value = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        parent.addView(value, fullWrap());
        SeekBar seek = sizeSeekBar(min, max);
        parent.addView(seek, fullWrap());
        return new LabeledSeek(value, seek);
    }

    private static void bind(SeekBar.OnSeekBarChangeListener listener,
            LabeledSeek... settings) {
        for (LabeledSeek setting : settings) setting.seek.setOnSeekBarChangeListener(listener);
    }

    private void renderPreview() {
        if (previewHost == null || widthSize == null || topInsetSetting == null) return;
        int configuredWidthDp = widthSize.getProgress();
        int configuredHeightDp = heightSize.getProgress();
        int maxWidthPx = Math.max(Ui.dp(this, 280),
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 112));
        int maxHeightPx = Math.max(Ui.dp(this, 180), Math.min(Ui.dp(this, 520),
                getResources().getDisplayMetrics().heightPixels / 2));
        int configuredWidthPx = Ui.dp(this, configuredWidthDp);
        int configuredHeightPx = Ui.dp(this, configuredHeightDp);
        float scale = Math.min(1f, Math.min(
                maxWidthPx / (float) configuredWidthPx,
                maxHeightPx / (float) configuredHeightPx));

        MediaCardView preview = new MediaCardView(this, configuredWidthDp, configuredHeightDp,
                configuredWidthPx, configuredHeightPx, currentStyle(), currentAppearance(),
                previewListener);
        preview.renderSnapshot(previewSnapshot(), true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        previewHost.removeAllViews();
        FrameLayout stage = new FrameLayout(this);
        stage.addView(preview, new FrameLayout.LayoutParams(
                preview.cardWidth(), preview.cardHeight()));
        stage.setPivotX(preview.cardWidth() / 2f);
        stage.setPivotY(0f);
        stage.setScaleX(scale);
        stage.setScaleY(scale);
        FrameLayout.LayoutParams stageParams = new FrameLayout.LayoutParams(
                preview.cardWidth(), preview.cardHeight(), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        stageParams.topMargin = Ui.dp(this, 8);
        previewHost.addView(stage, stageParams);
        View touchBlocker = new View(this);
        touchBlocker.setClickable(true);
        previewHost.addView(touchBlocker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams hostParams = (LinearLayout.LayoutParams)
                previewHost.getLayoutParams();
        hostParams.height = Math.round(preview.cardHeight() * scale) + Ui.dp(this, 16);
        previewHost.setLayoutParams(hostParams);
    }

    private MediaSnapshot previewSnapshot() {
        long capabilities = MediaBridgeContract.CAP_PLAY | MediaBridgeContract.CAP_PAUSE
                | MediaBridgeContract.CAP_TOGGLE | MediaBridgeContract.CAP_NEXT
                | MediaBridgeContract.CAP_PREVIOUS | MediaBridgeContract.CAP_SEEK
                | MediaBridgeContract.CAP_SET_SOURCE;
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, System.currentTimeMillis(), true, 0, "",
                MediaSource.Id.BT, "",
                Arrays.asList(
                        new MediaSource(MediaSource.Id.BT, true, true, true, capabilities),
                        new MediaSource(MediaSource.Id.RADIO, true, true, false, capabilities),
                        new MediaSource(MediaSource.Id.USB, true, true, false, capabilities),
                        new MediaSource(MediaSource.Id.ONLINE, true, true, false, capabilities)),
                "preview", "Bluetooth", "preview-track", "Ветер перемен",
                "Кино", "Группа крови", 232_000L, 84_000L,
                SystemClock.elapsedRealtime(), 1f, MediaSnapshot.STATE_PLAYING,
                0, "", 0L, capabilities, "", 0L);
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

    private static final class LabeledSeek {
        final TextView value;
        final SeekBar seek;

        LabeledSeek(TextView value, SeekBar seek) {
            this.value = value;
            this.seek = seek;
        }
    }
}
