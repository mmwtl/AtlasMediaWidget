package com.mmwtl.atlasmediawidget;

import android.Manifest;
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
import android.view.ViewGroup;
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

public final class MainActivity extends ScaledActivity {
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
        View content = buildContent();
        setContentView(content);
        Ui.applySystemBarInsets(content);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildContent() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout stickyPreview = new LinearLayout(this);
        stickyPreview.setOrientation(LinearLayout.VERTICAL);
        stickyPreview.setClipChildren(false);
        stickyPreview.setPadding(Ui.dp(this, 24), Ui.dp(this, 16),
                Ui.dp(this, 24), Ui.dp(this, 12));
        stickyPreview.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout previewTitleRow = new LinearLayout(this);
        previewTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        previewTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(R.string.app_name), 24, Ui.PRIMARY, Typeface.BOLD);
        previewTitleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView previewTitle = text(getString(R.string.preview_title), 13,
                Ui.SECONDARY, Typeface.NORMAL);
        previewTitleRow.addView(previewTitle);
        stickyPreview.addView(previewTitleRow);

        previewHost = new FrameLayout(this);
        previewHost.setClipChildren(false);
        previewHost.setClipToPadding(false);
        previewHost.setBackground(Ui.background(Ui.NESTED, 8, this));
        previewHost.setPadding(Ui.dp(this, 8), Ui.dp(this, 10),
                Ui.dp(this, 8), Ui.dp(this, 10));
        previewHost.setContentDescription(getString(R.string.preview_title));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 110));
        previewParams.topMargin = Ui.dp(this, 8);
        stickyPreview.addView(previewHost, previewParams);
        screen.addView(stickyPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 12),
                Ui.dp(this, 24), Ui.dp(this, 42));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView intro = text(getString(R.string.main_subtitle),
                15, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams introParams = fullWrap();
        introParams.topMargin = Ui.dp(this, 10);
        root.addView(intro, introParams);

        LinearLayout accessCard = card();
        accessCard.addView(text(getString(R.string.permissions_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        permissionStatus = text("", 14, Ui.SECONDARY, Typeface.NORMAL);
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
        bridgeCard.addView(text(getString(R.string.bridge_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        bridgeStatus = text("", 14, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams bridgeStatusParams = fullWrap();
        bridgeStatusParams.topMargin = Ui.dp(this, 10);
        bridgeCard.addView(bridgeStatus, bridgeStatusParams);
        Button openBridge = actionButton("Открыть GInputBridge");
        openBridge.setOnClickListener(v -> openGInputBridge());
        bridgeCard.addView(openBridge, buttonParams());

        LinearLayout serviceCard = card();
        serviceCard.addView(text(getString(R.string.appearance_title),
                20, Ui.PRIMARY, Typeface.BOLD));

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

        LinearLayout typographyCard = card();
        typographyCard.addView(text("Текст и отступы", 20, Ui.PRIMARY, Typeface.BOLD));
        typographyCard.addView(text("Сдвиг блока текста по вертикали",
                14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        textGapValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        typographyCard.addView(textGapValue, fullWrap());
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
        typographyCard.addView(textGap, fullWrap());

        topInsetSetting = addLabeledSeek(typographyCard, "Отступ верхней строки",
                Prefs.MIN_TOP_INSET_DP, Prefs.MAX_TOP_INSET_DP);
        contentInsetSetting = addLabeledSeek(typographyCard, "Боковой отступ контента",
                Prefs.MIN_CONTENT_INSET_DP, Prefs.MAX_CONTENT_INSET_DP);
        topRowTextSetting = addLabeledSeek(typographyCard, "Размер текста верхней строки",
                Prefs.MIN_TOP_ROW_TEXT_SIZE_SP, Prefs.MAX_TOP_ROW_TEXT_SIZE_SP);
        titleTextSetting = addLabeledSeek(typographyCard, "Размер названия",
                Prefs.MIN_TITLE_TEXT_SIZE_SP, Prefs.MAX_TITLE_TEXT_SIZE_SP);
        subtitleTextSetting = addLabeledSeek(typographyCard, "Размер исполнителя и альбома",
                Prefs.MIN_SUBTITLE_TEXT_SIZE_SP, Prefs.MAX_SUBTITLE_TEXT_SIZE_SP);
        subtitleGapSetting = addLabeledSeek(typographyCard, "Отступ подзаголовка",
                0, Prefs.MAX_SUBTITLE_GAP_DP);
        timeTextSetting = addLabeledSeek(typographyCard, "Размер времени",
                Prefs.MIN_TIME_TEXT_SIZE_SP, Prefs.MAX_TIME_TEXT_SIZE_SP);
        progressGapSetting = addLabeledSeek(typographyCard, "Отступ прогресса от панели",
                0, Prefs.MAX_PROGRESS_GAP_DP);
        progressThicknessSetting = addLabeledSeek(typographyCard, "Толщина линии прогресса",
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
        typographyCard.addView(resetAppearance, buttonParams());

        LinearLayout controlsCard = card();
        controlsCard.addView(text("Панель управления", 20, Ui.PRIMARY, Typeface.BOLD));
        controlsCard.addView(text("Высота нижней панели", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlPanelHeightValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        controlsCard.addView(controlPanelHeightValue, fullWrap());
        controlPanelHeight = sizeSeekBar(Prefs.MIN_CONTROL_PANEL_HEIGHT_DP,
                Prefs.MAX_CONTROL_PANEL_HEIGHT_DP);
        controlsCard.addView(controlPanelHeight, fullWrap());

        controlsCard.addView(text("Размер иконок", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlIconScaleValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        controlsCard.addView(controlIconScaleValue, fullWrap());
        controlIconScale = sizeSeekBar(Prefs.MIN_CONTROL_ICON_SCALE_PERCENT,
                Prefs.MAX_CONTROL_ICON_SCALE_PERCENT);
        controlsCard.addView(controlIconScale, fullWrap());

        controlsCard.addView(text("Разбежка боковых иконок от центра", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlSpreadValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        controlsCard.addView(controlSpreadValue, fullWrap());
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
        controlsCard.addView(controlSpread, fullWrap());

        controlsCard.addView(text("Дополнительный отступ иконок от нижней границы", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlBottomInsetValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        controlsCard.addView(controlBottomInsetValue, fullWrap());
        controlBottomInset = sizeSeekBar(0, Prefs.MAX_CONTROL_BOTTOM_INSET_DP);
        controlBottomInset.setOnSeekBarChangeListener(controlListener);
        controlsCard.addView(controlBottomInset, fullWrap());

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
        controlsCard.addView(resetControls, buttonParams());

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

        LinearLayout runtimeCard = card();
        runtimeCard.addView(text(getString(R.string.service_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        serviceButton = actionButton("Запустить");
        serviceButton.setOnClickListener(v -> toggleService());
        runtimeCard.addView(serviceButton, buttonParams());
        autoStart = new Switch(this);
        autoStart.setText("Автозапуск после загрузки ГУ");
        autoStart.setTextColor(Ui.PRIMARY);
        autoStart.setTextSize(15);
        autoStart.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed()) prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
        });
        LinearLayout.LayoutParams switchParams = fullWrap();
        switchParams.topMargin = Ui.dp(this, 14);
        runtimeCard.addView(autoStart, switchParams);

        LinearLayout behaviorCard = card();
        behaviorCard.addView(text(getString(R.string.behavior_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView note = text(
                "Карточка отображается только когда HOME находится на переднем плане. "
                        + "Перетаскивание выполняется за кнопку ⋮ в правом верхнем углу. "
                        + "Нажатие на свободную область открывает активный медиаисточник.",
                14, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams noteParams = fullWrap();
        noteParams.topMargin = Ui.dp(this, 8);
        behaviorCard.addView(note, noteParams);

        LinearLayout scaleCard = card();
        scaleCard.addView(text(getString(R.string.scale_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView scaleHint = text(getString(R.string.scale_hint),
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams scaleHintParams = fullWrap();
        scaleHintParams.topMargin = Ui.dp(this, 6);
        scaleCard.addView(scaleHint, scaleHintParams);
        addScaleSlider(scaleCard);

        addSectionHeading(root, getString(R.string.settings_section_system), true);
        root.addView(accessCard);
        root.addView(bridgeCard);
        root.addView(runtimeCard);

        addSectionHeading(root, getString(R.string.settings_section_content), false);
        root.addView(behaviorCard);

        addSectionHeading(root, getString(R.string.settings_section_visual), false);
        root.addView(serviceCard);
        root.addView(typographyCard);
        root.addView(controlsCard);
        root.addView(scaleCard);

        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return screen;
    }

    private void addSectionHeading(LinearLayout parent, String label, boolean first) {
        TextView heading = text(label, 16, Ui.ACCENT, Typeface.BOLD);
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, first ? 20 : 14);
        params.bottomMargin = Ui.dp(this, 10);
        parent.addView(heading, params);
    }

    private void addScaleSlider(LinearLayout parent) {
        int current = configuredScaleTenths(this);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = fullWrap();
        headerParams.topMargin = Ui.dp(this, 15);
        parent.addView(header, headerParams);
        header.addView(text(getString(R.string.scale), 14, Ui.PRIMARY, Typeface.NORMAL),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text(formatScale(current), 14, Ui.SECONDARY, Typeface.NORMAL);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(value);

        SeekBar scale = sizeSeekBar(MIN_SCALE_TENTHS, MAX_SCALE_TENTHS);
        scale.setProgress(current);
        scale.setContentDescription(getString(R.string.scale_content_description));
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress,
                    boolean fromUser) {
                value.setText(formatScale(progress));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int selected = seekBar.getProgress();
                if (selected != configuredScaleTenths(MainActivity.this)) {
                    prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, selected);
                    recreate();
                }
            }
        });
        parent.addView(scale, fullWrap());
    }

    private static String formatScale(int tenths) {
        return tenths % 10 == 0
                ? tenths / 10 + "×"
                : tenths / 10 + "." + tenths % 10 + "×";
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
        serviceButton.setBackground(Ui.background(enabled ? Ui.NESTED : Ui.ACCENT, 8, this));
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
        return Ui.card(this);
    }

    private Button actionButton(String label) {
        return Ui.button(this, label);
    }

    private RadioButton styleButton(String label) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setTextSize(15);
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
        if (previewHost.getWidth() <= 0) {
            previewHost.post(this::renderPreview);
            return;
        }
        int configuredWidthDp = widthSize.getProgress();
        int configuredHeightDp = heightSize.getProgress();
        android.content.Context widgetContext = getApplicationContext();
        int configuredWidthPx = Ui.dp(widgetContext, configuredWidthDp);
        int configuredHeightPx = Ui.dp(widgetContext, configuredHeightDp);
        int maxWidthPx = Math.max(1, previewHost.getWidth()
                - previewHost.getPaddingLeft() - previewHost.getPaddingRight());
        int maxContainerHeightPx = Math.max(1, Math.round(
                getWindowManager().getCurrentWindowMetrics().getBounds().height() * 0.30f));
        int verticalPadding = previewHost.getPaddingTop() + previewHost.getPaddingBottom();
        int maxHeightPx = Math.max(1, maxContainerHeightPx - verticalPadding);
        float scale = Math.min(1f, Math.min(
                maxWidthPx / (float) configuredWidthPx,
                maxHeightPx / (float) configuredHeightPx));

        MediaCardView preview = new MediaCardView(widgetContext,
                configuredWidthDp, configuredHeightDp,
                configuredWidthPx, configuredHeightPx, currentStyle(), currentAppearance(),
                previewListener);
        preview.renderSnapshot(previewSnapshot(), true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        previewHost.removeAllViews();
        FrameLayout stage = new FrameLayout(this);
        stage.addView(preview, new FrameLayout.LayoutParams(
                preview.cardWidth(), preview.cardHeight()));
        stage.setPivotX(preview.cardWidth() / 2f);
        stage.setPivotY(preview.cardHeight() / 2f);
        stage.setScaleX(scale);
        stage.setScaleY(scale);
        FrameLayout.LayoutParams stageParams = new FrameLayout.LayoutParams(
                preview.cardWidth(), preview.cardHeight(), Gravity.CENTER);
        previewHost.addView(stage, stageParams);
        View touchBlocker = new View(this);
        touchBlocker.setClickable(true);
        previewHost.addView(touchBlocker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams hostParams = (LinearLayout.LayoutParams)
                previewHost.getLayoutParams();
        hostParams.height = Math.min(maxContainerHeightPx,
                Math.round(preview.cardHeight() * scale) + verticalPadding);
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
        TextView view = Ui.text(this, value, size, color);
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
