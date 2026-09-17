package com.mmwtl.atlasmediawidget;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends ScaledActivity {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 33;
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQUEST_IMPORT_SETTINGS = 4102;
    private static final int REQUEST_IMPORT_RADIO_CATALOG = 4103;
    private static final long SETTINGS_READINESS_TIMEOUT_MS = 20_000L;
    private static final String MEDIA_NOTIFICATION_LISTENER_CLASS =
            "com.mmwtl.atlasmediaapi.media.session.MediaNotificationListenerService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private MediaBridgeClient mediaBridgeClient;
    private MediaSettingsSnapshot currentMediaSettings;
    private boolean importInProgress;
    private boolean recoveryInProgress;
    private boolean mediaSettingsBusy;
    private boolean settingsTransferBusy;
    private boolean radioCatalogBusy;
    private File pendingRadioImportFile;
    private final Map<String, Button> sourceTileButtons = new HashMap<>();
    private TextView defaultSourceTitle;
    private TextView defaultSourceDelayLabel;
    private SeekBar defaultSourceDelaySeekBar;
    private TextView onlinePlayerTitle;
    private Spinner onlinePlayerSpinner;
    private ArrayAdapter<OnlinePlayerOption> onlinePlayerAdapter;
    private boolean refreshingOnlinePlayer;
    private Switch minimizeOnlinePlayerSwitch;
    private Switch startupAutoplaySwitch;
    private Switch sourceLostSwitch;
    private Switch sourceLostAutoplaySwitch;
    private Switch switchToOnlineSwitch;
    private Switch radioWidgetBroadcastSwitch;
    private Switch clusterCoversSwitch;
    private Switch clusterOnlineSwitch;
    private Switch clusterOnlineProgressSwitch;
    private TextView clusterWatchdogLabel;
    private SeekBar clusterWatchdogSeekBar;
    private TextView clusterReassertBurstLabel;
    private SeekBar clusterReassertBurstSeekBar;
    private TextView radioCatalogInfoText;
    private Button restoreDefaultRadioCatalogButton;
    private TextView mediaStatusText;
    private LinearLayout mediaSettingsGroup;
    private Button overlayPermissionButton;
    private Button usageAccessButton;
    private Button accessibilityAccessButton;
    private Button notificationAccessButton;
    private Button storageAccessButton;
    private Button serviceButton;
    private Switch autoStart;
    private Switch radioSavedNavigation;
    private Switch radioFavoritesNavigation;
    private Switch dragHandleVisible;
    private Button exportSettingsButton;
    private Button importSettingsButton;
    private Button exportRadioCatalogButton;
    private Button importRadioCatalogButton;
    private RadioGroup coverDimPresetGroup;
    private RadioButton[] coverDimPresetButtons;
    private EditText widthSize;
    private EditText heightSize;
    private SeekBar hideThreshold;
    private Spinner positionCornerSpinner;
    private EditText positionX;
    private EditText positionY;
    private OverlayCorner displayedPositionCorner;
    private boolean refreshingGeometry;
    private boolean geometryApplyPending;
    private final Runnable applyGeometryDelayed = () -> {
        geometryApplyPending = false;
        applyGeometry();
    };
    private TextView metadataProgressGapValue;
    private SeekBar metadataProgressGap;
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
    private TextView favoriteColumnsValue;
    private SeekBar favoriteColumns;
    private TextView favoriteRowsValue;
    private SeekBar favoriteRows;
    private boolean refreshingStyle;

    private final MediaCardView.Listener previewListener = new MediaCardView.Listener() {
        @Override public boolean onDragTouch(View view, MotionEvent event) { return true; }
        @Override public void onCommand(String command) {}
        @Override public void onSeek(long positionMs) {}
        @Override public void onSource(MediaSource.Id source) {}
        @Override public void onOpenSource() {}
        @Override public void onRadioStationsRequested() {}
        @Override public void onRadioStation(RadioStation station) {}
        @Override public void onRadioArtworkRequested(RadioStation station) {}
    };

    private final MediaBridgeClient.Listener mediaBridgeListener = new MediaBridgeClient.Listener() {
        @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
            main.post(() -> {
                if (isDestroyed()) return;
                if (mediaStatusText != null) {
                    if (state == MediaBridgeClient.State.CONNECTED) {
                        mediaStatusText.setText("Медиасервис подключён.");
                        mediaStatusText.setTextColor(Ui.ACCENT);
                        if (mediaBridgeClient.isSettingsSupported()) {
                            checkPendingImportRecovery();
                            loadMediaSettings();
                        }
                    } else if (state == MediaBridgeClient.State.CONNECTING) {
                        currentMediaSettings = null;
                        setMediaControlsEnabled(mediaSettingsGroup, false);
                        setRadioCatalogTransferEnabled(false);
                        mediaStatusText.setText("Подключение к медиасервису…");
                        mediaStatusText.setTextColor(Ui.SECONDARY);
                    } else {
                        currentMediaSettings = null;
                        setMediaControlsEnabled(mediaSettingsGroup, false);
                        setRadioCatalogTransferEnabled(false);
                        mediaStatusText.setText("Медиасервис недоступен: " + (detail != null ? detail : state.name()));
                        mediaStatusText.setTextColor(Ui.ERROR);
                    }
                }
            });
        }

        @Override public void onSnapshot(MediaSnapshot snapshot) {}
        @Override public void onCommandResult(String requestId, int status, String message, long generation) {}
        @Override public void onRadioStations(RadioStationLists lists) {}
        @Override public void onRadioStationsError(int status, String message) {}
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        mediaBridgeClient = new MediaBridgeClient(this, mediaBridgeListener);
        View content = buildContent();
        setContentView(content);
        Ui.applySystemBarInsets(content);
    }

    @Override protected void onStart() {
        super.onStart();
        if (mediaBridgeClient != null) {
            mediaBridgeClient.start();
        }
    }

    @Override protected void onPause() {
        if (geometryApplyPending) {
            main.removeCallbacks(applyGeometryDelayed);
            applyGeometryDelayed.run();
        }
        super.onPause();
    }

    @Override protected void onStop() {
        if (mediaBridgeClient != null) {
            mediaBridgeClient.stop();
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        checkPendingImportRecovery();
        refresh();
        if (mediaBridgeClient != null && mediaBridgeClient.isSettingsSupported()) {
            loadMediaSettings();
        }
    }

    @Override protected void onDestroy() {
        if (mediaBridgeClient != null) {
            mediaBridgeClient.stop();
        }
        if (pendingRadioImportFile != null) {
            pendingRadioImportFile.delete();
            pendingRadioImportFile = null;
        }
        main.removeCallbacksAndMessages(null);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_SETTINGS && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            readSettingsForImport(data.getData());
        } else if (requestCode == REQUEST_IMPORT_RADIO_CATALOG && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            readRadioCatalogForImport(data.getData());
        }
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
        overlayPermissionButton = actionButton("Разрешить поверх окон");
        overlayPermissionButton.setOnClickListener(v -> openOverlaySettings());
        accessCard.addView(overlayPermissionButton, buttonParams());
        usageAccessButton = actionButton("Разрешить историю использования");
        usageAccessButton.setOnClickListener(v -> openUsageSettingsForApp());
        accessCard.addView(usageAccessButton, buttonParams());
        accessibilityAccessButton = actionButton(getString(R.string.allow_accessibility));
        accessibilityAccessButton.setOnClickListener(v -> openAccessibilitySettings());
        accessCard.addView(accessibilityAccessButton, buttonParams());
        notificationAccessButton = actionButton("Разрешить доступ к уведомлениям (медиа)");
        notificationAccessButton.setOnClickListener(v -> openNotificationAccessSettings());
        accessCard.addView(notificationAccessButton, buttonParams());
        storageAccessButton = actionButton("Разрешить доступ к хранилищу (USB)");
        storageAccessButton.setOnClickListener(v -> requestStorageAccess());
        accessCard.addView(storageAccessButton, buttonParams());

        LinearLayout serviceCard = card();
        serviceCard.addView(text(getString(R.string.appearance_title),
                20, Ui.PRIMARY, Typeface.BOLD));

        TextView coverDimTitle = text("Затемнение обложки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams coverDimTitleParams = fullWrap();
        coverDimTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(coverDimTitle, coverDimTitleParams);
        coverDimPresetGroup = new RadioGroup(this);
        coverDimPresetGroup.setOrientation(RadioGroup.HORIZONTAL);
        coverDimPresetButtons = new RadioButton[CoverDimPreset.values().length];
        for (CoverDimPreset preset : CoverDimPreset.values()) {
            RadioButton button = styleButton(preset.label);
            button.setTextSize(11);
            button.setTag(preset);
            coverDimPresetButtons[preset.preferenceValue] = button;
            coverDimPresetGroup.addView(button, new RadioGroup.LayoutParams(0,
                    RadioGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        coverDimPresetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!refreshingStyle) saveAppearance();
        });
        serviceCard.addView(coverDimPresetGroup, fullWrap());

        TextView sizeTitle = text("Размер карточки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams sizeTitleParams = fullWrap();
        sizeTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(sizeTitle, sizeTitleParams);
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        widthSize = numberInput();
        widthSize.setHint("Ширина");
        widthSize.setContentDescription("Ширина карточки в пикселях");
        sizeRow.addView(widthSize, sizeInputParams());
        sizeRow.addView(text("px", 14, Ui.SECONDARY, Typeface.NORMAL),
                compactUnitParams());
        sizeRow.addView(new View(this), sizeSpacerParams());
        sizeRow.addView(text("×", 18, Ui.PRIMARY, Typeface.BOLD), compactUnitParams());
        sizeRow.addView(new View(this), sizeSpacerParams());
        heightSize = numberInput();
        heightSize.setHint("Высота");
        heightSize.setContentDescription("Высота карточки в пикселях");
        sizeRow.addView(heightSize, sizeInputParams());
        sizeRow.addView(text("px", 14, Ui.SECONDARY, Typeface.NORMAL),
                compactUnitParams());
        serviceCard.addView(sizeRow, fullWrap());

        TextView positionTitle = text("Положение карточки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams positionTitleParams = fullWrap();
        positionTitleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(positionTitle, positionTitleParams);
        positionCornerSpinner = new Spinner(this);
        positionCornerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, cornerLabels()));
        positionCornerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (!refreshingGeometry && positionX != null && positionY != null) {
                    reanchorPositionFields(OverlayCorner.values()[position]);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        FrameLayout positionCornerField = new FrameLayout(this);
        positionCornerField.setBackground(Ui.background(Ui.NESTED, 8, this));
        positionCornerSpinner.setBackgroundColor(0x00000000);
        positionCornerSpinner.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 34), 0);
        positionCornerField.addView(positionCornerSpinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView positionCornerArrow = text("▾", 16, Ui.ACCENT, Typeface.BOLD);
        positionCornerArrow.setGravity(Gravity.CENTER);
        positionCornerArrow.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams positionCornerArrowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        positionCornerArrowParams.rightMargin = Ui.dp(this, 10);
        positionCornerField.addView(positionCornerArrow, positionCornerArrowParams);
        positionCornerField.setOnClickListener(v -> positionCornerSpinner.performClick());

        LinearLayout positionGrid = new LinearLayout(this);
        positionGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout positionHeader = new LinearLayout(this);
        positionHeader.setOrientation(LinearLayout.HORIZONTAL);
        positionHeader.addView(text("Угол привязки", 14, Ui.SECONDARY, Typeface.BOLD),
                positionColumnParams(1.25f, 0));
        positionHeader.addView(text("Отступ X", 14, Ui.SECONDARY, Typeface.BOLD),
                positionColumnParams(1f, 8));
        positionHeader.addView(text("Отступ Y", 14, Ui.SECONDARY, Typeface.BOLD),
                positionColumnParams(1f, 8));
        positionGrid.addView(positionHeader, fullWrap());

        LinearLayout positionRow = new LinearLayout(this);
        positionRow.setOrientation(LinearLayout.HORIZONTAL);
        positionRow.setGravity(Gravity.CENTER_VERTICAL);
        positionRow.addView(positionCornerField, positionColumnParams(1.25f, 0));
        positionX = numberInput();
        positionX.setHint("X");
        positionX.setContentDescription("Отступ по X в пикселях");
        LinearLayout xCell = new LinearLayout(this);
        xCell.setOrientation(LinearLayout.HORIZONTAL);
        xCell.addView(positionX, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        xCell.addView(text("px", 14, Ui.SECONDARY, Typeface.NORMAL), compactUnitParams());
        positionRow.addView(xCell, positionColumnParams(1f, 8));
        positionY = numberInput();
        positionY.setHint("Y");
        positionY.setContentDescription("Отступ по Y в пикселях");
        LinearLayout yCell = new LinearLayout(this);
        yCell.setOrientation(LinearLayout.HORIZONTAL);
        yCell.addView(positionY, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        yCell.addView(text("px", 14, Ui.SECONDARY, Typeface.NORMAL), compactUnitParams());
        positionRow.addView(yCell, positionColumnParams(1f, 8));
        positionGrid.addView(positionRow, fullWrap());
        serviceCard.addView(positionGrid, fullWrap());
        dragHandleVisible = new Switch(this);
        dragHandleVisible.setText("Показывать точки перемещения на виджете");
        dragHandleVisible.setTextColor(Ui.PRIMARY);
        dragHandleVisible.setTextSize(15);
        dragHandleVisible.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_DRAG_HANDLE_VISIBLE, checked);
            renderPreview();
            refreshOverlayIfRunning();
        });
        LinearLayout.LayoutParams dragHandleParams = fullWrap();
        dragHandleParams.topMargin = Ui.dp(this, 14);
        serviceCard.addView(dragHandleVisible, dragHandleParams);
        TextView dragHandleHint = text(
                "Если точки скрыты, включите их здесь снова, чтобы переместить виджет.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams dragHandleHintParams = fullWrap();
        dragHandleHintParams.topMargin = Ui.dp(this, 5);
        serviceCard.addView(dragHandleHint, dragHandleHintParams);
        TextWatcher geometryWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                    int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                if (refreshingGeometry || refreshingStyle) return;
                main.removeCallbacks(applyGeometryDelayed);
                geometryApplyPending = true;
                main.postDelayed(applyGeometryDelayed, 400L);
            }
        };
        widthSize.addTextChangedListener(geometryWatcher);
        heightSize.addTextChangedListener(geometryWatcher);
        positionX.addTextChangedListener(geometryWatcher);
        positionY.addTextChangedListener(geometryWatcher);

        LinearLayout typographyCard = card();
        typographyCard.addView(text("Текст и отступы", 20, Ui.PRIMARY, Typeface.BOLD));
        typographyCard.addView(text("Отступ текста от прогресса",
                14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        metadataProgressGapValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        typographyCard.addView(metadataProgressGapValue, fullWrap());
        metadataProgressGap = sizeSeekBar(Prefs.MIN_METADATA_PROGRESS_GAP_DP,
                Prefs.MAX_METADATA_PROGRESS_GAP_DP);
        metadataProgressGap.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateMetadataProgressGapLabel();
                if (fromUser) renderPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (refreshingStyle) return;
                saveAppearance();
            }
        });
        typographyCard.addView(metadataProgressGap, fullWrap());

        topInsetSetting = addLabeledSeek(typographyCard, "Отступ верхней строки",
                Prefs.MIN_TOP_INSET_DP, Prefs.MAX_TOP_INSET_DP);
        contentInsetSetting = addLabeledSeek(typographyCard, "Боковой отступ контента",
                Prefs.MIN_CONTENT_INSET_DP, Prefs.MAX_CONTENT_INSET_DP);
        topRowTextSetting = addLabeledSeek(typographyCard,
                "Размер плашек источника и избранного",
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
            CardStyle current = CardStyle.DEFAULT;
            WidgetAppearance defaults = WidgetAppearance.defaults(current);
            WidgetAppearance existing = currentAppearance();
            prefs.putAppearance(current, new WidgetAppearance(
                    defaults.metadataProgressGapDp,
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
                    defaults.progressThicknessDp,
                    existing.coverDimPreset));
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
            CardStyle current = CardStyle.DEFAULT;
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
            prefs.putCardSizePx(500, 500);
            refreshSizeControls(CardStyle.DEFAULT);
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
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_AUTO_START, checked);
            if (checked && (!Settings.canDrawOverlays(this)
                    || !ForegroundAppDetector.hasUsageAccess(this)
                    || !AccessibilityWindowState.isEnabled(this))) {
                Toast.makeText(this, R.string.auto_start_permission_warning,
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams switchParams = fullWrap();
        switchParams.topMargin = Ui.dp(this, 14);
        runtimeCard.addView(autoStart, switchParams);

        LinearLayout behaviorCard = card();
        behaviorCard.addView(text(getString(R.string.behavior_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView note = text(
                "Карточка отображается только когда HOME находится на переднем плане. "
                        + "Перетаскивание выполняется за точки ⋮ в правом верхнем углу. "
                        + "Нажатие на свободную область открывает активный медиаисточник.",
                14, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams noteParams = fullWrap();
        noteParams.topMargin = Ui.dp(this, 8);
        behaviorCard.addView(note, noteParams);
        radioSavedNavigation = new Switch(this);
        radioSavedNavigation.setText("Переключать радио без поиска по эфиру");
        radioSavedNavigation.setTextColor(Ui.PRIMARY);
        radioSavedNavigation.setTextSize(15);
        radioSavedNavigation.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, checked);
            updateRadioNavigationControls();
            refreshOverlayIfRunning();
        });
        LinearLayout.LayoutParams radioNavigationParams = fullWrap();
        radioNavigationParams.topMargin = Ui.dp(this, 14);
        behaviorCard.addView(radioSavedNavigation, radioNavigationParams);
        TextView radioNavigationHint = text(
                "Когда Радио активно, кнопки назад и вперёд напрямую выбирают соседнюю "
                        + "станцию из выбранного ниже списка.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams radioHintParams = fullWrap();
        radioHintParams.topMargin = Ui.dp(this, 5);
        behaviorCard.addView(radioNavigationHint, radioHintParams);
        radioFavoritesNavigation = new Switch(this);
        radioFavoritesNavigation.setText("Переключать только по избранным");
        radioFavoritesNavigation.setTextColor(Ui.PRIMARY);
        radioFavoritesNavigation.setTextSize(15);
        radioFavoritesNavigation.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_RADIO_FAVORITES_NAVIGATION, checked);
            refreshOverlayIfRunning();
        });
        LinearLayout.LayoutParams radioFavoritesNavigationParams = fullWrap();
        radioFavoritesNavigationParams.topMargin = Ui.dp(this, 10);
        behaviorCard.addView(radioFavoritesNavigation, radioFavoritesNavigationParams);
        TextView radioFavoritesNavigationHint = text(
                "Если выключено, кнопки перелистывают все сохранённые станции.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams radioFavoritesHintParams = fullWrap();
        radioFavoritesHintParams.topMargin = Ui.dp(this, 5);
        behaviorCard.addView(radioFavoritesNavigationHint, radioFavoritesHintParams);
        LinearLayout favoritesGridCard = card();
        favoritesGridCard.addView(text("Сетка избранных радиостанций",
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView favoritesGridHint = text(
                "Настройте число столбцов и строк в первом экране списка избранного. "
                        + "Остальные станции доступны прокруткой.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams favoritesGridHintParams = fullWrap();
        favoritesGridHintParams.topMargin = Ui.dp(this, 8);
        favoritesGridCard.addView(favoritesGridHint, favoritesGridHintParams);
        favoritesGridCard.addView(text("Столбцы", 14, Ui.SECONDARY, Typeface.NORMAL),
                labelParams());
        favoriteColumnsValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        favoritesGridCard.addView(favoriteColumnsValue, fullWrap());
        favoriteColumns = sizeSeekBar(Prefs.MIN_RADIO_FAVORITES_GRID_COLUMNS,
                Prefs.MAX_RADIO_FAVORITES_GRID_COLUMNS);
        favoritesGridCard.addView(favoriteColumns, fullWrap());
        favoritesGridCard.addView(text("Строки", 14, Ui.SECONDARY, Typeface.NORMAL),
                labelParams());
        favoriteRowsValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        favoritesGridCard.addView(favoriteRowsValue, fullWrap());
        favoriteRows = sizeSeekBar(Prefs.MIN_RADIO_FAVORITES_GRID_ROWS,
                Prefs.MAX_RADIO_FAVORITES_GRID_ROWS);
        favoritesGridCard.addView(favoriteRows, fullWrap());
        SeekBar.OnSeekBarChangeListener favoritesGridListener =
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        updateFavoriteGridLabels();
                    }

                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override public void onStopTrackingTouch(SeekBar seekBar) {
                        prefs.putRadioFavoritesGrid(favoriteColumns.getProgress(),
                                favoriteRows.getProgress());
                        refreshOverlayIfRunning();
                    }
                };
        favoriteColumns.setOnSeekBarChangeListener(favoritesGridListener);
        favoriteRows.setOnSeekBarChangeListener(favoritesGridListener);

        LinearLayout scaleCard = card();
        scaleCard.addView(text(getString(R.string.scale_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView scaleHint = text(getString(R.string.scale_hint),
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams scaleHintParams = fullWrap();
        scaleHintParams.topMargin = Ui.dp(this, 6);
        scaleCard.addView(scaleHint, scaleHintParams);
        addScaleSlider(scaleCard);

        LinearLayout settingsBackupCard = card();
        settingsBackupCard.addView(text("Настройки: резервная копия",
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView settingsBackupHint = text(
                "Архив ZIP содержит настройки карточки, источника звука и приборной панели. "
                        + "Также поддерживается импорт прежних JSON-настроек.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        settingsBackupHint.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams settingsBackupHintParams = fullWrap();
        settingsBackupHintParams.topMargin = Ui.dp(this, 8);
        settingsBackupCard.addView(settingsBackupHint, settingsBackupHintParams);
        exportSettingsButton = actionButton("Экспортировать настройки (ZIP)");
        exportSettingsButton.setOnClickListener(v -> chooseSettingsExport());
        settingsBackupCard.addView(exportSettingsButton, buttonParams());
        importSettingsButton = actionButton("Импортировать настройки (ZIP / JSON)");
        importSettingsButton.setOnClickListener(v -> chooseSettingsImport());
        settingsBackupCard.addView(importSettingsButton, buttonParams());

        // 1. Секция «Система»
        addSectionHeading(root, getString(R.string.section_system), true);
        root.addView(accessCard);
        root.addView(runtimeCard);

        // 2. Секция «Медиа»
        addSectionHeading(root, getString(R.string.section_media), false);
        LinearLayout mediaCard = createMediaCard();
        root.addView(mediaCard);
        root.addView(createRadioCatalogTransferCard());

        // 3. Секция «Виджет»
        addSectionHeading(root, getString(R.string.section_widget), false);
        root.addView(serviceCard);
        root.addView(createVisibilityCard());
        root.addView(typographyCard);
        root.addView(controlsCard);
        root.addView(behaviorCard);
        root.addView(favoritesGridCard);

        // 4. Секция «Резервная копия»
        addSectionHeading(root, getString(R.string.section_backup), false);
        root.addView(settingsBackupCard);

        // 5. Секция «Диагностика»
        addSectionHeading(root, getString(R.string.section_diagnostics), false);
        LinearLayout diagnosticCard = createDiagnosticCard();
        root.addView(diagnosticCard);
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

    private LinearLayout createVisibilityCard() {
        LinearLayout visibility = card();
        visibility.addView(text("Видимость", 20, Ui.PRIMARY, Typeface.BOLD), fullWrap());
        TextView hint = text("Карточка скрывается, если одно окно достигает порога одновременно "
                + "по ширине и высоте или если все видимые окна приложений вместе покрывают "
                + "этот процент экрана. Пересечения считаются один раз. GSplit и системные "
                + "исключения скрывают карточку при любом размере.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.1f);
        visibility.addView(hint, fullWrap());
        TextView value = text("", 15, Ui.PRIMARY, Typeface.NORMAL);
        visibility.addView(value, fullWrap());
        SeekBar threshold = sizeSeekBar(WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT);
        hideThreshold = threshold;
        threshold.setProgress(prefs.freeformHideThresholdPercent());
        value.setText("Порог скрытия: " + threshold.getProgress() + " %");
        threshold.setContentDescription("Порог скрытия");
        threshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText("Порог скрытия: " + progress + " %");
                if (fromUser) {
                    prefs.putInt(Prefs.KEY_FREEFORM_HIDE_THRESHOLD_PERCENT, progress);
                    OverlayService.onAccessibilityWindowsChanged();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        visibility.addView(threshold, fullWrap());
        return visibility;
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
        if (hideThreshold != null) hideThreshold.setProgress(prefs.freeformHideThresholdPercent());
        boolean overlay = Settings.canDrawOverlays(this);
        boolean usage = ForegroundAppDetector.hasUsageAccess(this);
        boolean accessibility = AccessibilityWindowState.isEnabled(this);
        boolean mediaNotifications = hasMediaNotificationAccess();
        boolean storage = hasStorageAccess();
        updatePermissionButton(overlayPermissionButton, overlay,
                "Поверх окон предоставлено", "Разрешить поверх окон");
        updatePermissionButton(usageAccessButton, usage,
                "История использования предоставлена", "Разрешить историю использования");
        updatePermissionButton(accessibilityAccessButton, accessibility,
                "Спецвозможности предоставлены", getString(R.string.allow_accessibility));
        updatePermissionButton(notificationAccessButton, mediaNotifications,
                "Доступ к уведомлениям (медиа) предоставлен",
                "Разрешить доступ к уведомлениям (медиа)");
        updatePermissionButton(storageAccessButton, storage,
                "Доступ к хранилищу (USB) предоставлен",
                "Разрешить доступ к хранилищу (USB)");
        boolean enabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        serviceButton.setText(enabled ? "Остановить" : "Запустить");
        serviceButton.setBackground(Ui.background(enabled ? Ui.NESTED : Ui.ACCENT, 8, this));
        serviceButton.setEnabled(enabled || overlay && usage && accessibility);
        autoStart.setChecked(prefs.getBoolean(Prefs.KEY_AUTO_START, false));
        radioSavedNavigation.setChecked(
                prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false));
        radioFavoritesNavigation.setChecked(
                prefs.getBoolean(Prefs.KEY_RADIO_FAVORITES_NAVIGATION, false));
        updateRadioNavigationControls();
        dragHandleVisible.setChecked(
                prefs.getBoolean(Prefs.KEY_DRAG_HANDLE_VISIBLE, true));
        refreshFavoriteGridControls();
        refreshingStyle = true;
        refreshSizeControls(CardStyle.DEFAULT);
        refreshingStyle = false;
        requestNotificationPermissionIfNeeded();
    }

    private void toggleService() {
        boolean enabled = prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false);
        if (enabled) {
            prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
            OverlayService.stop(this);
        } else if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
        } else if (!ForegroundAppDetector.hasUsageAccess(this)) {
            openUsageSettingsForApp();
        } else if (!AccessibilityWindowState.isEnabled(this)) {
            openAccessibilitySettings();
        } else {
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

    private void chooseSettingsExport() {
        exportSettings();
    }

    @SuppressWarnings("deprecation")
    private void chooseSettingsImport() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed",
                        "application/json", "text/json", "text/plain",
                        "application/octet-stream"});
        launchFilePicker(picker, REQUEST_IMPORT_SETTINGS);
    }

    private void chooseRadioCatalogImport() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed",
                        "application/octet-stream"});
        launchFilePicker(picker, REQUEST_IMPORT_RADIO_CATALOG);
    }

    @SuppressWarnings("deprecation")
    private void launchFilePicker(Intent picker, int requestCode) {
        try {
            startActivityForResult(picker, requestCode);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "На ГУ нет системного выбора файлов",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportSettings() {
        settingsTransferBusy = true;
        setSettingsTransferEnabled(false);
        setRadioCatalogTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            File tempMediaZip = null;
            File fullZip = null;
            try {
                if (mediaBridgeClient != null && mediaBridgeClient.isSettingsSupported()) {
                    File exportDir = new File(getCacheDir(), "exports");
                    exportDir.mkdirs();
                    tempMediaZip = new File(exportDir, "media_export_" + System.currentTimeMillis() + ".zip");
                    tempMediaZip.delete();

                    final CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] success = new boolean[1];
                    final String[] errorHolder = new String[1];
                    mediaBridgeClient.exportMediaBackup(tempMediaZip, new MediaBridgeClient.BackupCallback() {
                        @Override public void onBackupExported() {
                            success[0] = true;
                            latch.countDown();
                        }
                        @Override public void onError(int code, String message) {
                            errorHolder[0] = message;
                            latch.countDown();
                        }
                    });
                    latch.await(12, TimeUnit.SECONDS);
                    if (!success[0]) {
                        throw new IOException("Ошибка экспорта настроек медиа: "
                                + (errorHolder[0] != null ? errorHolder[0] : "таймаут"));
                    }

                    fullZip = FullSettingsBackup.createFullBackupZip(appContext, prefs, tempMediaZip);
                    tempMediaZip.delete();
                    SettingsExportStore.Result result = SettingsExportStore.exportZip(appContext, fullZip);
                    fullZip.delete();

                    main.post(() -> {
                        settingsTransferBusy = false;
                        if (isDestroyed()) return;
                        setSettingsTransferEnabled(true);
                        setRadioCatalogTransferEnabled(true);
                        Toast.makeText(this, "Резервная копия сохранена: " + result.location,
                                Toast.LENGTH_LONG).show();
                    });
                } else {
                    throw new IOException("Медиасервис недоступен. Полная резервная копия не создана.");

                }
            } catch (Exception error) {
                AppLog.warn("Cannot export settings", error);
                showSettingsTransferError("Не удалось экспортировать настройки", error);
            } finally {
                if (tempMediaZip != null) tempMediaZip.delete();
                if (fullZip != null) fullZip.delete();
            }
        });
    }

    private void exportRadioCatalog() {
        if (radioCatalogBusy || mediaSettingsBusy || settingsTransferBusy || recoveryInProgress
                || importInProgress || mediaBridgeClient == null
                || !mediaBridgeClient.isSettingsSupported()) return;
        radioCatalogBusy = true;
        setMediaControlsEnabled(mediaSettingsGroup, false);
        setRadioCatalogTransferEnabled(false);
        setSettingsTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            File exportFile = null;
            try {
                File exportDir = new File(getCacheDir(), "exports");
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    throw new IOException("Не удалось создать временное хранилище экспорта");
                }
                exportFile = new File(exportDir,
                        "radio_export_" + System.currentTimeMillis() + ".zip");
                if (exportFile.exists() && !exportFile.delete()) {
                    throw new IOException("Не удалось подготовить временный файл экспорта");
                }

                final CountDownLatch latch = new CountDownLatch(1);
                final boolean[] success = new boolean[1];
                final String[] errorHolder = new String[1];
                mediaBridgeClient.exportRadioCatalog(exportFile,
                        new MediaBridgeClient.RadioCatalogExportCallback() {
                            @Override public void onCatalogExported(int stationCount) {
                                success[0] = true;
                                latch.countDown();
                            }

                            @Override public void onError(int code, String message) {
                                errorHolder[0] = message;
                                latch.countDown();
                            }
                        });
                latch.await(17, TimeUnit.SECONDS);
                if (!success[0]) {
                    throw new IOException("Ошибка экспорта каталога радио: "
                            + (errorHolder[0] != null ? errorHolder[0] : "таймаут"));
                }
                SettingsExportStore.Result result = SettingsExportStore.exportZip(
                        appContext, exportFile, SettingsExportStore.RADIO_ZIP_FILE_NAME);
                main.post(() -> {
                    if (isDestroyed()) return;
                    Toast.makeText(this, "Каталог радио сохранён: " + result.location,
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                AppLog.warn("Cannot export radio catalog", error);
                showRadioCatalogTransferError("Не удалось экспортировать каталог радио", error);
            } finally {
                if (exportFile != null) exportFile.delete();
                main.post(() -> {
                    radioCatalogBusy = false;
                    if (!isDestroyed()) {
                        if (currentMediaSettings != null && mediaBridgeClient.isSettingsSupported()) {
                            updateMediaSettingsUi(currentMediaSettings);
                        } else {
                            setMediaControlsEnabled(mediaSettingsGroup, false);
                        }
                        setRadioCatalogTransferEnabled(true);
                        setSettingsTransferEnabled(true);
                    }
                });
            }
        });
    }

    private void readRadioCatalogForImport(Uri uri) {
        // File-picker results arrive before the bridge reconnects after onStop().
        if (radioCatalogBusy || settingsTransferBusy || recoveryInProgress
                || importInProgress) return;
        radioCatalogBusy = true;
        setMediaControlsEnabled(mediaSettingsGroup, false);
        setRadioCatalogTransferEnabled(false);
        setSettingsTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            File staged = null;
            try {
                File importDir = new File(getCacheDir(), "imports");
                if (!importDir.exists() && !importDir.mkdirs()) {
                    throw new IOException("Не удалось создать временное хранилище импорта");
                }
                staged = new File(importDir,
                        "radio_import_" + System.currentTimeMillis() + ".zip");
                try (java.io.InputStream in = appContext.getContentResolver().openInputStream(uri);
                     java.io.OutputStream out = new java.io.FileOutputStream(staged)) {
                    if (in == null) throw new IOException("Не удалось открыть выбранный файл");
                    byte[] buffer = new byte[8192];
                    long totalBytes = 0L;
                    int length;
                    while ((length = in.read(buffer)) != -1) {
                        totalBytes += length;
                        if (totalBytes > FullSettingsBackup.MAX_ZIP_BYTES) {
                            throw new IOException("Архив каталога радио слишком большой");
                        }
                        out.write(buffer, 0, length);
                    }
                }
                File readyFile = staged;
                main.post(() -> {
                    if (isDestroyed()) {
                        readyFile.delete();
                        radioCatalogBusy = false;
                        return;
                    }
                    pendingRadioImportFile = readyFile;
                    showRadioCatalogImportConfirmation(readyFile);
                });
            } catch (Exception error) {
                if (staged != null) staged.delete();
                AppLog.warn("Cannot stage radio catalog import", error);
                showRadioCatalogTransferError("Не удалось прочитать каталог радио", error);
            }
        });
    }

    private void showRadioCatalogImportConfirmation(File stagedFile) {
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Заменить каталог радио?")
                .setMessage("Текущий каталог радиостанций и обложек будет полностью заменён "
                        + "содержимым выбранного ZIP-файла.")
                .setOnCancelListener(d -> finishRadioCatalogImport(stagedFile))
                .setNegativeButton("Отмена", (d, w) -> finishRadioCatalogImport(stagedFile))
                .setPositiveButton("Заменить", (d, w) -> importRadioCatalog(stagedFile)));
    }

    private void importRadioCatalog(File stagedFile) {
        ioExecutor.execute(() -> {
            try {
                MediaBridgeClient bridge = mediaBridgeClient;
                if (bridge == null) {
                    throw new IOException("Медиасервис недоступен. Импорт не выполнен.");
                }
                MediaBridgeClient.SettingsReadiness readiness =
                        bridge.awaitSettingsSupported(SETTINGS_READINESS_TIMEOUT_MS);
                if (!readiness.supported) {
                    throw new IOException("Нельзя импортировать каталог радио: " + readiness.message);
                }
                final CountDownLatch latch = new CountDownLatch(1);
                final boolean[] success = new boolean[1];
                final int[] stationCount = new int[1];
                final String[] errorHolder = new String[1];
                bridge.importRadioCatalog(stagedFile,
                        new MediaBridgeClient.RadioCatalogImportCallback() {
                            @Override public void onCatalogImported(int count) {
                                stationCount[0] = count;
                                success[0] = true;
                                latch.countDown();
                            }

                            @Override public void onError(int code, String message) {
                                errorHolder[0] = message;
                                latch.countDown();
                            }
                        });
                latch.await(17, TimeUnit.SECONDS);
                if (!success[0]) {
                    throw new IOException("Ошибка импорта каталога радио: "
                            + (errorHolder[0] != null ? errorHolder[0] : "таймаут"));
                }
                main.post(() -> {
                    if (isDestroyed()) return;
                    Toast.makeText(this, "Каталог радио импортирован ("
                                    + stationCount[0] + " станций)", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                AppLog.warn("Cannot import radio catalog", error);
                showRadioCatalogTransferError("Не удалось импортировать каталог радио", error);
            } finally {
                stagedFile.delete();
                main.post(() -> {
                    pendingRadioImportFile = null;
                    radioCatalogBusy = false;
                    if (!isDestroyed()) {
                        if (currentMediaSettings != null && mediaBridgeClient.isSettingsSupported()) {
                            updateMediaSettingsUi(currentMediaSettings);
                        } else {
                            setMediaControlsEnabled(mediaSettingsGroup, false);
                        }
                        setRadioCatalogTransferEnabled(true);
                        setSettingsTransferEnabled(true);
                        loadMediaSettings();
                    }
                });
            }
        });
    }

    private void finishRadioCatalogImport(File stagedFile) {
        stagedFile.delete();
        pendingRadioImportFile = null;
        radioCatalogBusy = false;
        if (currentMediaSettings != null && mediaBridgeClient.isSettingsSupported()) {
            updateMediaSettingsUi(currentMediaSettings);
        } else {
            setMediaControlsEnabled(mediaSettingsGroup, false);
        }
        setRadioCatalogTransferEnabled(true);
        setSettingsTransferEnabled(true);
    }

    private void readSettingsForImport(Uri uri) {
        settingsTransferBusy = true;
        setSettingsTransferEnabled(false);
        setRadioCatalogTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            try {
                FullSettingsBackup.Preview preview = FullSettingsBackup.inspect(appContext, uri);
                main.post(() -> {
                    if (isDestroyed()) {
                        FullSettingsBackup.deleteRecursively(preview.stagedDir);
                        settingsTransferBusy = false;
                        return;
                    }
                    showImportPreviewDialog(preview);
                });
            } catch (Exception error) {
                AppLog.warn("Cannot read settings backup", error);
                showSettingsTransferError("Не удалось прочитать файл резервной копии", error);
            }
        });
    }

    private void showImportPreviewDialog(FullSettingsBackup.Preview preview) {
        StringBuilder sb = new StringBuilder();
        sb.append("Содержимое резервной копии:\n");
        if (preview.hasWidget) {
            sb.append("• Настройки карточки виджета\n");
        }
        if (preview.hasMedia) {
            sb.append("• Настройки медиа и источника звука\n");
        }
        if (preview.hasRadio) {
            sb.append("• Каталог радио в архиве будет проигнорирован (текущий каталог сохранится)\n");
        }
        if (!preview.warnings.isEmpty()) {
            sb.append("\nВнимание:\n");
            for (String w : preview.warnings) {
                sb.append("• ").append(w).append("\n");
            }
        }
        sb.append("\nТекущие переносимые настройки будут заменены.");

        CompactDialog.show(new AlertDialog.Builder(this)
                .setOnCancelListener(d -> {
                    settingsTransferBusy = false;
                    FullSettingsBackup.deleteRecursively(preview.stagedDir);
                    setSettingsTransferEnabled(true);
                    setRadioCatalogTransferEnabled(true);
                })
                .setTitle("Импортировать настройки?")
                .setMessage(sb.toString().trim())
                .setNegativeButton("Отмена", (d, w) -> {
                    settingsTransferBusy = false;
                    FullSettingsBackup.deleteRecursively(preview.stagedDir);
                    setSettingsTransferEnabled(true);
                    setRadioCatalogTransferEnabled(true);
                })
                .setPositiveButton("Импортировать", (d, w) -> {
                    settingsTransferBusy = false;
                    setRadioCatalogTransferEnabled(false);
                    applyFullImport(preview);
                }));
    }

    private void applyFullImport(FullSettingsBackup.Preview preview) {
        importInProgress = true;
        setSettingsTransferEnabled(false);
        setRadioCatalogTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            boolean journalStarted = false;
            boolean commitRequested = false;
            String operationId = null;
            try {
                MediaBridgeClient bridge = mediaBridgeClient;
                if (preview.hasMedia) {
                    if (bridge == null) {
                        throw new IOException("Медиасервис недоступен. Импорт не выполнен.");
                    }
                    MediaBridgeClient.SettingsReadiness readiness =
                            bridge.awaitSettingsSupported(SETTINGS_READINESS_TIMEOUT_MS);
                    if (!readiness.supported) {
                        throw new IOException("Нельзя импортировать настройки медиа: "
                                + readiness.message);
                    }
                }
                operationId = ImportJournal.startImport(appContext, prefs, preview.hasWidget, preview.hasMedia);
                journalStarted = true;

                String stagingToken = null;
                final String mediaOperationId = operationId;
                if (preview.hasMedia) {
                    final CountDownLatch prepLatch = new CountDownLatch(1);
                    final String[] tokenHolder = new String[1];
                    final String[] errHolder = new String[1];
                    bridge.prepareMediaImport(mediaOperationId, preview.stagedFile, new MediaBridgeClient.PrepareImportCallback() {
                        @Override public void onImportPrepared(String token, String catalogMode, int stationCount, java.util.List<String> warnings) {
                            tokenHolder[0] = token;
                            prepLatch.countDown();
                        }
                        @Override public void onError(int code, String message) {
                            errHolder[0] = message;
                            prepLatch.countDown();
                        }
                    });
                    prepLatch.await(20, TimeUnit.SECONDS);
                    stagingToken = tokenHolder[0];
                    if (stagingToken == null) {
                        throw new IOException("Ошибка подготовки импорта медиа: "
                                + (errHolder[0] != null ? errHolder[0] : "таймаут"));
                    }
                }

                if (preview.hasWidget && preview.widgetData != null) {
                    boolean saved = prefs.replacePortableSettings(preview.widgetData);
                    if (!saved) {
                        throw new IOException("Не удалось сохранить импортированные настройки виджета");
                    }
                }

                if (stagingToken != null) {
                    final CountDownLatch commitLatch = new CountDownLatch(1);
                    final boolean[] commitOk = new boolean[1];
                    final String[] commitErr = new String[1];
                    final String finalStagingToken = stagingToken;
                    ImportJournal.markMediaCommitRequested(appContext);
                    commitRequested = true;
                    bridge.commitMediaImport(mediaOperationId, finalStagingToken, new MediaBridgeClient.CommitImportCallback() {
                        @Override public void onImportCommitted(MediaSettingsSnapshot snapshot) {
                            commitOk[0] = true;
                            commitLatch.countDown();
                        }
                        @Override public void onError(int code, String message) {
                            commitErr[0] = message;
                            commitLatch.countDown();
                        }
                    });
                    commitLatch.await(15, TimeUnit.SECONDS);
                    if (!commitOk[0]) {
                        throw new IOException("Результат импорта медиа не подтверждён. Состояние будет проверено при подключении: "
                                + (commitErr[0] != null ? commitErr[0] : "таймаут"));
                    }
                }

                ImportJournal.markCommitted(appContext);
                FullSettingsBackup.deleteRecursively(preview.stagedDir);

                main.post(() -> {
                    if (isDestroyed()) return;
                    setSettingsTransferEnabled(true);
                    refreshOverlayIfRunning();
                    Toast.makeText(this, "Настройки успешно импортированы", Toast.LENGTH_LONG).show();
                    recreate();
                });
            } catch (Exception error) {
                if (journalStarted && !commitRequested) {
                    if (preview.hasMedia) mediaBridgeClient.abortMediaImport(operationId);
                    ImportJournal.rollback(appContext, prefs);
                }
                FullSettingsBackup.deleteRecursively(preview.stagedDir);
                AppLog.warn("Import failed", error);
                showSettingsTransferError("Не удалось импортировать настройки", error);
            } finally {
                main.post(() -> {
                    if (isDestroyed()) return;
                    importInProgress = false;
                    checkPendingImportRecovery();
                    if (!recoveryInProgress && ImportJournal.checkPendingRecovery(this) == null) {
                        setSettingsTransferEnabled(true);
                        setRadioCatalogTransferEnabled(true);
                        loadMediaSettings();
                    }
                });
            }
        });
    }

    private void checkPendingImportRecovery() {
        if (importInProgress || recoveryInProgress || isDestroyed()) return;
        ImportJournal.RecoveryInfo recovery = ImportJournal.checkPendingRecovery(this);
        if (recovery == null) return;
        if (recovery.hasMedia && recovery.mediaCommitRequested) {
            if (!mediaBridgeClient.isSettingsSupported()) return;
            recoveryInProgress = true;
            setSettingsTransferEnabled(false);
            setRadioCatalogTransferEnabled(false);
            mediaBridgeClient.getImportStatus(recovery.id, new MediaBridgeClient.ImportStatusCallback() {
                @Override public void onStatus(String status) {
                    recoveryInProgress = false;
                    if (isDestroyed()) return;
                    if ("COMMITTED".equals(status)) {
                        ImportJournal.markCommitted(MainActivity.this);
                        refreshOverlayIfRunning();
                        Toast.makeText(MainActivity.this, "Импорт настроек подтверждён", Toast.LENGTH_LONG).show();
                        recreate();
                    } else if ("PREPARED".equals(status) || "IDLE".equals(status)) {
                        mediaBridgeClient.abortMediaImport(recovery.id);
                        offerWidgetImportRollback(recovery);
                    } else {
                        setSettingsTransferEnabled(false);
                        recoveryInProgress = true;
                        CompactDialog.show(new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Импорт не завершён")
                                .setMessage("Медиасервис сообщает: " + status
                                        + ". Журнал сохранён. После устранения ошибки можно повторить проверку и завершение импорта.")
                                .setOnCancelListener(d -> {
                                    recoveryInProgress = false;
                                    setSettingsTransferEnabled(true);
                                    setRadioCatalogTransferEnabled(true);
                                })
                                .setNegativeButton("Позже", (d, w) -> {
                                    recoveryInProgress = false;
                                    setSettingsTransferEnabled(true);
                                    setRadioCatalogTransferEnabled(true);
                                })
                                .setPositiveButton("Повторить", (d, w) -> {
                                    recoveryInProgress = false;
                                    checkPendingImportRecovery();
                                }));
                    }
                }
                @Override public void onError(int code, String message) {
                    recoveryInProgress = false;
                    if (isDestroyed()) return;
                    setSettingsTransferEnabled(true);
                    setRadioCatalogTransferEnabled(true);
                    Toast.makeText(MainActivity.this,
                            "Не удалось проверить результат импорта: " + message,
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            if (recovery.hasMedia && mediaBridgeClient.isSettingsSupported()) {
                mediaBridgeClient.abortMediaImport(recovery.id);
            }
            offerWidgetImportRollback(recovery);
        }
    }

    private void offerWidgetImportRollback(ImportJournal.RecoveryInfo recovery) {
        if (!recovery.hasWidget) {
            ImportJournal.markCommitted(this);
            setSettingsTransferEnabled(true);
            setRadioCatalogTransferEnabled(true);
            return;
        }
        recoveryInProgress = true;
        setSettingsTransferEnabled(false);
        setRadioCatalogTransferEnabled(false);
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Прерванный импорт настроек")
                .setMessage("Импорт не завершён. Восстановить настройки карточки до импорта?")
                .setOnCancelListener(d -> {
                    recoveryInProgress = false;
                    setSettingsTransferEnabled(true);
                    setRadioCatalogTransferEnabled(true);
                })
                .setNegativeButton("Позже", (d, w) -> {
                    recoveryInProgress = false;
                    setSettingsTransferEnabled(true);
                    setRadioCatalogTransferEnabled(true);
                })
                .setPositiveButton("Восстановить", (d, w) -> {
                    recoveryInProgress = false;
                    if (ImportJournal.rollback(this, prefs)) {
                        refreshOverlayIfRunning();
                        recreate();
                    } else {
                        Toast.makeText(this, "Не удалось восстановить настройки. Журнал сохранён.",
                                Toast.LENGTH_LONG).show();
                    }
                }));
    }

    private LinearLayout createMediaCard() {
        LinearLayout mediaCard = card();
        mediaCard.addView(text("Медиасервис OneOS", 20, Ui.PRIMARY, Typeface.BOLD));

        mediaStatusText = text("Подключение к медиасервису…", 13, Ui.SECONDARY, Typeface.NORMAL);
        mediaStatusText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(this, 8);
        mediaCard.addView(mediaStatusText, statusParams);

        mediaSettingsGroup = new LinearLayout(this);
        mediaSettingsGroup.setOrientation(LinearLayout.VERTICAL);
        mediaCard.addView(mediaSettingsGroup, fullWrap());

        defaultSourceTitle = text("Источник звука по умолчанию", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams dstParams = fullWrap();
        dstParams.topMargin = Ui.dp(this, 14);
        mediaSettingsGroup.addView(defaultSourceTitle, dstParams);

        LinearLayout row1 = createSourceTileRow(new String[][]{
                {"Отключено", ""},
                {"Radio", "RADIO"},
                {"Bluetooth", "BT"}
        });
        mediaSettingsGroup.addView(row1);
        LinearLayout.LayoutParams r1Params = fullWrap();
        r1Params.topMargin = Ui.dp(this, 8);
        row1.setLayoutParams(r1Params);

        LinearLayout row2 = createSourceTileRow(new String[][]{
                {"USB", "USB"},
                {"Online", "ONLINE"},
                {"CarPlay", "CPAA"}
        });
        mediaSettingsGroup.addView(row2);
        LinearLayout.LayoutParams r2Params = fullWrap();
        r2Params.topMargin = Ui.dp(this, 6);
        row2.setLayoutParams(r2Params);

        onlinePlayerTitle = text("Онлайн медиаплеер по умолчанию", 15,
                Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams onlineTitleParams = fullWrap();
        onlineTitleParams.topMargin = Ui.dp(this, 14);
        mediaSettingsGroup.addView(onlinePlayerTitle, onlineTitleParams);

        onlinePlayerSpinner = new Spinner(this);
        onlinePlayerSpinner.setBackground(Ui.background(Ui.NESTED, 8, this));
        onlinePlayerSpinner.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        onlinePlayerAdapter = new ArrayAdapter<OnlinePlayerOption>(
                this, android.R.layout.simple_spinner_item, queryOnlinePlayerOptions()) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return createOnlinePlayerRow(getItem(position), false);
            }

            @Override public View getDropDownView(int position, View convertView,
                    ViewGroup parent) {
                return createOnlinePlayerRow(getItem(position), true);
            }
        };
        onlinePlayerSpinner.setAdapter(onlinePlayerAdapter);
        onlinePlayerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                if (refreshingOnlinePlayer || currentMediaSettings == null
                        || position < 0 || position >= onlinePlayerAdapter.getCount()) return;
                String packageName = onlinePlayerAdapter.getItem(position).packageName;
                if (!packageName.equals(currentMediaSettings.defaultMediaPackage)) {
                    sendMediaSettingChange(
                            MediaBridgeContract.K_DEFAULT_MEDIA_PACKAGE, packageName);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams onlineSpinnerParams = fullWrap();
        onlineSpinnerParams.topMargin = Ui.dp(this, 4);
        mediaSettingsGroup.addView(onlinePlayerSpinner, onlineSpinnerParams);

        minimizeOnlinePlayerSwitch = new Switch(this);
        minimizeOnlinePlayerSwitch.setText("Сворачивать онлайн-плеер после автозапуска");
        minimizeOnlinePlayerSwitch.setTextColor(Ui.PRIMARY);
        minimizeOnlinePlayerSwitch.setTextSize(15);
        minimizeOnlinePlayerSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(
                    MediaBridgeContract.K_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART,
                    checked);
        });
        LinearLayout.LayoutParams minimizeParams = fullWrap();
        minimizeParams.topMargin = Ui.dp(this, 10);
        mediaSettingsGroup.addView(minimizeOnlinePlayerSwitch, minimizeParams);

        TextView delayTitle = text("Задержка переключения на старте", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams dtParams = fullWrap();
        dtParams.topMargin = Ui.dp(this, 14);
        mediaSettingsGroup.addView(delayTitle, dtParams);

        defaultSourceDelayLabel = text("0 сек", 18, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams dslParams = fullWrap();
        dslParams.topMargin = Ui.dp(this, 4);
        mediaSettingsGroup.addView(defaultSourceDelayLabel, dslParams);

        defaultSourceDelaySeekBar = sizeSeekBar(0, 30);
        defaultSourceDelaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    defaultSourceDelayLabel.setText(progress + " сек");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                commitDelaySetting(bar.getProgress());
            }
        });
        mediaSettingsGroup.addView(defaultSourceDelaySeekBar, fullWrap());

        startupAutoplaySwitch = new Switch(this);
        startupAutoplaySwitch.setText("Автовоспроизведение при старте");
        startupAutoplaySwitch.setTextColor(Ui.PRIMARY);
        startupAutoplaySwitch.setTextSize(15);
        startupAutoplaySwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_AUTOPLAY, checked);
        });
        LinearLayout.LayoutParams s1 = fullWrap();
        s1.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(startupAutoplaySwitch, s1);

        sourceLostSwitch = new Switch(this);
        sourceLostSwitch.setText("Автопереключение при потере источника");
        sourceLostSwitch.setTextColor(Ui.PRIMARY);
        sourceLostSwitch.setTextSize(15);
        sourceLostSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT, checked);
        });
        LinearLayout.LayoutParams s2 = fullWrap();
        s2.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(sourceLostSwitch, s2);

        sourceLostAutoplaySwitch = new Switch(this);
        sourceLostAutoplaySwitch.setText("Автовоспроизведение при потере источника");
        sourceLostAutoplaySwitch.setTextColor(Ui.PRIMARY);
        sourceLostAutoplaySwitch.setTextSize(15);
        sourceLostAutoplaySwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY, checked);
        });
        LinearLayout.LayoutParams s3 = fullWrap();
        s3.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(sourceLostAutoplaySwitch, s3);

        switchToOnlineSwitch = new Switch(this);
        switchToOnlineSwitch.setText("Переключать на Online перед воспроизведением сессии");
        switchToOnlineSwitch.setTextColor(Ui.PRIMARY);
        switchToOnlineSwitch.setTextSize(15);
        switchToOnlineSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_SWITCH_TO_ONLINE_BEFORE_SESSION_PLAY, checked);
        });
        LinearLayout.LayoutParams s4 = fullWrap();
        s4.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(switchToOnlineSwitch, s4);

        TextView radioClusterTitle = text("Радио и приборная панель", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams rctParams = fullWrap();
        rctParams.topMargin = Ui.dp(this, 16);
        mediaSettingsGroup.addView(radioClusterTitle, rctParams);

        radioWidgetBroadcastSwitch = new Switch(this);
        radioWidgetBroadcastSwitch.setText("Трансляция радио в виджет (название и обложка)");
        radioWidgetBroadcastSwitch.setTextColor(Ui.PRIMARY);
        radioWidgetBroadcastSwitch.setTextSize(15);
        radioWidgetBroadcastSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_RADIO_WIDGET_BROADCAST_ENABLED, checked);
        });
        LinearLayout.LayoutParams s5 = fullWrap();
        s5.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(radioWidgetBroadcastSwitch, s5);

        clusterCoversSwitch = new Switch(this);
        clusterCoversSwitch.setText("Трансляция радио на приборку (название и обложка)");
        clusterCoversSwitch.setTextColor(Ui.PRIMARY);
        clusterCoversSwitch.setTextSize(15);
        clusterCoversSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_CLUSTER_COVERS_ENABLED, checked);
        });
        LinearLayout.LayoutParams s6 = fullWrap();
        s6.topMargin = Ui.dp(this, 10);
        mediaSettingsGroup.addView(clusterCoversSwitch, s6);

        clusterOnlineSwitch = new Switch(this);
        clusterOnlineSwitch.setText("Трансляция онлайн-плеера на приборку (название и обложка)");
        clusterOnlineSwitch.setTextColor(Ui.PRIMARY);
        clusterOnlineSwitch.setTextSize(15);
        clusterOnlineSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_CLUSTER_ONLINE_ENABLED, checked);
        });
        LinearLayout.LayoutParams s7 = fullWrap();
        s7.topMargin = Ui.dp(this, 10);
        mediaSettingsGroup.addView(clusterOnlineSwitch, s7);

        clusterOnlineProgressSwitch = new Switch(this);
        clusterOnlineProgressSwitch.setText("Трансляция прогресса онлайн на приборку (каждую 1 с)");
        clusterOnlineProgressSwitch.setTextColor(Ui.PRIMARY);
        clusterOnlineProgressSwitch.setTextSize(15);
        clusterOnlineProgressSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(MediaBridgeContract.K_CLUSTER_ONLINE_PROGRESS_ENABLED, checked);
        });
        LinearLayout.LayoutParams s8 = fullWrap();
        s8.topMargin = Ui.dp(this, 10);
        mediaSettingsGroup.addView(clusterOnlineProgressSwitch, s8);

        TextView clusterWatchdogTitle = text("Период watchdog радио на приборке", 14, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams cwtParams = fullWrap();
        cwtParams.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(clusterWatchdogTitle, cwtParams);

        clusterWatchdogLabel = text("1250 мс", 16, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams cwlParams = fullWrap();
        cwlParams.topMargin = Ui.dp(this, 4);
        mediaSettingsGroup.addView(clusterWatchdogLabel, cwlParams);

        clusterWatchdogSeekBar = sizeSeekBar(100, 500); // 1000..5000 ms
        clusterWatchdogSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long ms = progress * 10L;
                    clusterWatchdogLabel.setText(ms + " мс");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                commitWatchdog(bar.getProgress() * 10L);
            }
        });
        mediaSettingsGroup.addView(clusterWatchdogSeekBar, fullWrap());

        TextView clusterReassertBurstTitle = text("Базовый интервал быстрых повторов", 14, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams crbtParams = fullWrap();
        crbtParams.topMargin = Ui.dp(this, 12);
        mediaSettingsGroup.addView(clusterReassertBurstTitle, crbtParams);

        clusterReassertBurstLabel = text("100 мс", 16, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams crblParams = fullWrap();
        crblParams.topMargin = Ui.dp(this, 4);
        mediaSettingsGroup.addView(clusterReassertBurstLabel, crblParams);

        clusterReassertBurstSeekBar = sizeSeekBar(5, 50); // 50..500 ms
        clusterReassertBurstSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long ms = progress * 10L;
                    clusterReassertBurstLabel.setText(ms + " мс");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                commitReassertBurstInterval(bar.getProgress() * 10L);
            }
        });
        mediaSettingsGroup.addView(clusterReassertBurstSeekBar, fullWrap());

        TextView radioCatalogSectionTitle = text("Каталог радио", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams rcsParams = fullWrap();
        rcsParams.topMargin = Ui.dp(this, 16);
        mediaSettingsGroup.addView(radioCatalogSectionTitle, rcsParams);

        radioCatalogInfoText = text("Каталог: ...", 13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams rciParams = fullWrap();
        rciParams.topMargin = Ui.dp(this, 4);
        mediaSettingsGroup.addView(radioCatalogInfoText, rciParams);

        restoreDefaultRadioCatalogButton = actionButton("Восстановить стандартный каталог радио");
        restoreDefaultRadioCatalogButton.setOnClickListener(v -> restoreDefaultRadioCatalog());
        mediaSettingsGroup.addView(restoreDefaultRadioCatalogButton, buttonParams());
        setMediaControlsEnabled(mediaSettingsGroup, false);
        return mediaCard;
    }

    private LinearLayout createDiagnosticCard() {
        LinearLayout diagnosticCard = card();
        diagnosticCard.addView(text("Диагностика OneOS", 20, Ui.PRIMARY, Typeface.BOLD));
        TextView diagNote = text(
                "Проверка статуса сервисов OneOS, состояния шины CAN/приборки, "
                        + "регистрация callbacks сосуществования и экспорт диагностического отчёта.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        diagNote.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams diagNoteParams = fullWrap();
        diagNoteParams.topMargin = Ui.dp(this, 8);
        diagnosticCard.addView(diagNote, diagNoteParams);
        Button openDiagButton = actionButton("Открыть диагностику OneOS");
        openDiagButton.setOnClickListener(v -> openDiagnostics());
        diagnosticCard.addView(openDiagButton, buttonParams());
        return diagnosticCard;
    }

    private LinearLayout createRadioCatalogTransferCard() {
        LinearLayout radioCard = card();
        radioCard.addView(text("Каталог радио", 20, Ui.PRIMARY, Typeface.BOLD));
        TextView hint = text(
                "Отдельный ZIP-каталог содержит stations.csv и обложки радиостанций. "
                        + "Импорт заменяет текущий каталог после подтверждения.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams hintParams = fullWrap();
        hintParams.topMargin = Ui.dp(this, 8);
        radioCard.addView(hint, hintParams);

        exportRadioCatalogButton = actionButton("Экспортировать каталог радио (ZIP)");
        exportRadioCatalogButton.setOnClickListener(v -> exportRadioCatalog());
        radioCard.addView(exportRadioCatalogButton, buttonParams());

        importRadioCatalogButton = actionButton("Импортировать каталог радио (ZIP)");
        importRadioCatalogButton.setOnClickListener(v -> chooseRadioCatalogImport());
        radioCard.addView(importRadioCatalogButton, buttonParams());
        setRadioCatalogTransferEnabled(false);
        return radioCard;
    }

    private LinearLayout createSourceTileRow(String[][] options) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < options.length; i++) {
            final String label = options[i][0];
            final String id = options[i][1];
            Button btn = actionButton(label);
            btn.setTextSize(13);
            btn.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
            btn.setBackground(Ui.background(Ui.NESTED, 8, this));
            btn.setTextColor(Ui.SECONDARY);
            btn.setOnClickListener(v -> {
                sendMediaSettingChange(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE, id);
            });
            sourceTileButtons.put(id, btn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            row.addView(btn, lp);
        }
        return row;
    }

    private void loadMediaSettings() {
        if (mediaBridgeClient == null || !mediaBridgeClient.isSettingsSupported()
                || mediaSettingsBusy || settingsTransferBusy || radioCatalogBusy
                || recoveryInProgress || importInProgress) return;
        mediaSettingsBusy = true;
        setMediaControlsEnabled(mediaSettingsGroup, false);
        setSettingsTransferEnabled(false);
        mediaBridgeClient.getSettings(new MediaBridgeClient.SettingsCallback() {
            @Override public void onSettings(MediaSettingsSnapshot snapshot) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                currentMediaSettings = snapshot;
                updateMediaSettingsUi(snapshot);
                setSettingsTransferEnabled(true);
            }
            @Override public void onError(int code, String message) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                mediaStatusText.setText("Не удалось загрузить медианастройки: " + message);
                mediaStatusText.setTextColor(Ui.ERROR);
                setMediaControlsEnabled(mediaSettingsGroup, false);
                setSettingsTransferEnabled(true);
                AppLog.warn("Failed to load media settings: " + code + " " + message, null);
            }
        });
    }

    private void updateMediaSettingsUi(MediaSettingsSnapshot snapshot) {
        setMediaControlsEnabled(mediaSettingsGroup, true);
        setRadioCatalogTransferEnabled(true);
        for (Map.Entry<String, Button> entry : sourceTileButtons.entrySet()) {
            boolean isSelected = entry.getKey().equals(snapshot.defaultAudioSource);
            Button btn = entry.getValue();
            btn.setBackground(Ui.background(isSelected ? Ui.ACCENT : Ui.NESTED, 8, this));
            btn.setTextColor(isSelected ? 0xFFFFFFFF : Ui.SECONDARY);
        }

        boolean hasSource = snapshot.defaultAudioSource != null
                && !snapshot.defaultAudioSource.isEmpty();
        updateOnlinePlayerUi(snapshot);
        if (defaultSourceDelayLabel != null) {
            defaultSourceDelayLabel.setText(snapshot.defaultAudioSourceDelaySec + " сек");
            defaultSourceDelayLabel.setEnabled(hasSource);
            defaultSourceDelayLabel.setAlpha(hasSource ? 1f : 0.45f);
        }
        if (defaultSourceDelaySeekBar != null) {
            defaultSourceDelaySeekBar.setProgress(snapshot.defaultAudioSourceDelaySec);
            defaultSourceDelaySeekBar.setEnabled(hasSource);
            defaultSourceDelaySeekBar.setAlpha(hasSource ? 1f : 0.45f);
        }
        if (startupAutoplaySwitch != null) {
            startupAutoplaySwitch.setChecked(snapshot.defaultAudioSourceAutoplayOnStartup);
            startupAutoplaySwitch.setEnabled(true);
            startupAutoplaySwitch.setAlpha(1f);
        }
        if (sourceLostSwitch != null) {
            sourceLostSwitch.setChecked(snapshot.autoSwitchToDefaultOnSourceLost);
            sourceLostSwitch.setEnabled(hasSource);
            sourceLostSwitch.setAlpha(hasSource ? 1f : 0.45f);
        }
        if (sourceLostAutoplaySwitch != null) {
            boolean canAutoplay = hasSource && snapshot.autoSwitchToDefaultOnSourceLost;
            sourceLostAutoplaySwitch.setChecked(snapshot.autoSwitchToDefaultAutoplayOnSourceLost);
            sourceLostAutoplaySwitch.setEnabled(canAutoplay);
            sourceLostAutoplaySwitch.setAlpha(canAutoplay ? 1f : 0.45f);
        }
        if (switchToOnlineSwitch != null) {
            switchToOnlineSwitch.setChecked(snapshot.switchToOnlineBeforeSessionPlay);
        }
        if (radioWidgetBroadcastSwitch != null) {
            radioWidgetBroadcastSwitch.setChecked(snapshot.radioWidgetBroadcastEnabled);
        }
        if (clusterCoversSwitch != null) {
            clusterCoversSwitch.setChecked(snapshot.clusterCoversEnabled);
        }
        if (clusterOnlineSwitch != null) {
            clusterOnlineSwitch.setChecked(snapshot.clusterOnlineEnabled);
        }
        if (clusterOnlineProgressSwitch != null) {
            clusterOnlineProgressSwitch.setChecked(snapshot.clusterOnlineProgressEnabled);
            clusterOnlineProgressSwitch.setEnabled(snapshot.clusterOnlineEnabled);
            clusterOnlineProgressSwitch.setAlpha(snapshot.clusterOnlineEnabled ? 1f : 0.45f);
        }
        if (clusterWatchdogLabel != null) {
            clusterWatchdogLabel.setText(snapshot.clusterWatchdogIntervalMs + " мс");
        }
        if (clusterWatchdogLabel != null) {
            clusterWatchdogLabel.setEnabled(snapshot.clusterCoversEnabled);
            clusterWatchdogLabel.setAlpha(snapshot.clusterCoversEnabled ? 1f : 0.45f);
        }
        if (clusterWatchdogSeekBar != null) {
            clusterWatchdogSeekBar.setEnabled(snapshot.clusterCoversEnabled);
            clusterWatchdogSeekBar.setAlpha(snapshot.clusterCoversEnabled ? 1f : 0.45f);
            clusterWatchdogSeekBar.setProgress((int) (snapshot.clusterWatchdogIntervalMs / 10L));
        }
        if (clusterReassertBurstLabel != null) {
            clusterReassertBurstLabel.setText(snapshot.clusterReassertBurstIntervalMs + " мс");
            clusterReassertBurstLabel.setEnabled(snapshot.clusterCoversEnabled);
            clusterReassertBurstLabel.setAlpha(snapshot.clusterCoversEnabled ? 1f : 0.45f);
        }
        if (clusterReassertBurstSeekBar != null) {
            clusterReassertBurstSeekBar.setEnabled(snapshot.clusterCoversEnabled);
            clusterReassertBurstSeekBar.setAlpha(snapshot.clusterCoversEnabled ? 1f : 0.45f);
            clusterReassertBurstSeekBar.setProgress((int) (snapshot.clusterReassertBurstIntervalMs / 10L));
        }
        if (radioCatalogInfoText != null) {
            radioCatalogInfoText.setText("Тип каталога: " + snapshot.catalogType
                    + " (" + snapshot.catalogStationCount + " станций)");
        }
        if (restoreDefaultRadioCatalogButton != null) {
            boolean isCustom = "CUSTOM".equalsIgnoreCase(snapshot.catalogType);
            restoreDefaultRadioCatalogButton.setEnabled(isCustom);
            restoreDefaultRadioCatalogButton.setAlpha(isCustom ? 1f : 0.45f);
        }
    }

    private void updateOnlinePlayerUi(MediaSettingsSnapshot snapshot) {
        if (onlinePlayerTitle == null || onlinePlayerSpinner == null
                || onlinePlayerAdapter == null) return;
        onlinePlayerTitle.setVisibility(View.VISIBLE);
        onlinePlayerSpinner.setVisibility(View.VISIBLE);
        onlinePlayerTitle.setEnabled(true);
        onlinePlayerSpinner.setEnabled(true);
        onlinePlayerTitle.setAlpha(1f);
        onlinePlayerSpinner.setAlpha(1f);

        refreshingOnlinePlayer = true;
        onlinePlayerSpinner.setSelection(
                findOnlinePlayerOption(snapshot.defaultMediaPackage), false);
        refreshingOnlinePlayer = false;

        if (minimizeOnlinePlayerSwitch != null) {
            boolean hasDefaultOnlinePlayer = snapshot.defaultMediaPackage != null
                    && !snapshot.defaultMediaPackage.isEmpty();
            minimizeOnlinePlayerSwitch.setChecked(snapshot.minimizeOnlinePlayerAfterAutostart);
            minimizeOnlinePlayerSwitch.setEnabled(hasDefaultOnlinePlayer);
            minimizeOnlinePlayerSwitch.setAlpha(hasDefaultOnlinePlayer ? 1f : 0.45f);
        }
    }

    private int findOnlinePlayerOption(String packageName) {
        String selectedPackage = packageName != null ? packageName : "";
        for (int i = 0; i < onlinePlayerAdapter.getCount(); i++) {
            if (selectedPackage.equals(onlinePlayerAdapter.getItem(i).packageName)) return i;
        }
        if (!selectedPackage.isEmpty()) {
            onlinePlayerAdapter.add(new OnlinePlayerOption(
                    selectedPackage,
                    "Не установлено: " + selectedPackage,
                    loadApplicationIcon(getPackageManager(), selectedPackage)));
            return onlinePlayerAdapter.getCount() - 1;
        }
        return 0;
    }

    private List<OnlinePlayerOption> queryOnlinePlayerOptions() {
        List<OnlinePlayerOption> options = new ArrayList<>();
        options.add(new OnlinePlayerOption("", "Не запускать приложение"));

        PackageManager packageManager = getPackageManager();
        Set<String> mediaPackages = new HashSet<>();
        Intent musicIntent = new Intent(Intent.ACTION_MAIN);
        musicIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
        addMediaActivityPackages(packageManager, mediaPackages, musicIntent);
        addMediaActivityPackages(packageManager, mediaPackages,
                new Intent(Intent.ACTION_VIEW).setType("audio/*")
                        .addCategory(Intent.CATEGORY_DEFAULT));
        addMediaActivityPackages(packageManager, mediaPackages,
                new Intent(Intent.ACTION_VIEW).setType("video/*")
                        .addCategory(Intent.CATEGORY_DEFAULT));
        addMediaServicePackages(
                packageManager, mediaPackages, MEDIA_BROWSER_SERVICE_ACTION);

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, OnlinePlayerOption> byPackage = new HashMap<>();
        for (ResolveInfo info : packageManager.queryIntentActivities(launcherIntent, 0)) {
            if (info.activityInfo == null) continue;
            String candidatePackage = info.activityInfo.packageName;
            if (getPackageName().equals(candidatePackage)
                    || !mediaPackages.contains(candidatePackage)
                    || packageManager.getLaunchIntentForPackage(candidatePackage) == null) {
                continue;
            }
            CharSequence label = info.loadLabel(packageManager);
            String displayName = label != null && !label.toString().isBlank()
                    ? label.toString() : candidatePackage;
            byPackage.putIfAbsent(candidatePackage, new OnlinePlayerOption(
                    candidatePackage,
                    displayName,
                    loadApplicationIcon(packageManager, candidatePackage)));
        }
        List<OnlinePlayerOption> discovered = new ArrayList<>(byPackage.values());
        discovered.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        options.addAll(discovered);
        return options;
    }

    private View createOnlinePlayerRow(OnlinePlayerOption option, boolean dropdown) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dropdown ? Ui.dp(this, 8) : 0, 0,
                dropdown ? Ui.dp(this, 8) : 0);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (option != null && option.icon != null) {
            icon.setImageDrawable(option.icon);
        } else {
            icon.setVisibility(View.INVISIBLE);
        }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 28), Ui.dp(this, 28));
        iconParams.rightMargin = Ui.dp(this, 10);
        row.addView(icon, iconParams);

        TextView label = text(option != null ? option.label : "", 15,
                Ui.PRIMARY, Typeface.NORMAL);
        label.setSingleLine(true);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private Drawable loadApplicationIcon(PackageManager packageManager, String packageName) {
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private void addMediaActivityPackages(PackageManager packageManager, Set<String> packages,
            Intent intent) {
        for (ResolveInfo info : packageManager.queryIntentActivities(intent, 0)) {
            if (info.activityInfo != null) {
                packages.add(info.activityInfo.packageName);
            }
        }
    }

    private void addMediaServicePackages(PackageManager packageManager, Set<String> packages,
            String action) {
        Intent serviceIntent = new Intent(action);
        for (ResolveInfo info : packageManager.queryIntentServices(serviceIntent, 0)) {
            if (info.serviceInfo != null) {
                packages.add(info.serviceInfo.packageName);
            }
        }
    }

    private static final class OnlinePlayerOption {
        final String packageName;
        final String label;
        final Drawable icon;

        OnlinePlayerOption(String packageName, String label) {
            this(packageName, label, null);
        }

        OnlinePlayerOption(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }

        @Override public String toString() {
            return label;
        }
    }

    private void sendMediaSettingChange(String key, Object value) {
        if (mediaBridgeClient == null || currentMediaSettings == null || mediaSettingsBusy
                || settingsTransferBusy || radioCatalogBusy
                || !mediaBridgeClient.isSettingsSupported()) return;
        mediaSettingsBusy = true;
        setMediaControlsEnabled(mediaSettingsGroup, false);
        setSettingsTransferEnabled(false);
        Bundle b = new Bundle();
        if (value instanceof Boolean) {
            b.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            b.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            b.putLong(key, (Long) value);
        } else if (value instanceof String) {
            b.putString(key, (String) value);
        }
        Long expectedRev = currentMediaSettings != null ? currentMediaSettings.revision : null;
        mediaBridgeClient.updateSettings(expectedRev, b, new MediaBridgeClient.UpdateSettingsCallback() {
            @Override public void onSettingsUpdated(MediaSettingsSnapshot snapshot) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                currentMediaSettings = snapshot;
                updateMediaSettingsUi(snapshot);
                setSettingsTransferEnabled(true);
            }
            @Override public void onError(int code, String message) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                AppLog.warn("Failed to update media setting " + key + ": " + code + " " + message, null);
                Toast.makeText(MainActivity.this, "Ошибка сохранения настройки: " + message, Toast.LENGTH_SHORT).show();
                setSettingsTransferEnabled(true);
                loadMediaSettings();
            }
        });
    }

    private void restoreDefaultRadioCatalog() {
        if (mediaBridgeClient == null || mediaSettingsBusy || !mediaBridgeClient.isSettingsSupported()) return;
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Восстановить стандартный каталог радио?")
                .setMessage("Пользовательские названия и обложки радиостанций будут удалены.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Восстановить", (d, w) -> applyDefaultRadioCatalog()));
    }

    private void applyDefaultRadioCatalog() {
        mediaSettingsBusy = true;
        setMediaControlsEnabled(mediaSettingsGroup, false);
        setSettingsTransferEnabled(false);
        mediaBridgeClient.restoreDefaultCatalog(new MediaBridgeClient.RestoreCatalogCallback() {
            @Override public void onCatalogRestored(MediaSettingsSnapshot snapshot) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                currentMediaSettings = snapshot;
                updateMediaSettingsUi(snapshot);
                setSettingsTransferEnabled(true);
                Toast.makeText(MainActivity.this, "Стандартный каталог радио восстановлен", Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(int code, String message) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                setSettingsTransferEnabled(true);
                loadMediaSettings();
                Toast.makeText(MainActivity.this, "Ошибка восстановления каталога: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void commitDelaySetting(int seconds) {
        sendMediaSettingChange(MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_DELAY_SEC, seconds);
    }

    private void commitWatchdog(long ms) {
        sendMediaSettingChange(MediaBridgeContract.K_CLUSTER_WATCHDOG_INTERVAL_MS, ms);
    }

    private void commitReassertBurstInterval(long ms) {
        sendMediaSettingChange(MediaBridgeContract.K_CLUSTER_REASSERT_BURST_INTERVAL_MS, ms);
    }

    private void setMediaControlsEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                setMediaControlsEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    private void showSettingsTransferError(String fallback, Exception error) {
        String detail = error.getMessage() == null || error.getMessage().isBlank()
                ? fallback : error.getMessage();
        main.post(() -> {
            settingsTransferBusy = false;
            if (isDestroyed()) return;
            setSettingsTransferEnabled(true);
            setRadioCatalogTransferEnabled(true);
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
        });
    }

    private void showRadioCatalogTransferError(String fallback, Exception error) {
        String detail = error.getMessage() == null || error.getMessage().isBlank()
                ? fallback : error.getMessage();
        main.post(() -> {
            radioCatalogBusy = false;
            if (isDestroyed()) return;
            if (currentMediaSettings != null && mediaBridgeClient.isSettingsSupported()) {
                updateMediaSettingsUi(currentMediaSettings);
            } else {
                setMediaControlsEnabled(mediaSettingsGroup, false);
            }
            setRadioCatalogTransferEnabled(true);
            setSettingsTransferEnabled(true);
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
        });
    }

    private void setSettingsTransferEnabled(boolean enabled) {
        enabled = enabled && !recoveryInProgress && !importInProgress
                && !settingsTransferBusy && !radioCatalogBusy && !mediaSettingsBusy
                && ImportJournal.checkPendingRecovery(this) == null;
        if (exportSettingsButton != null) exportSettingsButton.setEnabled(enabled);
        if (importSettingsButton != null) importSettingsButton.setEnabled(enabled);
    }

    private void setRadioCatalogTransferEnabled(boolean enabled) {
        enabled = enabled && !recoveryInProgress && !importInProgress
                && !settingsTransferBusy && !radioCatalogBusy && !mediaSettingsBusy
                && mediaBridgeClient != null && mediaBridgeClient.isSettingsSupported()
                && ImportJournal.checkPendingRecovery(this) == null;
        if (exportRadioCatalogButton != null) exportRadioCatalogButton.setEnabled(enabled);
        if (importRadioCatalogButton != null) importRadioCatalogButton.setEnabled(enabled);
    }

    private void openOverlaySettings() {
        openPermissionSettings(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())),
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                "настройки отображения поверх окон");
    }

    private void openUsageSettingsForApp() {
        openPermissionSettings(
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:" + getPackageName())),
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                "настройки истории использования");
    }

    private void openAccessibilitySettings() {
        openPermissionSettings(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
                "настройки контроля окон");
    }

    private void openNotificationAccessSettings() {
        openPermissionSettings(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
                "настройки доступа к уведомлениям");
    }

    private boolean hasMediaNotificationAccess() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isBlank()) return false;
        for (String value : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (component != null
                    && getPackageName().equals(component.getPackageName())
                    && MEDIA_NOTIFICATION_LISTENER_CLASS.equals(component.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStorageAccess() {
        boolean readGranted = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Environment.isExternalStorageManager() || readGranted
                : readGranted;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (ActivityNotFoundException | SecurityException directError) {
                AppLog.warn("App storage access settings unavailable", directError);
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    return;
                } catch (ActivityNotFoundException | SecurityException generalError) {
                    AppLog.warn("General storage access settings unavailable", generalError);
                }
            }
        }
        try {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSION);
        } catch (SecurityException error) {
            AppLog.warn("Storage permission request unavailable", error);
            openPermissionSettings(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())),
                    new Intent(Settings.ACTION_SETTINGS),
                    "настройки приложения");
        }
    }

    private void openPermissionSettings(Intent direct, Intent fallback, String label) {
        try {
            startActivity(direct);
        } catch (ActivityNotFoundException | SecurityException directError) {
            AppLog.warn("Direct " + label + " unavailable", directError);
            try {
                startActivity(fallback);
            } catch (ActivityNotFoundException | SecurityException fallbackError) {
                AppLog.warn("Fallback " + label + " unavailable", fallbackError);
                Toast.makeText(this, "Не удалось открыть " + label,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openDiagnostics() {
        int scaleTenths = configuredScaleTenths(this);
        Intent settings = new Intent().setClassName(
                getPackageName(),
                "com.mmwtl.atlasmediaapi.diagnostics.DiagnosticActivity");
        settings.putExtra("app_ui_scale_tenths", scaleTenths);
        try {
            startActivity(settings);
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.warn("Integrated media service settings unavailable", error);
            Toast.makeText(this, "Настройки медиасервиса недоступны",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    private LinearLayout card() {
        return Ui.card(this);
    }

    private Button actionButton(String label) {
        return Ui.button(this, label);
    }

    private void updatePermissionButton(Button button, boolean granted,
            String grantedText, String requestText) {
        if (button == null) return;
        button.setText((granted ? "✓ " : "✕ ") + (granted ? grantedText : requestText));
        button.setBackground(Ui.background(granted ? Ui.ACCENT : Ui.NESTED, 8, this));
        button.setTextColor(granted ? Ui.ON_ACCENT : Ui.PRIMARY);
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

    private EditText numberInput() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Ui.PRIMARY);
        input.setTextSize(16);
        input.setSelectAllOnFocus(true);
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12),
                Ui.dp(this, 8));
        input.setBackground(Ui.background(Ui.NESTED, 8, this));
        return input;
    }

    private String[] cornerLabels() {
        String[] labels = new String[OverlayCorner.values().length];
        for (OverlayCorner corner : OverlayCorner.values()) labels[corner.ordinal()] = corner.label;
        return labels;
    }

    private LinearLayout.LayoutParams sizeInputParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f);
    }

    private LinearLayout.LayoutParams sizeSpacerParams() {
        return new LinearLayout.LayoutParams(0, 1, 0.25f);
    }

    private LinearLayout.LayoutParams positionColumnParams(float weight, int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = Ui.dp(this, leftMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams compactUnitParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = Ui.dp(this, 4);
        return params;
    }

    private CardStyle currentStyle() {
        return CardStyle.DEFAULT;
    }

    private void refreshSizeControls(CardStyle style) {
        if (widthSize == null || heightSize == null || metadataProgressGap == null
                || controlPanelHeight == null || controlIconScale == null
                || controlSpread == null || controlBottomInset == null
                || topInsetSetting == null || coverDimPresetButtons == null) return;
        boolean previous = refreshingStyle;
        refreshingStyle = true;
        WidgetAppearance appearance = prefs.appearance(style);
        widthSize.setText(Integer.toString(clamp(prefs.cardWidthPx(),
                Prefs.MIN_CARD_WIDTH_PX, maxWidthPx())));
        heightSize.setText(Integer.toString(clamp(prefs.cardHeightPx(),
                Prefs.MIN_CARD_HEIGHT_PX, maxHeightPx())));
        metadataProgressGap.setProgress(appearance.metadataProgressGapDp);
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
        coverDimPresetButtons[appearance.coverDimPreset.preferenceValue].setChecked(true);
        updateMetadataProgressGapLabel();
        updateControlLabels();
        updateAppearanceLabels();
        refreshingStyle = previous;
        refreshPositionControls();
        renderPreview();
    }

    private int widthInput() {
        return parseInput(widthSize, Prefs.MIN_CARD_WIDTH_PX, maxWidthPx(),
                prefs == null ? 500 : prefs.cardWidthPx());
    }

    private int heightInput() {
        return parseInput(heightSize, Prefs.MIN_CARD_HEIGHT_PX, maxHeightPx(),
                prefs == null ? 500 : prefs.cardHeightPx());
    }

    private int parseInput(EditText input, int min, int max, int fallback) {
        if (input == null) return fallback;
        try {
            long value = Long.parseLong(input.getText().toString().trim());
            if (value < min || value > max) return clamp((int) Math.max(Integer.MIN_VALUE,
                    Math.min(Integer.MAX_VALUE, value)), min, max);
            return (int) value;
        } catch (NumberFormatException error) {
            return clamp(fallback, min, max);
        }
    }

    private Integer validatedInput(EditText input, int min, int max, String message) {
        if (input == null) return null;
        try {
            long value = Long.parseLong(input.getText().toString().trim());
            if (value < min || value > max || value > Integer.MAX_VALUE) {
                input.setError(message);
                return null;
            }
            input.setError(null);
            return (int) value;
        } catch (NumberFormatException error) {
            input.setError(message);
            return null;
        }
    }

    private void refreshFavoriteGridControls() {
        if (favoriteColumns == null || favoriteRows == null) return;
        favoriteColumns.setProgress(prefs.radioFavoritesColumns());
        favoriteRows.setProgress(prefs.radioFavoritesRows());
        updateFavoriteGridLabels();
    }

    private void updateRadioNavigationControls() {
        if (radioSavedNavigation == null || radioFavoritesNavigation == null) return;
        boolean enabled = radioSavedNavigation.isChecked();
        radioFavoritesNavigation.setEnabled(enabled);
        radioFavoritesNavigation.setAlpha(enabled ? 1f : 0.5f);
    }

    private void updateFavoriteGridLabels() {
        if (favoriteColumnsValue == null || favoriteRowsValue == null) return;
        favoriteColumnsValue.setText("Столбцы: " + favoriteColumns.getProgress());
        favoriteRowsValue.setText("Строки: " + favoriteRows.getProgress());
    }

    private void updateMetadataProgressGapLabel() {
        metadataProgressGapValue.setText(metadataProgressGap.getProgress() + " dp");
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

    private void refreshPositionControls() {
        if (positionCornerSpinner == null || positionX == null || positionY == null) return;
        boolean previous = refreshingGeometry;
        refreshingGeometry = true;
        Rect bounds = availableBoundsForGeometry();
        int width = widthInput();
        int height = heightInput();
        OverlayCorner corner = OverlayCorner.fromPreference(
                prefs.getString(Prefs.KEY_POSITION_CORNER, null));
        if (corner != null) {
            OverlayPositionMigration.migrate(prefs, bounds, width, height);
        }
        int storedX = prefs.getInt(Prefs.KEY_POSITION_X, Prefs.POSITION_UNSET);
        int storedY = prefs.getInt(Prefs.KEY_POSITION_Y, Prefs.POSITION_UNSET);
        if (corner == null) {
            corner = OverlayCorner.TOP_START;
            int defaultX = bounds.left + Math.max(0, (bounds.width() - width) / 2);
            int defaultY = bounds.top + Math.max(0, Math.round(bounds.height() * 0.62f));
            int absoluteX = storedX == Prefs.POSITION_UNSET ? defaultX : storedX;
            int absoluteY = storedY == Prefs.POSITION_UNSET ? defaultY : storedY;
            OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(corner,
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    width, height, absoluteX, absoluteY);
            storedX = offsets.x();
            storedY = offsets.y();
        }
        displayedPositionCorner = corner;
        positionCornerSpinner.setSelection(corner.ordinal());
        positionX.setText(Integer.toString(Math.max(0, storedX == Prefs.POSITION_UNSET
                ? 0 : storedX)));
        positionY.setText(Integer.toString(Math.max(0, storedY == Prefs.POSITION_UNSET
                ? 0 : storedY)));
        refreshingGeometry = previous;
    }

    private void reanchorPositionFields(OverlayCorner newCorner) {
        if (newCorner == displayedPositionCorner) return;
        Rect bounds = availableBoundsForGeometry();
        int width = widthInput();
        int height = heightInput();
        OverlayCorner oldCorner = displayedPositionCorner == null
                ? OverlayCorner.TOP_START : displayedPositionCorner;
        int oldX = nonNegativeInput(positionX);
        int oldY = nonNegativeInput(positionY);
        OverlayGeometry.Position absolute = OverlayGeometry.positionFor(oldCorner,
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                width, height, oldX, oldY);
        OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(newCorner,
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                width, height, absolute.x(), absolute.y());
        displayedPositionCorner = newCorner;
        refreshingGeometry = true;
        positionX.setText(Integer.toString(offsets.x()));
        positionY.setText(Integer.toString(offsets.y()));
        refreshingGeometry = false;
        main.removeCallbacks(applyGeometryDelayed);
        geometryApplyPending = false;
        applyGeometry();
    }

    private void applyGeometry() {
        Integer width = validatedInput(widthSize, Prefs.MIN_CARD_WIDTH_PX, maxWidthPx(),
                "Введите число от 320 до " + maxWidthPx() + " px");
        Integer height = validatedInput(heightSize, Prefs.MIN_CARD_HEIGHT_PX, maxHeightPx(),
                "Введите число от 220 до " + maxHeightPx() + " px");
        if (width == null || height == null) return;
        Rect bounds = availableBoundsForGeometry();
        int maxX = Math.max(0, bounds.width() - width);
        int maxY = Math.max(0, bounds.height() - height);
        Integer x = validatedInput(positionX, 0, maxX,
                "Введите число от 0 до " + maxX + " px");
        Integer y = validatedInput(positionY, 0, maxY,
                "Введите число от 0 до " + maxY + " px");
        int selectedPosition = positionCornerSpinner.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= OverlayCorner.values().length
                || x == null || y == null) return;
        OverlayCorner corner = OverlayCorner.values()[selectedPosition];
        prefs.putCardSizePx(width, height);
        prefs.putPosition(corner, x, y);
        displayedPositionCorner = corner;
        renderPreview();
        refreshOverlayIfRunning();
    }

    private int nonNegativeInput(EditText input) {
        try {
            return Math.max(0, Integer.parseInt(input.getText().toString().trim()));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private Rect availableBoundsForGeometry() {
        WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
        Rect full = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect safe = new Rect(full.left + insets.left, full.top + insets.top,
                full.right - insets.right, full.bottom - insets.bottom);
        return safe.width() > 0 && safe.height() > 0 ? safe : new Rect(full);
    }

    private int maxWidthPx() {
        return Math.max(Prefs.MIN_CARD_WIDTH_PX,
                availableBoundsForGeometry().width() - Ui.dp(this, 32));
    }

    private int maxHeightPx() {
        return Math.max(Prefs.MIN_CARD_HEIGHT_PX,
                availableBoundsForGeometry().height() - Ui.dp(this, 32));
    }

    private WidgetAppearance currentAppearance() {
        if (topInsetSetting == null) return prefs.appearance(currentStyle());
        return new WidgetAppearance(
                metadataProgressGap.getProgress(),
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
                progressThicknessSetting.seek.getProgress(),
                selectedCoverDimPreset());
    }

    private CoverDimPreset selectedCoverDimPreset() {
        if (coverDimPresetGroup != null) {
            View selected = coverDimPresetGroup.findViewById(
                    coverDimPresetGroup.getCheckedRadioButtonId());
            if (selected != null && selected.getTag() instanceof CoverDimPreset preset) {
                return preset;
            }
        }
        return prefs.coverDimPreset(currentStyle());
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
        int configuredWidthPx = widthInput();
        int configuredHeightPx = heightInput();
        android.content.Context widgetContext = getApplicationContext();
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
                configuredWidthPx, configuredHeightPx,
                configuredWidthPx, configuredHeightPx, CardStyle.DEFAULT,
                prefs.appearance(CardStyle.DEFAULT),
                prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                prefs.getBoolean(Prefs.KEY_DRAG_HANDLE_VISIBLE, true),
                favoriteColumns == null ? prefs.radioFavoritesColumns()
                        : favoriteColumns.getProgress(),
                favoriteRows == null ? prefs.radioFavoritesRows() : favoriteRows.getProgress(),
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
                | MediaBridgeContract.CAP_SET_SOURCE | MediaBridgeContract.CAP_TUNE_RADIO;
        return new MediaSnapshot(
                MediaBridgeContract.VERSION, 1L, System.currentTimeMillis(), true, 0, "",
                MediaSource.Id.RADIO, "",
                Arrays.asList(
                        new MediaSource(MediaSource.Id.BT, true, true, false, capabilities),
                        new MediaSource(MediaSource.Id.RADIO, true, true, true, capabilities),
                        new MediaSource(MediaSource.Id.USB, true, true, false, capabilities),
                        new MediaSource(MediaSource.Id.ONLINE, true, true, false, capabilities)),
                "preview", "Радио", "preview-station", "Радио Дача",
                "98.8 FM", "", 0L, 0L,
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

    private static final class LabeledSeek {
        final TextView value;
        final SeekBar seek;

        LabeledSeek(TextView value, SeekBar seek) {
            this.value = value;
            this.seek = seek;
        }
    }
}
