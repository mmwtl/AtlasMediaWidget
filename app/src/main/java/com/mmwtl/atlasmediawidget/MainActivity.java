package com.mmwtl.atlasmediawidget;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
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
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    static final String RADIO_ZIP_FILE_NAME = "AtlasMediaWidget-radio.zip";
    private static final int MAX_CATALOG_NAME_LENGTH = 40;
    private static final long SETTINGS_READINESS_TIMEOUT_MS = 20_000L;
    private static final String MEDIA_NOTIFICATION_LISTENER_CLASS =
            "com.mmwtl.atlasmediaapi.media.session.MediaNotificationListenerService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private Prefs prefs;
    private static final String STATE_TAB = "settings_tab";
    private static final String STATE_EDITED_WIDGET = "edited_widget";
    private static final int OPACITY_STEPS = 20;
    /** Stands for the shared look, which is the template of new widgets and the overlay's look. */
    private static final int TEMPLATE = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final int TAB_CARD = 0;
    private static final int TAB_MEDIA = 1;
    private static final int TAB_RADIO = 2;
    private static final int TAB_SYSTEM = 3;
    private static final int[] TAB_LABELS = {R.string.tab_card, R.string.tab_media,
            R.string.tab_radio, R.string.tab_system};
    private boolean builtWidgetMode;
    private int[] widgetIds = new int[0];
    private int editedWidget = TEMPLATE;
    private TextView widgetPreviewTitle;
    private final List<View> overlayOnly = new ArrayList<>();
    private final List<View> widgetOnly = new ArrayList<>();
    private final LinearLayout[] tabPages = new LinearLayout[TAB_LABELS.length];
    private final TextView[] tabButtons = new TextView[TAB_LABELS.length];
    private int selectedTab;
    private ScrollView settingsScroll;
    private TextView widgetPlacementStatus;
    private Button addWidgetButton;
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
    private LinearLayout sourceTilesList;
    private Button sourceTilesAdd;
    private Button sourceTilesReset;
    private Switch playerIconsMono;
    private TextView onlinePlayerTitle;
    private Spinner onlinePlayerSpinner;
    private ArrayAdapter<OnlinePlayerOption> onlinePlayerAdapter;
    private boolean refreshingOnlinePlayer;
    private Switch minimizeOnlinePlayerSwitch;
    private Switch startupAutoplaySwitch;
    private Switch startupInternetSwitch;
    private TextView startupInternetTimeoutLabel;
    private SeekBar startupInternetTimeoutSeekBar;
    private Switch sourceLostSwitch;
    private Switch sourceLostAutoplaySwitch;
    private Switch radioWidgetBroadcastSwitch;
    private Switch clusterCoversSwitch;
    private Switch clusterOnlineSwitch;
    private TextView clusterWatchdogLabel;
    private SeekBar clusterWatchdogSeekBar;
    private TextView clusterReassertBurstLabel;
    private SeekBar clusterReassertBurstSeekBar;
    private TextView radioCatalogInfoText;
    private LinearLayout radioCatalogList;
    /** The catalog the picked ZIP replaces, or null when it adds a new catalog. */
    private String pendingReplaceCatalogId;
    /** The pack a picked ZIP must update, when the import was started from that pack's menu. */
    private String pendingUpdatePackId;
    /** A newly loaded pack whose city picker opens once the settings list it. */
    private String pendingCityPickerCatalogId;
    private TextView mediaStatusText;
    private final List<View> mediaSettingsViews = new ArrayList<>();
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
    private Switch statusBarMedia;
    private Switch cardOpensPlayer;
    private RadioGroup statusBarFormatGroup;
    private RadioButton[] statusBarFormatButtons;
    private Button exportSettingsButton;
    private Button importSettingsButton;
    private Button importRadioCatalogButton;
    private TextView coverDimTitle;
    private RadioGroup coverDimPresetGroup;
    private LinearLayout backdropSection;
    private RadioButton backdropArtworkButton;
    private RadioButton backdropSolidButton;
    private LinearLayout backdropSolidOptions;
    private LabeledSeek backdropAlphaSetting;
    private LinearLayout artworkAlphaSection;
    private LabeledSeek artworkAlphaSetting;
    private int artworkAlpha = 255;
    private LabeledSeek contentAlphaSetting;
    private int contentAlpha = 255;
    private View backdropSwatch;
    private Switch backdropOpaqueControls;
    private Switch backdropOpaqueTopRow;
    private Button backdropColorButton;
    private LinearLayout backdropColorOptions;
    private int backdropColor = CardBackdrop.THEME_COLOR;
    private int backdropAlpha = CardBackdrop.DEFAULT_ALPHA;
    private int backdropDayColor = CardBackdrop.THEME_COLOR;
    private int backdropDayAlpha = CardBackdrop.DEFAULT_ALPHA;
    private CardColors cardColors = CardColors.DEFAULT;
    private final View[] paletteSwatches = new View[3];
    private final Button[] paletteButtons = new Button[3];
    /**
     * The mode whose colours the settings edit and the preview shows. The overlay is always
     * night; in widget mode it starts at the widget's theme and is not stored.
     */
    private boolean editNight = true;
    /** The День/Ночь choice of the edited colours, in widget mode only. */
    private RadioGroup paletteModes;
    private RadioButton paletteDay;
    private RadioButton paletteNight;
    private RadioButton[] coverDimPresetButtons;
    private LinearLayout thumbnailSizeSection;
    private LabeledSeek thumbnailSizeSetting;
    private LabeledSeek cornerRadiusSetting;
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
    private TextView playerActionsValue;
    private TextView playerActionsPlayer;
    private LinearLayout playerActionsList;
    private Button playerActionsReset;
    private MediaSnapshot playerActionsSnapshot;
    private List<?> playerActionsKey;
    private SeekBar playerActions;
    private TextView favoriteColumnsValue;
    private SeekBar favoriteColumns;
    private TextView favoriteRowsValue;
    private SeekBar favoriteRows;
    private boolean refreshingStyle;

    private final MediaBridgeClient.Listener mediaBridgeListener = new MediaBridgeClient.Listener() {
        @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
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
                    setMediaSettingsEnabled(false);
                    setRadioCatalogTransferEnabled(false);
                    mediaStatusText.setText("Подключение к медиасервису…");
                    mediaStatusText.setTextColor(Ui.SECONDARY);
                } else {
                    currentMediaSettings = null;
                    setMediaSettingsEnabled(false);
                    setRadioCatalogTransferEnabled(false);
                    mediaStatusText.setText("Медиасервис недоступен: " + (detail != null ? detail : state.name()));
                    mediaStatusText.setTextColor(Ui.ERROR);
                }
            }
        }

        @Override public void onSnapshot(MediaSnapshot snapshot) {
            if (isDestroyed()) return;
            playerActionsSnapshot = snapshot;
            refreshPlayerActionChoices(false);
        }
        @Override public void onCommandResult(String requestId, int status, String message, long generation) {}
        @Override public void onRadioStations(RadioStationLists lists) {}
        @Override public void onRadioStationsError(int status, String message) {}
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openEditedWidget(savedInstanceState);
        mediaBridgeClient = new MediaBridgeClient(this, mediaBridgeListener);
        View content = buildContent(savedInstanceState == null ? TAB_CARD
                : savedInstanceState.getInt(STATE_TAB, TAB_CARD));
        setContentView(content);
        Ui.applySystemBarInsets(content);
        AtlasMediaWidgetProvider.refresh(this);
    }

    /** Every look control reads and writes through {@link #prefs}, so it alone picks the widget. */
    private void openEditedWidget(Bundle savedInstanceState) {
        Prefs shared = new Prefs(this);
        widgetIds = AtlasMediaWidgetProvider.sortedIds(this);
        editedWidget = shared.isWidgetMode() ? requestedWidget(savedInstanceState) : TEMPLATE;
        prefs = editedWidget == TEMPLATE ? shared : shared.forWidget(editedWidget);
        if (editedWidget == TEMPLATE && shared.isWidgetMode()) {
            // Editing the template must not restyle widgets that still follow it.
            for (int id : widgetIds) shared.forWidget(id).ensureProfile();
        }
    }

    private int requestedWidget(Bundle savedInstanceState) {
        int fallback = widgetIds.length == 0 ? TEMPLATE : widgetIds[0];
        int requested = savedInstanceState != null
                && savedInstanceState.containsKey(STATE_EDITED_WIDGET)
                ? savedInstanceState.getInt(STATE_EDITED_WIDGET)
                : getIntent().getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                        fallback);
        if (requested == TEMPLATE) return savedInstanceState != null ? TEMPLATE : fallback;
        return Arrays.stream(widgetIds).anyMatch(id -> id == requested) ? requested : fallback;
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
        if (builtWidgetMode) outState.putInt(STATE_EDITED_WIDGET, editedWidget);
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
        if (isFinishing()) return;
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

    private View buildContent(int initialTab) {
        builtWidgetMode = prefs.isWidgetMode();
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
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.addView(text(getString(R.string.app_name), 24, Ui.PRIMARY, Typeface.BOLD));
        TextView version = text(BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ")", 12, Ui.SECONDARY, Typeface.NORMAL);
        version.setSingleLine(true);
        version.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        versionParams.leftMargin = Ui.dp(this, 8);
        titleBox.addView(version, versionParams);
        previewTitleRow.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView previewTitle = text(getString(R.string.preview_title), 13,
                Ui.SECONDARY, Typeface.NORMAL);
        widgetPreviewTitle = previewTitle;
        previewTitleRow.addView(previewTitle);
        ImageView donate = new ImageView(this);
        donate.setImageResource(R.drawable.ic_heart);
        donate.setColorFilter(Ui.ACCENT);
        int heartPad = Ui.dp(this, 8);
        donate.setPadding(heartPad, heartPad, heartPad, heartPad);
        donate.setBackground(Ui.background(Ui.CARD, 8, this));
        donate.setContentDescription(getString(R.string.donate_title));
        donate.setOnClickListener(v -> DonateDialog.show(this));
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        heartParams.leftMargin = Ui.dp(this, 12);
        previewTitleRow.addView(donate, heartParams);
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

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int tab = i;
            TextView button = Ui.segment(this, getString(TAB_LABELS[i]));
            button.setTextSize(15);
            button.setOnClickListener(v -> selectTab(tab));
            tabButtons[i] = button;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) params.leftMargin = Ui.dp(this, 6);
            tabs.addView(button, params);
        }
        LinearLayout.LayoutParams tabsParams = fullWrap();
        tabsParams.topMargin = Ui.dp(this, 12);
        stickyPreview.addView(tabs, tabsParams);
        screen.addView(stickyPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 8),
                Ui.dp(this, 24), Ui.dp(this, 42));
        settingsScroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < tabPages.length; i++) {
            tabPages[i] = new LinearLayout(this);
            tabPages[i].setOrientation(LinearLayout.VERTICAL);
            root.addView(tabPages[i], fullWrap());
        }

        buildCardTab(tabPages[TAB_CARD]);
        buildMediaTab(tabPages[TAB_MEDIA]);
        buildRadioTab(tabPages[TAB_RADIO]);
        buildSystemTab(tabPages[TAB_SYSTEM]);
        setMediaSettingsEnabled(false);

        refreshModeVisibility();
        screen.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        selectTab(initialTab < 0 || initialTab >= tabPages.length ? TAB_CARD : initialTab);
        return screen;
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        for (int i = 0; i < tabPages.length; i++) {
            tabPages[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            Ui.setSegmentSelected(this, tabButtons[i], i == tab);
        }
        int preview = tab == TAB_CARD ? View.VISIBLE : View.GONE;
        previewHost.setVisibility(preview);
        widgetPreviewTitle.setVisibility(preview);
        settingsScroll.scrollTo(0, 0);
        if (tab == TAB_CARD) renderPreview();
    }

    private void refreshModeVisibility() {
        boolean widgetMode = prefs.isWidgetMode();
        for (View view : overlayOnly) view.setVisibility(widgetMode ? View.GONE : View.VISIBLE);
        for (View view : widgetOnly) view.setVisibility(widgetMode ? View.VISIBLE : View.GONE);
    }

    private void buildCardTab(LinearLayout page) {
        page.addView(createModeCard());
        LinearLayout placementCard = createWidgetPlacementCard();
        page.addView(placementCard);
        widgetOnly.add(placementCard);

        LinearLayout overlayCard = card();
        overlayCard.addView(text("Оверлей", 20, Ui.PRIMARY, Typeface.BOLD));
        TextView overlayNote = text(
                "Карточка показывается поверх HOME, только когда он на переднем плане. "
                        + "Перетаскивание — за точки ⋮ в правом верхнем углу, нажатие на "
                        + "свободную область открывает активный медиаисточник.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams overlayNoteParams = fullWrap();
        overlayNoteParams.topMargin = Ui.dp(this, 6);
        overlayCard.addView(overlayNote, overlayNoteParams);
        overlayCard.addView(text(getString(R.string.permissions_title), 15,
                Ui.SECONDARY, Typeface.BOLD), labelParams());
        overlayPermissionButton = actionButton("Разрешить поверх окон");
        overlayPermissionButton.setOnClickListener(v -> openOverlaySettings());
        overlayCard.addView(overlayPermissionButton, buttonParams());
        usageAccessButton = actionButton("Разрешить историю использования");
        usageAccessButton.setOnClickListener(v -> openUsageSettingsForApp());
        overlayCard.addView(usageAccessButton, buttonParams());
        accessibilityAccessButton = actionButton(getString(R.string.allow_accessibility));
        accessibilityAccessButton.setOnClickListener(v -> openAccessibilitySettings());
        overlayCard.addView(accessibilityAccessButton, buttonParams());
        serviceButton = actionButton("Запустить");
        serviceButton.setOnClickListener(v -> toggleService());
        LinearLayout.LayoutParams serviceParams = buttonParams();
        serviceParams.topMargin = Ui.dp(this, 18);
        overlayCard.addView(serviceButton, serviceParams);
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
        overlayCard.addView(autoStart, switchParams);
        page.addView(overlayCard);
        overlayOnly.add(overlayCard);

        LinearLayout geometryCard = createGeometryCard();
        page.addView(geometryCard);
        overlayOnly.add(geometryCard);
        LinearLayout visibilityCard = createVisibilityCard();
        page.addView(visibilityCard);
        overlayOnly.add(visibilityCard);

        page.addView(createLookCard());
        LinearLayout themeCard = createThemeCard();
        page.addView(themeCard);
        widgetOnly.add(themeCard);
        page.addView(createSourceTilesCard());
        page.addView(createPlayerActionsCard());
        createTypographySection(collapsibleCard(page, "Текст и отступы",
                "Размеры шрифтов, отступы и линия прогресса"));
        createControlsSection(collapsibleCard(page, "Панель управления",
                "Высота панели, размер и разбежка кнопок"));
        createColorsSection(collapsibleCard(page, "Свои цвета",
                "Необязательно: без них карточка следует теме OneOS"));
    }

    private LinearLayout createModeCard() {
        LinearLayout card = card();
        card.addView(text("Режим отображения", 20, Ui.PRIMARY, Typeface.BOLD));
        RadioGroup modes = new RadioGroup(this);
        modes.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton overlay = styleButton("Оверлей");
        RadioButton widget = styleButton("Виджет");
        modes.addView(overlay);
        modes.addView(widget);
        modes.check(prefs.isWidgetMode() ? widget.getId() : overlay.getId());
        modes.setOnCheckedChangeListener((group, id) -> {
            boolean nativeWidget = id == widget.getId();
            if (nativeWidget == prefs.isWidgetMode()) return;
            prefs.setWidgetMode(nativeWidget);
            AtlasMediaWidgetProvider.refresh(this);
            if (!nativeWidget && prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                BootReceiver.startIfAllowed(this, prefs);
            }
            recreate();
        });
        card.addView(modes);
        card.addView(text(prefs.isWidgetMode()
                        ? "Системный виджет HOME: размер и место задаёт лаунчер."
                        : "Отдельное окно поверх HOME: размер и положение задаются здесь.",
                13, Ui.SECONDARY, Typeface.NORMAL));
        cardOpensPlayer = new Switch(this);
        cardOpensPlayer.setText("Открывать плеер нажатием на карточку");
        cardOpensPlayer.setTextColor(Ui.PRIMARY);
        cardOpensPlayer.setTextSize(15);
        cardOpensPlayer.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_CARD_OPENS_PLAYER, checked);
        });
        LinearLayout.LayoutParams opensPlayerParams = fullWrap();
        opensPlayerParams.topMargin = Ui.dp(this, 14);
        card.addView(cardOpensPlayer, opensPlayerParams);
        return card;
    }

    private LinearLayout createWidgetPlacementCard() {
        LinearLayout card = card();
        card.addView(text("На главном экране", 20, Ui.PRIMARY, Typeface.BOLD));
        widgetPlacementStatus = text("", 15, Ui.PRIMARY, Typeface.NORMAL);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(this, 6);
        card.addView(widgetPlacementStatus, statusParams);
        addWidgetButton = actionButton("Добавить виджет");
        addWidgetButton.setOnClickListener(v -> {
            var manager = android.appwidget.AppWidgetManager.getInstance(this);
            if (manager.isRequestPinAppWidgetSupported() && manager.requestPinAppWidget(
                    new ComponentName(this, AtlasMediaWidgetProvider.class), null, null)) return;
            Toast.makeText(this, "Удерживайте свободное место на HOME → Виджеты → Atlas Media Widget",
                    Toast.LENGTH_LONG).show();
        });
        card.addView(addWidgetButton, buttonParams());
        TextView hint = text("Или удерживайте свободное место на HOME → Виджеты → "
                        + "Atlas Media Widget. Медиасервис запускается сам после загрузки ГУ, пока размещён "
                        + "хотя бы один виджет.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = fullWrap();
        hintParams.topMargin = Ui.dp(this, 10);
        card.addView(hint, hintParams);
        addEditedWidgetChooser(card);
        return card;
    }

    private void addEditedWidgetChooser(LinearLayout card) {
        card.addView(text("Какой виджет настраивать", 15, Ui.SECONDARY, Typeface.BOLD),
                labelParams());
        List<String> labels = new ArrayList<>();
        int selection = widgetIds.length;
        for (int index = 0; index < widgetIds.length; index++) {
            labels.add(editedWidgetLabel(widgetIds[index]));
            if (widgetIds[index] == editedWidget) selection = index;
        }
        labels.add(editedWidgetLabel(TEMPLATE));
        Spinner chooser = new Spinner(this);
        chooser.setContentDescription("Какой виджет настраивать");
        chooser.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        chooser.setSelection(selection, false);
        chooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                int target = position < widgetIds.length ? widgetIds[position] : TEMPLATE;
                if (target == editedWidget) return;
                editedWidget = target;
                recreate();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        card.addView(dropdownField(chooser), fullWrap());
        LinearLayout.LayoutParams noteParams = fullWrap();
        noteParams.topMargin = Ui.dp(this, 8);
        card.addView(text("Настройки карточки ниже меняют только выбранный виджет. Новый виджет "
                        + "начинает с шаблона, и этим же оформлением показывается оверлей. Общими "
                        + "остаются правила кнопок плеера и навигация по радио.",
                13, Ui.SECONDARY, Typeface.NORMAL), noteParams);
        if (editedWidget != TEMPLATE && widgetIds.length > 1) {
            Button applyToAll = actionButton("Применить ко всем виджетам");
            applyToAll.setOnClickListener(v -> {
                prefs.copyLookTo(widgetIds);
                refreshOverlayIfRunning();
                Toast.makeText(this, "Оформление применено ко всем виджетам",
                        Toast.LENGTH_SHORT).show();
            });
            card.addView(applyToAll, buttonParams());
        }
    }

    private String editedWidgetLabel(int id) {
        if (id == TEMPLATE) return "Шаблон для новых виджетов";
        Bundle options = android.appwidget.AppWidgetManager.getInstance(this)
                .getAppWidgetOptions(id);
        int number = Arrays.binarySearch(widgetIds, id) + 1;
        return "Виджет " + number + " · " + AtlasMediaWidgetProvider.widthDp(this, options) + "×"
                + AtlasMediaWidgetProvider.heightDp(this, options) + " dp";
    }

    private void refreshWidgetPlacement() {
        if (widgetPlacementStatus == null) return;
        int placed = AtlasMediaWidgetProvider.ids(this).length;
        widgetPlacementStatus.setText(placed == 0 ? "Виджет ещё не добавлен"
                : "Размещено виджетов: " + placed);
        addWidgetButton.setBackground(Ui.background(placed == 0 ? Ui.ACCENT : Ui.NESTED, 8, this));
        addWidgetButton.setTextColor(placed == 0 ? Ui.ON_ACCENT : Ui.PRIMARY);
    }

    private FrameLayout dropdownField(Spinner spinner) {
        FrameLayout field = new FrameLayout(this);
        field.setBackground(Ui.background(Ui.NESTED, 8, this));
        spinner.setBackgroundColor(0x00000000);
        spinner.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 34), 0);
        field.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView arrow = text("▾", 16, Ui.ACCENT, Typeface.BOLD);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        arrowParams.rightMargin = Ui.dp(this, 10);
        field.addView(arrow, arrowParams);
        field.setOnClickListener(v -> spinner.performClick());
        return field;
    }

    private LinearLayout createGeometryCard() {
        LinearLayout geometryCard = card();
        geometryCard.addView(text("Размер и положение", 20, Ui.PRIMARY, Typeface.BOLD));
        geometryCard.addView(text("Размер карточки", 15, Ui.SECONDARY, Typeface.BOLD),
                labelParams());
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
        LinearLayout.LayoutParams sizeRowParams = fullWrap();
        sizeRowParams.topMargin = Ui.dp(this, 6);
        geometryCard.addView(sizeRow, sizeRowParams);

        TextView positionTitle = text("Положение карточки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams positionTitleParams = fullWrap();
        positionTitleParams.topMargin = Ui.dp(this, 14);
        geometryCard.addView(positionTitle, positionTitleParams);
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

        FrameLayout positionCornerField = dropdownField(positionCornerSpinner);

        LinearLayout positionGrid = new LinearLayout(this);
        positionGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout positionHeader = new LinearLayout(this);
        positionHeader.setOrientation(LinearLayout.HORIZONTAL);
        positionHeader.addView(text("Угол привязки", 14, Ui.SECONDARY, Typeface.NORMAL),
                positionColumnParams(1.25f, 0));
        positionHeader.addView(text("Отступ X", 14, Ui.SECONDARY, Typeface.NORMAL),
                positionColumnParams(1f, 8));
        positionHeader.addView(text("Отступ Y", 14, Ui.SECONDARY, Typeface.NORMAL),
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
        geometryCard.addView(positionGrid, fullWrap());

        dragHandleVisible = new Switch(this);
        dragHandleVisible.setText("Показывать точки перемещения на карточке");
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
        geometryCard.addView(dragHandleVisible, dragHandleParams);
        TextView dragHandleHint = text(
                "Если точки скрыты, включите их здесь снова, чтобы переместить карточку.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams dragHandleHintParams = fullWrap();
        dragHandleHintParams.topMargin = Ui.dp(this, 5);
        geometryCard.addView(dragHandleHint, dragHandleHintParams);
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

        Button resetSize = actionButton("Вернуть размер по умолчанию");
        resetSize.setOnClickListener(v -> {
            prefs.putCardSizePx(500, 500);
            refreshSizeControls(currentStyle());
            if (prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                OverlayService.refreshStyle(this);
            }
        });
        geometryCard.addView(resetSize, buttonParams());
        return geometryCard;
    }

    private LinearLayout createThemeCard() {
        LinearLayout card = card();
        card.addView(text("Тема", 20, Ui.PRIMARY, Typeface.BOLD));
        RadioGroup themes = new RadioGroup(this);
        themes.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton[] buttons = {styleButton("Авто"), styleButton("День"), styleButton("Ночь")};
        for (RadioButton button : buttons) themes.addView(button);
        themes.check(buttons[prefs.widgetTheme()].getId());
        themes.setOnCheckedChangeListener((group, id) -> {
            int theme = id == buttons[CardTheme.NIGHT].getId() ? CardTheme.NIGHT
                    : id == buttons[CardTheme.DAY].getId() ? CardTheme.DAY : CardTheme.AUTO;
            prefs.putInt(Prefs.KEY_WIDGET_THEME, theme);
            // The colours being edited and the preview follow the theme the widget now shows.
            if (paletteModes != null) {
                paletteModes.check(widgetNight() ? paletteNight.getId() : paletteDay.getId());
            }
        });
        card.addView(themes, fullWrap());
        card.addView(text("«Авто» переключает карточку вместе с дневным и ночным режимом OneOS. "
                        + "«День» и «Ночь» оставляют её в одной теме при любом режиме.",
                13, Ui.SECONDARY, Typeface.NORMAL), fullWrap());
        return card;
    }

    /** Whether the edited widget shows its night theme now. */
    private boolean widgetNight() {
        int theme = prefs.widgetTheme();
        return theme == CardTheme.AUTO ? CardTheme.isNight(getResources().getConfiguration())
                : theme == CardTheme.NIGHT;
    }

    private LinearLayout createLookCard() {
        LinearLayout lookCard = card();
        lookCard.addView(text(getString(R.string.appearance_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        lookCard.addView(text("Компоновка", 15, Ui.SECONDARY, Typeface.BOLD), labelParams());
        RadioGroup styles = new RadioGroup(this);
        styles.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton compactStyle = styleButton("Компактная");
        RadioButton squareStyle = styleButton("Просторная");
        styles.addView(compactStyle);
        styles.addView(squareStyle);
        styles.check(currentStyle() == CardStyle.COMPACT ? compactStyle.getId() : squareStyle.getId());
        styles.setOnCheckedChangeListener((group, id) -> {
            prefs.putInt(Prefs.KEY_CARD_STYLE, (id == compactStyle.getId()
                    ? CardStyle.COMPACT : CardStyle.SQUARE).preferenceValue);
            refreshingStyle = true;
            refreshSizeControls(currentStyle());
            refreshingStyle = false;
            refreshOverlayIfRunning();
        });
        lookCard.addView(styles);
        lookCard.addView(text("Компактная подходит для широкой невысокой карточки; просторная — для квадратной или высокой. Выбор не меняет размер карточки.",
                13, Ui.SECONDARY, Typeface.NORMAL));
        backdropSection = new LinearLayout(this);
        backdropSection.setOrientation(LinearLayout.VERTICAL);
        backdropSection.addView(text("Подложка", 15, Ui.SECONDARY, Typeface.BOLD),
                labelParams());
        RadioGroup backdropGroup = new RadioGroup(this);
        backdropGroup.setOrientation(RadioGroup.HORIZONTAL);
        backdropArtworkButton = styleButton("Обложка");
        backdropSolidButton = styleButton("Один цвет");
        backdropGroup.addView(backdropArtworkButton);
        backdropGroup.addView(backdropSolidButton);
        backdropGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateBackdropControls();
            if (!refreshingStyle) saveAppearance();
        });
        backdropSection.addView(backdropGroup, fullWrap());
        backdropSolidOptions = new LinearLayout(this);
        backdropSolidOptions.setOrientation(LinearLayout.VERTICAL);
        backdropSolidOptions.addView(text("Выключено — панель прозрачна, как подложка; "
                + "включено — всегда непрозрачна.", 13, Ui.SECONDARY, Typeface.NORMAL),
                labelParams());
        backdropOpaqueTopRow = backdropPanelSwitch("Непрозрачная верхняя панель");
        backdropOpaqueControls = backdropPanelSwitch("Непрозрачная панель управления");
        backdropSection.addView(backdropSolidOptions, fullWrap());
        lookCard.addView(backdropSection, fullWrap());

        coverDimTitle = text("Затемнение обложки", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams coverDimTitleParams = fullWrap();
        coverDimTitleParams.topMargin = Ui.dp(this, 14);
        lookCard.addView(coverDimTitle, coverDimTitleParams);
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
        lookCard.addView(coverDimPresetGroup, fullWrap());

        // Fades the artwork, its veil and the surface; the elements have their own slider.
        artworkAlphaSection = new LinearLayout(this);
        artworkAlphaSection.setOrientation(LinearLayout.VERTICAL);
        artworkAlphaSetting = addLabeledSeek(artworkAlphaSection,
                "Непрозрачность карточки с обложкой", 0, OPACITY_STEPS);
        artworkAlphaSection.addView(text("Обложка с затемнением и фоном карточки; текст и "
                + "кнопки настраиваются ниже.", 13, Ui.SECONDARY, Typeface.NORMAL));
        artworkAlphaSetting.seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (!fromUser) return;
                artworkAlpha = opacityAlpha(progress);
                updateAppearanceLabels();
                renderPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!refreshingStyle) saveAppearance();
            }
        });
        lookCard.addView(artworkAlphaSection, fullWrap());

        contentAlphaSetting = addLabeledSeek(lookCard, "Непрозрачность элементов",
                0, OPACITY_STEPS);
        lookCard.addView(text("Текст, плашки, прогресс и кнопки поверх подложки.",
                13, Ui.SECONDARY, Typeface.NORMAL));
        contentAlphaSetting.seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (!fromUser) return;
                contentAlpha = opacityAlpha(progress);
                updateAppearanceLabels();
                renderPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!refreshingStyle) saveAppearance();
            }
        });

        thumbnailSizeSection = new LinearLayout(this);
        thumbnailSizeSection.setOrientation(LinearLayout.VERTICAL);
        thumbnailSizeSetting = addLabeledSeek(thumbnailSizeSection,
                "Размер маленькой обложки", Prefs.MIN_THUMBNAIL_SIZE_DP,
                Prefs.MAX_THUMBNAIL_SIZE_DP);
        lookCard.addView(thumbnailSizeSection, fullWrap());
        cornerRadiusSetting = addLabeledSeek(lookCard, "Радиус карточки",
                0, Prefs.MAX_CARD_RADIUS_DP);
        return lookCard;
    }

    private void createColorsSection(LinearLayout section) {
        cardColors = prefs.cardColors();
        boolean widgetMode = prefs.isWidgetMode();
        editNight = !widgetMode || widgetNight();
        if (widgetMode) {
            RadioGroup modes = new RadioGroup(this);
            modes.setOrientation(RadioGroup.HORIZONTAL);
            RadioButton day = styleButton("День");
            RadioButton night = styleButton("Ночь");
            paletteModes = modes;
            paletteDay = day;
            paletteNight = night;
            modes.addView(day);
            modes.addView(night);
            modes.check(editNight ? night.getId() : day.getId());
            modes.setOnCheckedChangeListener((group, id) -> {
                editNight = id == night.getId();
                updatePaletteSwatches();
                updateBackdropControls();
                updateAppearanceLabels();
                renderPreview();
            });
            section.addView(modes, fullWrap());
        }
        section.addView(text(widgetMode
                        ? "Цвета отдельно для дня и ночи; предпросмотр показывает выбранный режим. "
                                + "«По теме» в выборе цвета возвращает цвет темы. Фон карточки — "
                                + "также цвет вуали над обложкой. Текст подбирается по яркости фона, "
                                + "пока не задан свой."
                        : "Оверлей не следует теме OneOS и всегда использует эти цвета. Фон "
                                + "карточки — также цвет вуали над обложкой; второстепенный текст "
                                + "берётся из цвета текста.",
                13, Ui.SECONDARY, Typeface.NORMAL));
        addPaletteRow(section, 0, "Фон карточки");

        backdropColorOptions = new LinearLayout(this);
        backdropColorOptions.setOrientation(LinearLayout.VERTICAL);
        backdropAlphaSetting = addLabeledSeek(backdropColorOptions,
                "Непрозрачность подложки", 0, OPACITY_STEPS);
        backdropAlphaSetting.seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (!fromUser) return;
                if (editingDay()) {
                    backdropDayAlpha = opacityAlpha(progress);
                } else {
                    backdropAlpha = opacityAlpha(progress);
                }
                updateAppearanceLabels();
                renderPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!refreshingStyle) saveAppearance();
            }
        });
        backdropSwatch = new View(this);
        LinearLayout backdropRow = colorRow(backdropSwatch, "Цвет подложки",
                () -> ColorPickerDialog.show(this, "Цвет подложки", shownBackdropRgb(),
                        CardBackdrop.THEME_COLOR, color -> {
                            setShownBackdropColor(color);
                            updateBackdropControls();
                            saveAppearance();
                        }));
        backdropColorButton = (Button) backdropRow.getChildAt(1);
        backdropColorOptions.addView(backdropRow, colorRowParams());
        section.addView(backdropColorOptions, fullWrap());

        addPaletteRow(section, 1, "Текст");
        addPaletteRow(section, 2, "Акцент и прогресс");
        Button reset = actionButton("Вернуть цвета по умолчанию");
        reset.setOnClickListener(v -> {
            setShownBackdropColor(CardBackdrop.THEME_COLOR);
            savePalette(CardPalette.defaults(editNight));
            updateBackdropControls();
            saveAppearance();
        });
        section.addView(reset, buttonParams());
        updatePaletteSwatches();
    }

    private void addPaletteRow(LinearLayout parent, int channel, String label) {
        paletteSwatches[channel] = new View(this);
        LinearLayout row = colorRow(paletteSwatches[channel], label,
                () -> ColorPickerDialog.show(this, label, paletteColor(channel),
                        channelOf(CardPalette.defaults(editNight), channel), color -> {
                            CardPalette palette = cardColors.palette(editNight);
                            int[] colors = {palette.background, palette.text, palette.accent};
                            colors[channel] = color;
                            savePalette(palette.with(colors[0], colors[1], colors[2]));
                        }));
        paletteButtons[channel] = (Button) row.getChildAt(1);
        paletteButtons[channel].setTag(label);
        parent.addView(row, colorRowParams());
    }

    private static int channelOf(CardPalette palette, int channel) {
        return channel == 0 ? palette.background
                : channel == 1 ? palette.text : palette.accent;
    }

    private int paletteColor(int channel) {
        return channelOf(cardColors.palette(editNight), channel);
    }

    private void savePalette(CardPalette value) {
        cardColors = cardColors.with(value);
        prefs.putCardColors(cardColors);
        updatePaletteSwatches();
        renderPreview();
        refreshOverlayIfRunning();
    }

    private void updatePaletteSwatches() {
        CardPalette defaults = CardPalette.defaults(editNight);
        for (int index = 0; index < paletteSwatches.length; index++) {
            paletteSwatches[index].setBackground(
                    Ui.background(0xFF000000 | paletteColor(index), 8, this));
            paletteButtons[index].setText(paletteButtons[index].getTag()
                    + (paletteColor(index) == channelOf(defaults, index) ? "  по теме" : ""));
        }
    }

    /** Whether the settings edit the day colours, which only the AppWidget shows. */
    private boolean editingDay() {
        return !editNight && prefs.isWidgetMode();
    }

    private int shownBackdropColor() {
        return editingDay() ? backdropDayColor : backdropColor;
    }

    private void setShownBackdropColor(int color) {
        if (editingDay()) {
            backdropDayColor = color;
        } else {
            backdropColor = color;
        }
    }

    /** The colour shown for the edited mode, with a colour left to the theme resolved. */
    private int shownBackdropRgb() {
        int color = shownBackdropColor();
        return color == CardBackdrop.THEME_COLOR ? paletteColor(0) : color;
    }

    private int shownBackdropAlpha() {
        return editingDay() ? backdropDayAlpha : backdropAlpha;
    }

    /** A colour swatch with the button that opens its picker. */
    private LinearLayout colorRow(View swatch, String label, Runnable pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44));
        swatchParams.rightMargin = Ui.dp(this, 12);
        row.addView(swatch, swatchParams);
        Button button = actionButton(label);
        button.setOnClickListener(v -> pick.run());
        row.addView(button, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private LinearLayout.LayoutParams colorRowParams() {
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, 12);
        return params;
    }

    private void createTypographySection(LinearLayout body) {
        body.addView(text("Отступ текста от прогресса",
                14, Ui.SECONDARY, Typeface.NORMAL), labelParams());
        metadataProgressGapValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        body.addView(metadataProgressGapValue, fullWrap());
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
        body.addView(metadataProgressGap, fullWrap());

        topInsetSetting = addLabeledSeek(body, "Отступ верхней строки",
                Prefs.MIN_TOP_INSET_DP, Prefs.MAX_TOP_INSET_DP);
        contentInsetSetting = addLabeledSeek(body, "Боковой отступ контента",
                Prefs.MIN_CONTENT_INSET_DP, Prefs.MAX_CONTENT_INSET_DP);
        topRowTextSetting = addLabeledSeek(body,
                "Размер плашек источника и избранного",
                Prefs.MIN_TOP_ROW_TEXT_SIZE_SP, Prefs.MAX_TOP_ROW_TEXT_SIZE_SP);
        titleTextSetting = addLabeledSeek(body, "Размер названия",
                Prefs.MIN_TITLE_TEXT_SIZE_SP, Prefs.MAX_TITLE_TEXT_SIZE_SP);
        subtitleTextSetting = addLabeledSeek(body, "Размер исполнителя и альбома",
                Prefs.MIN_SUBTITLE_TEXT_SIZE_SP, Prefs.MAX_SUBTITLE_TEXT_SIZE_SP);
        subtitleGapSetting = addLabeledSeek(body, "Отступ подзаголовка",
                0, Prefs.MAX_SUBTITLE_GAP_DP);
        timeTextSetting = addLabeledSeek(body, "Размер времени",
                Prefs.MIN_TIME_TEXT_SIZE_SP, Prefs.MAX_TIME_TEXT_SIZE_SP);
        progressGapSetting = addLabeledSeek(body, "Отступ прогресса от панели",
                0, Prefs.MAX_PROGRESS_GAP_DP);
        progressThicknessSetting = addLabeledSeek(body, "Толщина линии прогресса",
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
                progressGapSetting, progressThicknessSetting, thumbnailSizeSetting,
                cornerRadiusSetting);

        Button resetAppearance = actionButton("Вернуть текст и отступы по умолчанию");
        resetAppearance.setOnClickListener(v -> {
            CardStyle current = currentStyle();
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
                    existing.coverDimPreset,
                    existing.thumbnailSizeDp,
                    existing.backdrop,
                    existing.cornerRadiusDp));
            refreshSizeControls(current);
            refreshOverlayIfRunning();
        });
        body.addView(resetAppearance, buttonParams());
    }

    private void createControlsSection(LinearLayout body) {
        body.addView(text("Высота нижней панели", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlPanelHeightValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        body.addView(controlPanelHeightValue, fullWrap());
        controlPanelHeight = sizeSeekBar(Prefs.MIN_CONTROL_PANEL_HEIGHT_DP,
                Prefs.MAX_CONTROL_PANEL_HEIGHT_DP);
        body.addView(controlPanelHeight, fullWrap());

        body.addView(text("Размер иконок", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlIconScaleValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        body.addView(controlIconScaleValue, fullWrap());
        controlIconScale = sizeSeekBar(Prefs.MIN_CONTROL_ICON_SCALE_PERCENT,
                Prefs.MAX_CONTROL_ICON_SCALE_PERCENT);
        body.addView(controlIconScale, fullWrap());

        body.addView(text("Разбежка боковых иконок от центра", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlSpreadValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        body.addView(controlSpreadValue, fullWrap());
        controlSpread = sizeSeekBar(Prefs.MIN_CONTROL_SPREAD_PERCENT,
                Prefs.MAX_CONTROL_SPREAD_PERCENT);
        body.addView(controlSpread, fullWrap());

        body.addView(text("Дополнительный отступ иконок от нижней границы", 14,
                Ui.SECONDARY, Typeface.NORMAL), labelParams());
        controlBottomInsetValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        body.addView(controlBottomInsetValue, fullWrap());
        controlBottomInset = sizeSeekBar(0, Prefs.MAX_CONTROL_BOTTOM_INSET_DP);
        body.addView(controlBottomInset, fullWrap());

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
        controlBottomInset.setOnSeekBarChangeListener(controlListener);

        Button resetControls = actionButton("Вернуть панель по умолчанию");
        resetControls.setOnClickListener(v -> {
            CardStyle current = currentStyle();
            prefs.putControlLayout(current, current.defaultControlPanelHeightDp,
                    Prefs.DEFAULT_CONTROL_ICON_SCALE_PERCENT,
                    Prefs.DEFAULT_CONTROL_SPREAD_PERCENT, 0);
            refreshSizeControls(current);
            refreshOverlayIfRunning();
        });
        body.addView(resetControls, buttonParams());
    }

    private void buildRadioTab(LinearLayout page) {
        LinearLayout navigationCard = card();
        navigationCard.addView(text("Переключение станций", 20, Ui.PRIMARY, Typeface.BOLD));
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
        navigationCard.addView(radioSavedNavigation, radioNavigationParams);
        TextView radioNavigationHint = text(
                "Когда Радио активно, кнопки назад и вперёд напрямую выбирают соседнюю "
                        + "сохранённую станцию.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams radioHintParams = fullWrap();
        radioHintParams.topMargin = Ui.dp(this, 5);
        navigationCard.addView(radioNavigationHint, radioHintParams);
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
        navigationCard.addView(radioFavoritesNavigation, radioFavoritesNavigationParams);
        TextView radioFavoritesNavigationHint = text(
                "Если выключено, кнопки перелистывают все сохранённые станции.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams radioFavoritesHintParams = fullWrap();
        radioFavoritesHintParams.topMargin = Ui.dp(this, 5);
        navigationCard.addView(radioFavoritesNavigationHint, radioFavoritesHintParams);
        page.addView(navigationCard);

        LinearLayout favoritesGridCard = card();
        favoritesGridCard.addView(text("Сетка избранного",
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView favoritesGridHint = text(
                "Число столбцов и строк на первом экране списка избранного. "
                        + "Остальные станции доступны прокруткой."
                        + (builtWidgetMode ? " Настройка относится к выбранному на вкладке "
                                + "«Карточка»: " + editedWidgetLabel(editedWidget) + "." : ""),
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams favoritesGridHintParams = fullWrap();
        favoritesGridHintParams.topMargin = Ui.dp(this, 8);
        favoritesGridCard.addView(favoritesGridHint, favoritesGridHintParams);
        favoriteColumnsValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        favoritesGridCard.addView(favoriteColumnsValue, labelParams());
        favoriteColumns = sizeSeekBar(Prefs.MIN_RADIO_FAVORITES_GRID_COLUMNS,
                Prefs.MAX_RADIO_FAVORITES_GRID_COLUMNS);
        favoritesGridCard.addView(favoriteColumns, fullWrap());
        favoriteRowsValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        favoritesGridCard.addView(favoriteRowsValue, labelParams());
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
        page.addView(favoritesGridCard);

        page.addView(createRadioCatalogCard());
    }

    private void buildSystemTab(LinearLayout page) {
        LinearLayout settingsBackupCard = card();
        settingsBackupCard.addView(text("Резервная копия настроек",
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView settingsBackupHint = text(
                "Архив ZIP содержит настройки карточки, источника звука и приборной панели. "
                        + "Экспорт отправляет файл через «Поделиться» — например, себе в Telegram. "
                        + "Также поддерживается импорт прежних JSON-настроек. "
                        + "Каталог радио сохраняется отдельно во вкладке «Радио».",
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
        page.addView(settingsBackupCard);

        page.addView(createDiagnosticCard());

        LinearLayout scaleCard = card();
        scaleCard.addView(text(getString(R.string.scale_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView scaleHint = text(getString(R.string.scale_hint),
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams scaleHintParams = fullWrap();
        scaleHintParams.topMargin = Ui.dp(this, 6);
        scaleCard.addView(scaleHint, scaleHintParams);
        addScaleSlider(scaleCard);
        page.addView(scaleCard);

        page.addView(createGStoreCard());
        page.addView(createDonateCard());
    }

    private LinearLayout createGStoreCard() {
        LinearLayout gstoreCard = card();
        gstoreCard.addView(text(getString(R.string.gstore_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView gstoreHint = text(getString(R.string.gstore_hint),
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams gstoreHintParams = fullWrap();
        gstoreHintParams.topMargin = Ui.dp(this, 6);
        gstoreCard.addView(gstoreHint, gstoreHintParams);
        Button gstoreButton = actionButton(getString(R.string.gstore_button));
        gstoreButton.setOnClickListener(v -> GStoreDialog.show(this));
        gstoreCard.addView(gstoreButton, buttonParams());
        return gstoreCard;
    }

    private LinearLayout createDonateCard() {
        LinearLayout donateCard = card();
        donateCard.addView(text(getString(R.string.donate_title),
                20, Ui.PRIMARY, Typeface.BOLD));
        TextView donateHint = text(getString(R.string.donate_hint),
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams donateHintParams = fullWrap();
        donateHintParams.topMargin = Ui.dp(this, 6);
        donateCard.addView(donateHint, donateHintParams);
        Button donateButton = actionButton(getString(R.string.donate_button));
        donateButton.setOnClickListener(v -> DonateDialog.show(this));
        donateCard.addView(donateButton, buttonParams());
        return donateCard;
    }

    /** Adds a card whose body starts collapsed and returns the body for its controls. */
    private LinearLayout collapsibleCard(LinearLayout page, String title, String summary) {
        LinearLayout card = card();
        LinearLayout body = collapsible(card, title, summary, 20, Ui.PRIMARY);
        page.addView(card);
        return body;
    }

    /** The header stays outside the body so it remains usable while the body is disabled. */
    private LinearLayout collapsible(LinearLayout parent, String title, String summary,
            float titleSize, int titleColor) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setMinimumHeight(Ui.dp(this, 40));
        header.setClickable(true);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text(title, titleSize, titleColor, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = text("▸", 26, Ui.ACCENT, Typeface.BOLD);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleRow.addView(chevron);
        header.addView(titleRow, fullWrap());
        if (summary != null) {
            header.addView(text(summary, 13, Ui.SECONDARY, Typeface.NORMAL), fullWrap());
        }
        parent.addView(header, fullWrap());
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(View.GONE);
        parent.addView(body, fullWrap());
        header.setContentDescription(title + ", свёрнуто");
        header.setOnClickListener(v -> {
            boolean expand = body.getVisibility() != View.VISIBLE;
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.setText(expand ? "▾" : "▸");
            header.setContentDescription(title + (expand ? ", развёрнуто" : ", свёрнуто"));
        });
        return body;
    }

    private void renderSystemWidgetPreview() {
        var context = getApplicationContext();
        var manager = android.appwidget.AppWidgetManager.getInstance(context);
        int id = editedWidget != TEMPLATE || widgetIds.length == 0 ? editedWidget : widgetIds[0];
        Bundle options = id == TEMPLATE ? new Bundle() : manager.getAppWidgetOptions(id);
        widgetPreviewTitle.setText((editedWidget == TEMPLATE ? "Шаблон, размер "
                        : "Виджет " + (Arrays.binarySearch(widgetIds, editedWidget) + 1) + ", размер ")
                + AtlasMediaWidgetProvider.widthDp(context, options) + "×"
                + AtlasMediaWidgetProvider.heightDp(context, options) + " dp · демо");
        int maxHeight = Math.round(getWindowManager().getCurrentWindowMetrics().getBounds().height() * .30f);
        WidgetPreview.renderWidget(CardTheme.mode(context, editNight), previewHost,
                prefs, options, maxHeight);
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
        if (builtWidgetMode != prefs.isWidgetMode() || builtWidgetMode && !Arrays.equals(
                widgetIds, AtlasMediaWidgetProvider.sortedIds(this))) { recreate(); return; }
        refreshModeVisibility();
        refreshWidgetPlacement();
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
        statusBarMedia.setChecked(prefs.getBoolean(Prefs.KEY_STATUS_BAR_MEDIA, false));
        cardOpensPlayer.setChecked(prefs.getBoolean(Prefs.KEY_CARD_OPENS_PLAYER, true));
        statusBarFormatButtons[prefs.statusBarFormat().preferenceValue].setChecked(true);
        updateStatusBarFormatControls();
        refreshFavoriteGridControls();
        refreshPlayerActionsControl();
        refreshPlayerActionChoices(true);
        refreshingStyle = true;
        refreshSizeControls(currentStyle());
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
                        "application/octet-stream", "*/*"});
        launchFilePicker(picker, REQUEST_IMPORT_SETTINGS);
    }

    private void chooseRadioPackUpdate(String packId) {
        chooseRadioCatalogImport(null, packId);
    }

    private void chooseRadioCatalogImport(String replaceCatalogId) {
        chooseRadioCatalogImport(replaceCatalogId, null);
    }

    private void chooseRadioCatalogImport(String replaceCatalogId, String expectedPackId) {
        if (radioCatalogBusy || mediaSettingsBusy || settingsTransferBusy) return;
        pendingReplaceCatalogId = replaceCatalogId;
        pendingUpdatePackId = expectedPackId;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed",
                        "application/octet-stream", "*/*"});
        launchFilePicker(picker, REQUEST_IMPORT_RADIO_CATALOG);
    }

    @SuppressWarnings("deprecation")
    private void launchFilePicker(Intent picker, int requestCode) {
        try {
            startActivityForResult(picker, requestCode);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "В прошивке нет системного выбора файлов",
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
                    String name = datedExportName(FullSettingsBackup.ZIP_FILE_NAME);
                    Uri uri = BackupProvider.publish(appContext, name, fullZip);
                    fullZip.delete();

                    main.post(() -> {
                        settingsTransferBusy = false;
                        if (isDestroyed()) return;
                        setSettingsTransferEnabled(true);
                        setRadioCatalogTransferEnabled(true);
                        shareExport(uri, name, "Экспорт настроек");
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

    static String datedExportName(String fileName) {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())
                + "_" + fileName;
    }

    /** As GInputBridge shares .gibb: the cached export goes out through the share sheet. */
    void shareExport(Uri uri, String name, String title) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(BackupProvider.mimeType(name))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(name, uri));
        try {
            startActivity(Intent.createChooser(send, title)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Нет приложений, которые могут принять файл",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportRadioCatalog(MediaSettingsSnapshot.Catalog catalog) {
        if (radioCatalogBusy || mediaSettingsBusy || settingsTransferBusy || recoveryInProgress
                || importInProgress || mediaBridgeClient == null
                || !mediaBridgeClient.isSettingsSupported()) return;
        radioCatalogBusy = true;
        setMediaSettingsEnabled(false);
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
                mediaBridgeClient.exportRadioCatalog(exportFile, catalog.id,
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
                String name = datedExportName(radioCatalogExportFileName(catalog));
                Uri uri = BackupProvider.publish(appContext, name, exportFile);
                main.post(() -> {
                    if (isDestroyed()) return;
                    shareExport(uri, name, "Экспорт каталога радио");
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
                            setMediaSettingsEnabled(false);
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
        setMediaSettingsEnabled(false);
        setRadioCatalogTransferEnabled(false);
        setSettingsTransferEnabled(false);
        android.content.Context appContext = getApplicationContext();
        String replaceCatalogId = pendingReplaceCatalogId;
        String expectedPackId = pendingUpdatePackId;
        pendingReplaceCatalogId = null;
        pendingUpdatePackId = null;
        ioExecutor.execute(() -> {
            File staged = null;
            try {
                String suggestedName = catalogNameFromFileName(displayName(appContext, uri));
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
                RadioPackPreview pack = RadioPackPreview.read(staged);
                File readyFile = staged;
                main.post(() -> {
                    if (isDestroyed()) {
                        readyFile.delete();
                        radioCatalogBusy = false;
                        return;
                    }
                    pendingRadioImportFile = readyFile;
                    showRadioCatalogImportConfirmation(readyFile, replaceCatalogId,
                            suggestedName, pack, expectedPackId);
                });
            } catch (Exception error) {
                if (staged != null) staged.delete();
                AppLog.warn("Cannot stage radio catalog import", error);
                showRadioCatalogTransferError("Не удалось прочитать каталог радио", error);
            }
        });
    }

    private static String displayName(android.content.Context context, Uri uri) {
        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (RuntimeException ignored) {
            // Some pickers expose no display name; fall back to the path.
        }
        return uri.getLastPathSegment();
    }

    private String packName(String packId) {
        if (currentMediaSettings != null) {
            for (MediaSettingsSnapshot.Catalog catalog : currentMediaSettings.catalogs) {
                if (catalog.packId.equals(packId)) return catalog.name;
            }
        }
        return packId;
    }

    private boolean isPackInstalled(String packId) {
        if (packId == null || packId.isEmpty() || currentMediaSettings == null) return false;
        for (MediaSettingsSnapshot.Catalog catalog : currentMediaSettings.catalogs) {
            if (catalog.packId.equals(packId)) return true;
        }
        return false;
    }

    private void showRadioPackImportConfirmation(File stagedFile, RadioPackPreview pack) {
        boolean update = isPackInstalled(pack.id);
        String name = pack.name.isEmpty() ? "без названия" : pack.name;
        String size = plural(pack.cityCount, "город", "города", "городов")
                + (pack.regionCount > 1 ? " в " + pack.regionCount + " регионах" : "") + ".";
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle((update ? "Обновить набор «" : "Загрузить набор «") + name + "»?")
                .setMessage(size + (update
                        ? " Города и обложки набора будут заменены новой версией. Выбранный "
                                + "город останется выбранным, если он есть в новой версии."
                        : " Остальные каталоги сохранятся. После загрузки выберите свой город."))
                .setOnCancelListener(d -> finishRadioCatalogImport(stagedFile))
                .setNegativeButton("Отмена", (d, w) -> finishRadioCatalogImport(stagedFile))
                .setPositiveButton(update ? "Обновить" : "Загрузить",
                        (d, w) -> importRadioCatalog(stagedFile, null, null)));
    }

    private MediaSettingsSnapshot.Catalog catalogById(String id) {
        if (id == null || currentMediaSettings == null) return null;
        for (MediaSettingsSnapshot.Catalog catalog : currentMediaSettings.catalogs) {
            if (catalog.id.equals(id)) return catalog;
        }
        return null;
    }

    private void showRadioCatalogImportConfirmation(File stagedFile, String replaceCatalogId,
            String suggestedName, RadioPackPreview pack, String expectedPackId) {
        if (expectedPackId != null && (pack == null || !expectedPackId.equals(pack.id))) {
            finishRadioCatalogImport(stagedFile);
            Toast.makeText(this, "Выбранный файл — не набор «" + packName(expectedPackId) + "»",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (pack != null) {
            if (replaceCatalogId != null) {
                finishRadioCatalogImport(stagedFile);
                Toast.makeText(this, "Набор нельзя загрузить вместо отдельного каталога",
                        Toast.LENGTH_LONG).show();
                return;
            }
            showRadioPackImportConfirmation(stagedFile, pack);
            return;
        }
        MediaSettingsSnapshot.Catalog replaced = catalogById(replaceCatalogId);
        if (replaceCatalogId != null && replaced != null) {
            CompactDialog.show(new AlertDialog.Builder(this)
                    .setTitle("Заменить каталог «" + replaced.name + "»?")
                    .setMessage("Станции и обложки каталога будут полностью заменены "
                            + "содержимым выбранного ZIP-файла. Название сохранится.")
                    .setOnCancelListener(d -> finishRadioCatalogImport(stagedFile))
                    .setNegativeButton("Отмена", (d, w) -> finishRadioCatalogImport(stagedFile))
                    .setPositiveButton("Заменить", (d, w) ->
                            importRadioCatalog(stagedFile, null, replaced.id)));
            return;
        }
        EditText input = catalogNameInput(suggestedName);
        input.setHint("Название каталога");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(text("Новый каталог станет активным. Остальные каталоги сохранятся.",
                13, Ui.SECONDARY, Typeface.NORMAL));
        LinearLayout.LayoutParams inputParams = fullWrap();
        inputParams.topMargin = Ui.dp(this, 8);
        content.addView(input, inputParams);
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Добавить каталог радио")
                .setView(paddedDialogView(content))
                .setOnCancelListener(d -> finishRadioCatalogImport(stagedFile))
                .setNegativeButton("Отмена", (d, w) -> finishRadioCatalogImport(stagedFile))
                .setPositiveButton("Добавить", (d, w) -> importRadioCatalog(stagedFile,
                        input.getText().toString().trim(), null)));
    }

    private void importRadioCatalog(File stagedFile, String name, String replaceCatalogId) {
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
                final int[] catalogCount = new int[1];
                final String[] pack = new String[2];
                final String[] importedId = new String[1];
                final String[] errorHolder = new String[1];
                bridge.importRadioCatalog(stagedFile, name, replaceCatalogId,
                        new MediaBridgeClient.RadioCatalogImportCallback() {
                            @Override public void onCatalogImported(int count, String catalogId,
                                    String packId, String packName, int catalogs) {
                                stationCount[0] = count;
                                catalogCount[0] = catalogs;
                                pack[0] = packId;
                                pack[1] = packName;
                                importedId[0] = catalogId;
                                success[0] = true;
                                latch.countDown();
                            }

                            @Override public void onError(int code, String message) {
                                errorHolder[0] = message;
                                latch.countDown();
                            }
                        });
                latch.await(MediaBridgeClient.RADIO_IMPORT_TIMEOUT_MS + 2_000L,
                        TimeUnit.MILLISECONDS);
                if (!success[0]) {
                    throw new IOException("Ошибка импорта каталога радио: "
                            + (errorHolder[0] != null ? errorHolder[0] : "таймаут"));
                }
                main.post(() -> {
                    if (isDestroyed()) return;
                    String packId = pack[0] == null ? "" : pack[0];
                    String message;
                    if (!packId.isEmpty()) {
                        boolean update = isPackInstalled(packId);
                        // A new pack leaves the active catalog as it was: offer its cities.
                        if (!update) pendingCityPickerCatalogId = importedId[0];
                        message = (update ? "Набор «" + pack[1] + "» обновлён: "
                                : "Набор «" + pack[1] + "» загружен: ")
                                + plural(catalogCount[0], "город", "города", "городов");
                    } else {
                        message = (replaceCatalogId != null ? "Каталог заменён ("
                                : "Каталог добавлен (") + stationCountLabel(stationCount[0]) + ")";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
                            setMediaSettingsEnabled(false);
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
            setMediaSettingsEnabled(false);
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

    private void buildMediaTab(LinearLayout page) {
        LinearLayout statusCard = card();
        statusCard.addView(text("Медиасервис OneOS", 20, Ui.PRIMARY, Typeface.BOLD));
        mediaStatusText = text("Подключение к медиасервису…", 13, Ui.SECONDARY, Typeface.NORMAL);
        mediaStatusText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams statusParams = fullWrap();
        statusParams.topMargin = Ui.dp(this, 8);
        statusCard.addView(mediaStatusText, statusParams);
        notificationAccessButton = actionButton("Разрешить доступ к уведомлениям (медиа)");
        notificationAccessButton.setOnClickListener(v -> openNotificationAccessSettings());
        statusCard.addView(notificationAccessButton, buttonParams());
        storageAccessButton = actionButton("Разрешить доступ к хранилищу (USB)");
        storageAccessButton.setOnClickListener(v -> requestStorageAccess());
        statusCard.addView(storageAccessButton, buttonParams());
        page.addView(statusCard);

        LinearLayout startupCard = card();
        startupCard.addView(text("Источник при запуске", 20, Ui.PRIMARY, Typeface.BOLD));
        LinearLayout startup = mediaSettingsBody(startupCard);
        defaultSourceTitle = text("Источник звука по умолчанию", 15, Ui.SECONDARY, Typeface.BOLD);
        startup.addView(defaultSourceTitle, labelParams());

        LinearLayout row1 = createSourceTileRow(new String[][]{
                {"Отключено", ""},
                {"Radio", "RADIO"},
                {"Bluetooth", "BT"}
        });
        LinearLayout.LayoutParams r1Params = fullWrap();
        r1Params.topMargin = Ui.dp(this, 8);
        startup.addView(row1, r1Params);

        LinearLayout row2 = createSourceTileRow(new String[][]{
                {"USB", "USB"},
                {"Online", "ONLINE"},
                {"CarPlay", "CPAA"}
        });
        LinearLayout.LayoutParams r2Params = fullWrap();
        r2Params.topMargin = Ui.dp(this, 6);
        startup.addView(row2, r2Params);

        TextView delayTitle = text("Задержка переключения на старте", 15, Ui.SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams dtParams = fullWrap();
        dtParams.topMargin = Ui.dp(this, 14);
        startup.addView(delayTitle, dtParams);

        defaultSourceDelayLabel = text("0 сек", 18, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams dslParams = fullWrap();
        dslParams.topMargin = Ui.dp(this, 4);
        startup.addView(defaultSourceDelayLabel, dslParams);

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
        startup.addView(defaultSourceDelaySeekBar, fullWrap());

        startupAutoplaySwitch = mediaSwitch(startup, "Автовоспроизведение при старте", 12,
                MediaBridgeContract.K_DEFAULT_AUDIO_SOURCE_AUTOPLAY);
        startupInternetSwitch = mediaSwitch(startup, "Ждать подключения к интернету", 12,
                MediaBridgeContract.K_STARTUP_WAIT_FOR_INTERNET);
        TextView internetNote = text("Работает вместе с автовоспроизведением: переключение и "
                        + "запуск при старте начнутся, только когда появится интернет (Wi-Fi, "
                        + "точка доступа телефона или связь машины). Если за отведённое время его "
                        + "нет, автозапуск пропускается.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams internetNoteParams = fullWrap();
        internetNoteParams.topMargin = Ui.dp(this, 4);
        startup.addView(internetNote, internetNoteParams);
        startupInternetTimeoutLabel = text("Ждать не дольше 60 сек", 15, Ui.PRIMARY,
                Typeface.BOLD);
        LinearLayout.LayoutParams internetLabelParams = fullWrap();
        internetLabelParams.topMargin = Ui.dp(this, 10);
        startup.addView(startupInternetTimeoutLabel, internetLabelParams);
        startupInternetTimeoutSeekBar = sizeSeekBar(
                MediaSettingsSnapshot.MIN_STARTUP_INTERNET_TIMEOUT_SEC / 10,
                MediaSettingsSnapshot.MAX_STARTUP_INTERNET_TIMEOUT_SEC / 10);
        startupInternetTimeoutSeekBar.setContentDescription("Предел ожидания интернета");
        startupInternetTimeoutSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) setStartupInternetTimeoutLabel(progress * 10);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                sendMediaSettingChange(MediaBridgeContract.K_STARTUP_INTERNET_TIMEOUT_SEC,
                        bar.getProgress() * 10);
            }
        });
        startup.addView(startupInternetTimeoutSeekBar, fullWrap());
        page.addView(startupCard);

        LinearLayout onlineCard = card();
        onlineCard.addView(text("Онлайн-плеер", 20, Ui.PRIMARY, Typeface.BOLD));
        LinearLayout online = mediaSettingsBody(onlineCard);
        onlinePlayerTitle = text("Плеер по умолчанию", 15, Ui.SECONDARY, Typeface.BOLD);
        online.addView(onlinePlayerTitle, labelParams());

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
        online.addView(onlinePlayerSpinner, onlineSpinnerParams);

        minimizeOnlinePlayerSwitch = mediaSwitch(online,
                "Сворачивать онлайн-плеер после автозапуска", 10,
                MediaBridgeContract.K_MINIMIZE_ONLINE_PLAYER_AFTER_AUTOSTART);
        page.addView(onlineCard);

        LinearLayout sourceLostCard = card();
        sourceLostCard.addView(text("Потеря источника", 20, Ui.PRIMARY, Typeface.BOLD));
        LinearLayout sourceLost = mediaSettingsBody(sourceLostCard);
        sourceLostSwitch = mediaSwitch(sourceLost, "Автопереключение на источник по умолчанию",
                12, MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT);
        sourceLostAutoplaySwitch = mediaSwitch(sourceLost,
                "Автовоспроизведение после переключения", 12,
                MediaBridgeContract.K_AUTO_SWITCH_TO_DEFAULT_AUTOPLAY);
        page.addView(sourceLostCard);

        LinearLayout broadcastCard = card();
        broadcastCard.addView(text("Приборная панель", 20, Ui.PRIMARY, Typeface.BOLD));
        LinearLayout broadcast = mediaSettingsBody(broadcastCard);
        clusterCoversSwitch = mediaSwitch(broadcast,
                "Трансляция радио (название и обложка)", 12,
                MediaBridgeContract.K_CLUSTER_COVERS_ENABLED);
        clusterOnlineSwitch = mediaSwitch(broadcast,
                "Трансляция онлайн-плеера (название, обложка и прогресс)", 10,
                MediaBridgeContract.K_CLUSTER_ONLINE_ENABLED);

        LinearLayout tuningSpacer = new LinearLayout(this);
        tuningSpacer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tuningParams = fullWrap();
        tuningParams.topMargin = Ui.dp(this, 16);
        broadcastCard.addView(tuningSpacer, tuningParams);
        LinearLayout tuning = collapsible(tuningSpacer, "Тонкая настройка трансляции радио",
                null, 15, Ui.SECONDARY);
        mediaSettingsViews.add(tuning);

        TextView clusterWatchdogTitle = text("Период watchdog", 14, Ui.SECONDARY, Typeface.BOLD);
        tuning.addView(clusterWatchdogTitle, labelParams());

        clusterWatchdogLabel = text("1250 мс", 16, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams cwlParams = fullWrap();
        cwlParams.topMargin = Ui.dp(this, 4);
        tuning.addView(clusterWatchdogLabel, cwlParams);

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
        tuning.addView(clusterWatchdogSeekBar, fullWrap());

        TextView clusterReassertBurstTitle = text("Базовый интервал быстрых повторов", 14, Ui.SECONDARY, Typeface.BOLD);
        tuning.addView(clusterReassertBurstTitle, labelParams());

        clusterReassertBurstLabel = text("100 мс", 16, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams crblParams = fullWrap();
        crblParams.topMargin = Ui.dp(this, 4);
        tuning.addView(clusterReassertBurstLabel, crblParams);

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
        tuning.addView(clusterReassertBurstSeekBar, fullWrap());
        page.addView(broadcastCard);

        // A Widget preference: the foreground service process owns the status bar output.
        LinearLayout statusBarCard = card();
        statusBarCard.addView(text("Строка состояния", 20, Ui.PRIMARY, Typeface.BOLD));
        statusBarMedia = new Switch(this);
        statusBarMedia.setText("Текущее воспроизведение в строке состояния");
        statusBarMedia.setTextColor(Ui.PRIMARY);
        statusBarMedia.setTextSize(15);
        statusBarMedia.setOnCheckedChangeListener((button, checked) -> {
            updateStatusBarFormatControls();
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_STATUS_BAR_MEDIA, checked);
        });
        LinearLayout.LayoutParams statusBarParams = fullWrap();
        statusBarParams.topMargin = Ui.dp(this, 12);
        statusBarCard.addView(statusBarMedia, statusBarParams);
        TextView statusBarHint = text(
                "Во время воспроизведения в плашке строки состояния OneOS появляется трек, "
                        + "для радио — станция или частота. Работает, пока запущен сервис "
                        + "оверлея или на HOME размещён системный виджет.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams statusBarHintParams = fullWrap();
        statusBarHintParams.topMargin = Ui.dp(this, 5);
        statusBarCard.addView(statusBarHint, statusBarHintParams);
        statusBarFormatGroup = new RadioGroup(this);
        statusBarFormatGroup.setOrientation(RadioGroup.VERTICAL);
        statusBarFormatButtons = new RadioButton[StatusBarScene.Format.values().length];
        for (StatusBarScene.Format format : StatusBarScene.Format.values()) {
            RadioButton button = styleButton(format.label);
            button.setOnClickListener(v ->
                    prefs.putInt(Prefs.KEY_STATUS_BAR_FORMAT, format.preferenceValue));
            statusBarFormatButtons[format.preferenceValue] = button;
            statusBarFormatGroup.addView(button, fullWrap());
        }
        LinearLayout.LayoutParams statusBarFormatParams = fullWrap();
        statusBarFormatParams.topMargin = Ui.dp(this, 8);
        statusBarCard.addView(statusBarFormatGroup, statusBarFormatParams);
        TextView statusBarFormatHint = text(
                "Плашка — системное поле OneOS: шрифт, размер, ширину и прокрутку задаёт "
                        + "прошивка, приложение передаёт только текст. Плашка вмещает около "
                        + "10–12 символов; более длинный текст прокручивается бегущей строкой. "
                        + "Обрезанное название не прокручивается.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams statusBarFormatHintParams = fullWrap();
        statusBarFormatHintParams.topMargin = Ui.dp(this, 5);
        statusBarCard.addView(statusBarFormatHint, statusBarFormatHintParams);
        page.addView(statusBarCard);
    }

    private void updateStatusBarFormatControls() {
        boolean enabled = statusBarMedia.isChecked();
        statusBarFormatGroup.setAlpha(enabled ? 1f : 0.45f);
        for (RadioButton button : statusBarFormatButtons) button.setEnabled(enabled);
    }

    /** A card body that stays disabled until the Media Bridge delivers its settings. */
    private LinearLayout mediaSettingsBody(LinearLayout card) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(body, fullWrap());
        mediaSettingsViews.add(body);
        return body;
    }

    private Switch mediaSwitch(LinearLayout parent, String label, int topMarginDp, String key) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextColor(Ui.PRIMARY);
        view.setTextSize(15);
        view.setOnCheckedChangeListener((btn, checked) -> {
            if (!btn.isPressed()) return;
            sendMediaSettingChange(key, checked);
        });
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, topMarginDp);
        parent.addView(view, params);
        return view;
    }

    private void setMediaSettingsEnabled(boolean enabled) {
        for (View view : mediaSettingsViews) setMediaControlsEnabled(view, enabled);
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

    private LinearLayout createRadioCatalogCard() {
        LinearLayout radioCard = card();
        radioCard.addView(text("Каталоги станций", 20, Ui.PRIMARY, Typeface.BOLD));
        LinearLayout catalog = mediaSettingsBody(radioCard);
        radioWidgetBroadcastSwitch = mediaSwitch(catalog,
                "Названия и обложки из каталога в карточке", 12,
                MediaBridgeContract.K_RADIO_WIDGET_BROADCAST_ENABLED);
        radioCatalogInfoText = text("Каталог: ...", 13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams rciParams = fullWrap();
        rciParams.topMargin = Ui.dp(this, 12);
        catalog.addView(radioCatalogInfoText, rciParams);
        radioCatalogList = new LinearLayout(this);
        radioCatalogList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = fullWrap();
        listParams.topMargin = Ui.dp(this, 4);
        catalog.addView(radioCatalogList, listParams);

        TextView hint = text(
                "Активен один каталог: нажмите на нужный, чтобы переключиться. Набор городов "
                        + "занимает одну строку — нажмите на неё, чтобы выбрать город. В меню «⋯» "
                        + "— экспорт, обновление и удаление. Отдельный ZIP со stations.csv и "
                        + "обложками добавляется как свой каталог; одинаковые обложки хранятся "
                        + "один раз.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams hintParams = fullWrap();
        hintParams.topMargin = Ui.dp(this, 14);
        radioCard.addView(hint, hintParams);

        importRadioCatalogButton = actionButton("Добавить каталог или набор (ZIP)");
        importRadioCatalogButton.setOnClickListener(v -> chooseRadioCatalogImport(null));
        radioCard.addView(importRadioCatalogButton, buttonParams());
        setRadioCatalogTransferEnabled(false);
        return radioCard;
    }

    private void renderRadioCatalogs(MediaSettingsSnapshot snapshot) {
        if (radioCatalogList == null) return;
        radioCatalogList.removeAllViews();
        boolean enabled = !mediaSettingsBusy && !radioCatalogBusy && !settingsTransferBusy;
        for (MediaSettingsSnapshot.Catalog catalog : snapshot.catalogs) {
            boolean active = catalog.id.equals(snapshot.catalogActiveId);
            if (catalog.isPack()) {
                // A pack is one row however many cities it has; its city is chosen in a dialog.
                String subtitle = active
                        ? cityLabel(snapshot.catalogActiveCityName, snapshot.catalogActiveRegion)
                                + " · " + stationCountLabel(snapshot.catalogStationCount)
                                + " · активен"
                        : plural(catalog.cityCount, "город", "города", "городов")
                                + " · нажмите, чтобы выбрать город";
                addRadioCatalogRow(catalog.name, subtitle, active, enabled,
                        () -> openRadioCityPicker(catalog),
                        () -> showRadioPackActions(catalog, active));
            } else {
                addRadioCatalogRow(catalog.name, stationCountLabel(catalog.stationCount)
                                + (active ? " · активен" : ""), active, enabled,
                        () -> {
                            if (!active) activateRadioCatalog(catalog);
                        },
                        () -> showRadioCatalogActions(catalog, active));
            }
        }
    }

    private void addRadioCatalogRow(String title, String subtitle, boolean active,
            boolean enabled, Runnable onClick, Runnable onMenu) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.background(active ? Ui.ACCENT : Ui.NESTED, 8, this));
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        int primary = active ? Ui.ON_ACCENT : Ui.PRIMARY;

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, primary, active ? Typeface.BOLD : Typeface.NORMAL));
        labels.addView(text(subtitle, 12, active ? Ui.ON_ACCENT : Ui.SECONDARY,
                Typeface.NORMAL));
        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button menu = actionButton("⋯");
        menu.setTextSize(18);
        menu.setTextColor(primary);
        menu.setBackground(null);
        menu.setMinWidth(Ui.dp(this, 48));
        menu.setContentDescription("Действия: " + title);
        menu.setOnClickListener(v -> onMenu.run());
        menu.setEnabled(enabled);
        row.addView(menu, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> onClick.run());
        row.setEnabled(enabled);
        LinearLayout.LayoutParams rowParams = fullWrap();
        rowParams.topMargin = Ui.dp(this, 8);
        radioCatalogList.addView(row, rowParams);
    }

    private void activateRadioCatalog(MediaSettingsSnapshot.Catalog catalog) {
        manageRadioCatalog(MediaBridgeContract.CATALOG_ACTION_ACTIVATE, catalog, null, null,
                "Активен каталог «" + catalog.name + "»");
    }

    /** "Пенза · Пензенская область", or the plain name when there is no region. */
    static String cityLabel(String name, String region) {
        return region == null || region.isEmpty() || region.equals(name)
                ? name : name + " · " + region;
    }

    static String stationCountLabel(int count) {
        return plural(count, "станция", "станции", "станций");
    }

    static String plural(int count, String one, String few, String many) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        String word = mod100 >= 11 && mod100 <= 14 ? many
                : mod10 == 1 ? one
                : mod10 >= 2 && mod10 <= 4 ? few : many;
        return count + " " + word;
    }

    /** Reads the pack's cities from the Media Bridge, then shows the picker. */
    private void openRadioCityPicker(MediaSettingsSnapshot.Catalog pack) {
        if (mediaBridgeClient == null || mediaSettingsBusy || radioCatalogBusy
                || settingsTransferBusy || !mediaBridgeClient.isSettingsSupported()) return;
        radioCatalogBusy = true;
        setMediaSettingsEnabled(false);
        setRadioCatalogTransferEnabled(false);
        setSettingsTransferEnabled(false);
        MediaBridgeClient bridge = mediaBridgeClient;
        ioExecutor.execute(() -> {
            File file = new File(getCacheDir(), "radio_pack_cities.json");
            List<RadioPackCities.City> cities = null;
            Exception failure = null;
            try {
                CountDownLatch latch = new CountDownLatch(1);
                String[] error = new String[1];
                boolean[] success = new boolean[1];
                bridge.readPackCities(file, pack.id, new MediaBridgeClient.PackCitiesCallback() {
                    @Override public void onPackCities(int count) {
                        success[0] = true;
                        latch.countDown();
                    }

                    @Override public void onError(int code, String message) {
                        error[0] = message;
                        latch.countDown();
                    }
                });
                latch.await(17, TimeUnit.SECONDS);
                if (!success[0]) {
                    throw new IOException("Не удалось прочитать города набора: "
                            + (error[0] != null ? error[0] : "таймаут"));
                }
                cities = RadioPackCities.parse(new String(java.nio.file.Files.readAllBytes(
                        file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception error) {
                failure = error;
                AppLog.warn("Cannot read radio pack cities", error);
            } finally {
                file.delete();
            }
            List<RadioPackCities.City> loaded = cities;
            Exception problem = failure;
            main.post(() -> {
                radioCatalogBusy = false;
                if (isDestroyed()) return;
                if (currentMediaSettings != null) updateMediaSettingsUi(currentMediaSettings);
                setRadioCatalogTransferEnabled(true);
                setSettingsTransferEnabled(true);
                if (loaded != null) {
                    showRadioCityPicker(pack, loaded);
                } else {
                    Toast.makeText(this, problem != null && problem.getMessage() != null
                                    ? problem.getMessage() : "Не удалось прочитать города набора",
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * Cities grouped by region, each region collapsed except the active city's; a search field
     * lists matching cities across all regions.
     */
    private void showRadioCityPicker(MediaSettingsSnapshot.Catalog pack,
            List<RadioPackCities.City> cities) {
        MediaSettingsSnapshot snapshot = currentMediaSettings;
        if (snapshot == null || cities.isEmpty()) return;
        String activeKey = pack.id.equals(snapshot.catalogActiveId)
                ? snapshot.catalogActiveCityKey : "";
        Map<String, List<RadioPackCities.City>> regions = RadioPackCities.byRegion(cities);
        boolean expandAll = cities.size() <= 8 || regions.size() == 1;
        AlertDialog[] dialog = new AlertDialog[1];

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 20);
        content.setPadding(side, Ui.dp(this, 4), side, 0);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Найти город");
        search.setTextColor(Ui.PRIMARY);
        content.addView(search, fullWrap());
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 8));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 420)));

        java.util.function.Function<RadioPackCities.City, View> cityRow = city -> {
            boolean active = city.key.equals(activeKey);
            TextView row = text(city.name + "  ·  " + stationCountLabel(city.stationCount),
                    15, active ? Ui.ON_ACCENT : Ui.PRIMARY,
                    active ? Typeface.BOLD : Typeface.NORMAL);
            row.setBackground(Ui.background(active ? Ui.ACCENT : Ui.NESTED, 8, this));
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            row.setOnClickListener(v -> {
                if (dialog[0] != null) dialog[0].dismiss();
                if (!active) {
                    manageRadioCatalog(MediaBridgeContract.CATALOG_ACTION_ACTIVATE, pack, null,
                            city.key, "Активен город «" + cityLabel(city.name, city.region) + "»");
                }
            });
            return row;
        };
        Runnable showRegions = () -> {
            list.removeAllViews();
            for (Map.Entry<String, List<RadioPackCities.City>> region : regions.entrySet()) {
                LinearLayout group = new LinearLayout(this);
                group.setOrientation(LinearLayout.VERTICAL);
                boolean containsActive = false;
                for (RadioPackCities.City city : region.getValue()) {
                    if (city.key.equals(activeKey)) containsActive = true;
                }
                // Rows of a region are built when it opens: a pack can hold thousands of cities.
                Runnable fill = () -> {
                    if (group.getChildCount() > 0) return;
                    for (RadioPackCities.City city : region.getValue()) {
                        LinearLayout.LayoutParams params = fullWrap();
                        params.topMargin = Ui.dp(this, 6);
                        group.addView(cityRow.apply(city), params);
                    }
                };
                if (region.getKey().isEmpty()) {
                    fill.run();
                } else {
                    TextView header = text("", 15, Ui.PRIMARY, Typeface.BOLD);
                    header.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
                    String title = region.getKey() + " · " + region.getValue().size();
                    boolean open = expandAll || containsActive;
                    group.setVisibility(open ? View.VISIBLE : View.GONE);
                    if (open) fill.run();
                    header.setText((open ? "▾ " : "▸ ") + title);
                    header.setOnClickListener(v -> {
                        boolean show = group.getVisibility() != View.VISIBLE;
                        if (show) fill.run();
                        group.setVisibility(show ? View.VISIBLE : View.GONE);
                        header.setText((show ? "▾ " : "▸ ") + title);
                    });
                    list.addView(header, fullWrap());
                }
                list.addView(group, fullWrap());
            }
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable value) {
                String query = value.toString().trim();
                if (query.isEmpty()) {
                    showRegions.run();
                    return;
                }
                list.removeAllViews();
                List<RadioPackCities.City> found = RadioPackCities.search(cities, query,
                        RadioPackCities.MAX_SEARCH_RESULTS);
                if (found.isEmpty()) {
                    list.addView(text("Ничего не найдено", 14, Ui.SECONDARY, Typeface.NORMAL));
                }
                for (RadioPackCities.City city : found) {
                    View row = cityRow.apply(city);
                    if (!city.region.isEmpty() && row instanceof TextView label) {
                        label.setText(city.name + "  ·  " + city.region + "  ·  "
                                + stationCountLabel(city.stationCount));
                    }
                    LinearLayout.LayoutParams params = fullWrap();
                    params.topMargin = Ui.dp(MainActivity.this, 6);
                    list.addView(row, params);
                }
            }
        });
        showRegions.run();
        dialog[0] = CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle(pack.name + ": " + plural(cities.size(), "город", "города", "городов")
                        + (regions.size() > 1 ? " в " + regions.size() + " регионах" : ""))
                .setView(content)
                .setNegativeButton("Закрыть", null));
    }

    private void showRadioPackActions(MediaSettingsSnapshot.Catalog pack, boolean active) {
        if (mediaSettingsBusy || radioCatalogBusy || settingsTransferBusy) return;
        String[] labels = {"Выбрать город", "Обновить из ZIP", "Экспортировать набор (ZIP)",
                "Удалить набор"};
        Runnable[] actions = {
                () -> openRadioCityPicker(pack),
                () -> chooseRadioPackUpdate(pack.packId),
                () -> exportRadioCatalog(pack),
                () -> confirmDeleteRadioPack(pack, active),
        };
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle(pack.name)
                .setItems(labels, (d, which) -> actions[which].run())
                .setNegativeButton("Отмена", null));
    }

    private void confirmDeleteRadioPack(MediaSettingsSnapshot.Catalog pack, boolean active) {
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Удалить набор «" + pack.name + "»?")
                .setMessage(plural(pack.cityCount, "город", "города", "городов")
                        + " и их обложки будут удалены."
                        + (active ? " Активным станет встроенный каталог." : ""))
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d, w) -> manageRadioCatalog(
                        MediaBridgeContract.CATALOG_ACTION_DELETE, pack, null, null,
                        "Набор «" + pack.name + "» удалён")));
    }

    private void showRadioCatalogActions(MediaSettingsSnapshot.Catalog catalog, boolean active) {
        if (mediaSettingsBusy || radioCatalogBusy || settingsTransferBusy) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!active) {
            labels.add("Сделать активным");
            actions.add(() -> activateRadioCatalog(catalog));
        }
        labels.add("Экспортировать (ZIP)");
        actions.add(() -> exportRadioCatalog(catalog));
        if (!catalog.isBuiltIn()) {
            labels.add("Заменить из ZIP");
            actions.add(() -> chooseRadioCatalogImport(catalog.id));
            labels.add("Переименовать");
            actions.add(() -> showRenameRadioCatalog(catalog));
            labels.add("Удалить");
            actions.add(() -> confirmDeleteRadioCatalog(catalog, active));
        }
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle(catalog.name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Отмена", null));
    }

    private EditText catalogNameInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setSelectAllOnFocus(true);
        input.setTextColor(Ui.PRIMARY);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(MAX_CATALOG_NAME_LENGTH)});
        return input;
    }

    private View paddedDialogView(View content) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        int padding = Ui.dp(this, 20);
        frame.setPadding(padding, Ui.dp(this, 8), padding, 0);
        frame.addView(content);
        return frame;
    }

    private void showRenameRadioCatalog(MediaSettingsSnapshot.Catalog catalog) {
        EditText input = catalogNameInput(catalog.name);
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Переименовать каталог")
                .setView(paddedDialogView(input))
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.equals(catalog.name)) return;
                    manageRadioCatalog(MediaBridgeContract.CATALOG_ACTION_RENAME, catalog, name,
                            null, "Каталог переименован");
                }));
    }

    private void confirmDeleteRadioCatalog(MediaSettingsSnapshot.Catalog catalog, boolean active) {
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Удалить каталог «" + catalog.name + "»?")
                .setMessage("Станции и обложки этого каталога будут удалены."
                        + (active ? " Активным станет встроенный каталог." : ""))
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d, w) -> manageRadioCatalog(
                        MediaBridgeContract.CATALOG_ACTION_DELETE, catalog, null, null,
                        "Каталог «" + catalog.name + "» удалён")));
    }

    private void manageRadioCatalog(String action, MediaSettingsSnapshot.Catalog catalog,
            String name, String cityKey, String successMessage) {
        if (mediaBridgeClient == null || mediaSettingsBusy || radioCatalogBusy
                || settingsTransferBusy || !mediaBridgeClient.isSettingsSupported()) return;
        mediaSettingsBusy = true;
        setMediaSettingsEnabled(false);
        setSettingsTransferEnabled(false);
        setRadioCatalogTransferEnabled(false);
        mediaBridgeClient.manageRadioCatalog(action, catalog.id, name, cityKey,
                new MediaBridgeClient.ManageCatalogCallback() {
                    @Override public void onCatalogManaged(MediaSettingsSnapshot snapshot) {
                        mediaSettingsBusy = false;
                        if (isDestroyed()) return;
                        currentMediaSettings = snapshot;
                        updateMediaSettingsUi(snapshot);
                        setSettingsTransferEnabled(true);
                        Toast.makeText(MainActivity.this, successMessage,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onError(int code, String message) {
                        mediaSettingsBusy = false;
                        if (isDestroyed()) return;
                        AppLog.warn("Radio catalog " + action + " failed: " + code + " "
                                + message, null);
                        setSettingsTransferEnabled(true);
                        loadMediaSettings();
                        Toast.makeText(MainActivity.this, message == null || message.isBlank()
                                        ? "Не удалось изменить каталог" : message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** The share-sheet name of an exported catalog, before the date prefix. */
    static String radioCatalogExportFileName(MediaSettingsSnapshot.Catalog catalog) {
        if (catalog == null || catalog.isBuiltIn()) return RADIO_ZIP_FILE_NAME;
        String safe = catalog.name.replaceAll("[^\\p{L}\\p{N}._-]+", "_")
                .replaceAll("^[._]+|_+$", "");
        if (safe.length() > MAX_CATALOG_NAME_LENGTH) {
            safe = safe.substring(0, MAX_CATALOG_NAME_LENGTH);
        }
        return safe.isEmpty() ? RADIO_ZIP_FILE_NAME
                : RADIO_ZIP_FILE_NAME.replace(".zip", "-" + safe + ".zip");
    }

    /** Suggests a catalog name from a picked file, dropping our own export decoration. */
    static String catalogNameFromFileName(String displayName) {
        if (displayName == null) return "";
        String name = displayName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.replaceFirst("^\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_", "");
        String base = RADIO_ZIP_FILE_NAME.substring(0, RADIO_ZIP_FILE_NAME.length() - 4);
        if (name.equals(base)) return "";
        if (name.startsWith(base + "-")) name = name.substring(base.length() + 1);
        name = name.replace('_', ' ').trim();
        return name.length() > MAX_CATALOG_NAME_LENGTH
                ? name.substring(0, MAX_CATALOG_NAME_LENGTH).trim() : name;
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
        setMediaSettingsEnabled(false);
        setSettingsTransferEnabled(false);
        mediaBridgeClient.getSettings(new MediaBridgeClient.SettingsCallback() {
            @Override public void onSettings(MediaSettingsSnapshot snapshot) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                currentMediaSettings = snapshot;
                updateMediaSettingsUi(snapshot);
                setSettingsTransferEnabled(true);
                MediaSettingsSnapshot.Catalog pickerPack = catalogById(pendingCityPickerCatalogId);
                pendingCityPickerCatalogId = null;
                if (pickerPack != null && pickerPack.isPack()) openRadioCityPicker(pickerPack);
            }
            @Override public void onError(int code, String message) {
                mediaSettingsBusy = false;
                if (isDestroyed()) return;
                mediaStatusText.setText("Не удалось загрузить медианастройки: " + message);
                mediaStatusText.setTextColor(Ui.ERROR);
                setMediaSettingsEnabled(false);
                setSettingsTransferEnabled(true);
                AppLog.warn("Failed to load media settings: " + code + " " + message, null);
            }
        });
    }

    private void updateMediaSettingsUi(MediaSettingsSnapshot snapshot) {
        setMediaSettingsEnabled(true);
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
        boolean autoplay = snapshot.defaultAudioSourceAutoplayOnStartup;
        if (startupInternetSwitch != null) {
            startupInternetSwitch.setChecked(snapshot.startupWaitForInternet);
            startupInternetSwitch.setEnabled(autoplay);
            startupInternetSwitch.setAlpha(autoplay ? 1f : 0.45f);
        }
        if (startupInternetTimeoutSeekBar != null) {
            boolean waits = autoplay && snapshot.startupWaitForInternet;
            setStartupInternetTimeoutLabel(snapshot.startupInternetTimeoutSec);
            startupInternetTimeoutSeekBar.setProgress(snapshot.startupInternetTimeoutSec / 10);
            startupInternetTimeoutSeekBar.setEnabled(waits);
            startupInternetTimeoutSeekBar.setAlpha(waits ? 1f : 0.45f);
            startupInternetTimeoutLabel.setAlpha(waits ? 1f : 0.45f);
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
        if (radioWidgetBroadcastSwitch != null) {
            radioWidgetBroadcastSwitch.setChecked(snapshot.radioWidgetBroadcastEnabled);
        }
        if (clusterCoversSwitch != null) {
            clusterCoversSwitch.setChecked(snapshot.clusterCoversEnabled);
        }
        if (clusterOnlineSwitch != null) {
            clusterOnlineSwitch.setChecked(snapshot.clusterOnlineEnabled);
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
            MediaSettingsSnapshot.Catalog active = snapshot.activeCatalog();
            String activeName = active == null ? null : active.isPack()
                    ? cityLabel(snapshot.catalogActiveCityName, snapshot.catalogActiveRegion)
                            + " (" + active.name + ")"
                    : active.name;
            radioCatalogInfoText.setText(activeName != null
                    ? "Активный каталог: «" + activeName + "», "
                            + stationCountLabel(snapshot.catalogStationCount)
                    : snapshot.catalogDescription);
        }
        renderRadioCatalogs(snapshot);
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
        setMediaSettingsEnabled(false);
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

    private void setStartupInternetTimeoutLabel(int seconds) {
        if (startupInternetTimeoutLabel == null) return;
        startupInternetTimeoutLabel.setText(seconds < 60 ? "Ждать не дольше " + seconds + " сек"
                : seconds % 60 == 0 ? "Ждать не дольше " + seconds / 60 + " мин"
                : "Ждать не дольше " + seconds / 60 + " мин " + seconds % 60 + " сек");
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
                setMediaSettingsEnabled(false);
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
        return CardStyle.fromPreference(prefs.getInt(Prefs.KEY_CARD_STYLE, CardStyle.DEFAULT.preferenceValue));
    }

    private void refreshSizeControls(CardStyle style) {
        if (widthSize == null || heightSize == null || metadataProgressGap == null
                || controlPanelHeight == null || controlIconScale == null
                || controlSpread == null || controlBottomInset == null
                || topInsetSetting == null || coverDimPresetButtons == null
                || thumbnailSizeSetting == null || backdropSection == null
                || cornerRadiusSetting == null) return;
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
        thumbnailSizeSetting.seek.setProgress(appearance.thumbnailSizeDp);
        thumbnailSizeSection.setVisibility(style == CardStyle.COMPACT ? View.VISIBLE : View.GONE);
        cornerRadiusSetting.seek.setProgress(appearance.cornerRadiusDp);
        coverDimPresetButtons[appearance.coverDimPreset.preferenceValue].setChecked(true);
        backdropSection.setVisibility(style == CardStyle.COMPACT ? View.VISIBLE : View.GONE);
        backdropColor = appearance.backdrop.color;
        backdropAlpha = appearance.backdrop.alpha;
        artworkAlpha = appearance.backdrop.artworkAlpha;
        contentAlpha = appearance.backdrop.contentAlpha;
        backdropOpaqueControls.setChecked(appearance.backdrop.opaqueControls);
        backdropOpaqueTopRow.setChecked(appearance.backdrop.opaqueTopRow);
        backdropDayColor = appearance.backdrop.dayColor;
        backdropDayAlpha = appearance.backdrop.dayAlpha;
        (appearance.backdrop.solid ? backdropSolidButton : backdropArtworkButton)
                .setChecked(true);
        updateBackdropControls();
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

    /**
     * The card's source chooser: head-unit sources and Android players in the user's order, up
     * to {@link SourceTile#MAX_TILES}. A player tile switches Online to that app.
     */
    private LinearLayout createSourceTilesCard() {
        LinearLayout card = card();
        card.addView(text("Источники на карточке", 20, Ui.PRIMARY, Typeface.BOLD));
        TextView hint = text("Плитки, которые открываются нажатием на название источника. "
                        + "Можно убрать ненужные и добавить Android-плееры: плитка плеера "
                        + "переключает Онлайн на это приложение и включает его. Плитка «Онлайн» "
                        + "запускает плеер по умолчанию с вкладки «Медиа». Не больше "
                        + SourceTile.MAX_TILES + " плиток.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = fullWrap();
        hintParams.topMargin = Ui.dp(this, 8);
        card.addView(hint, hintParams);
        sourceTilesList = new LinearLayout(this);
        sourceTilesList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = fullWrap();
        listParams.topMargin = Ui.dp(this, 8);
        card.addView(sourceTilesList, listParams);
        sourceTilesAdd = actionButton("Добавить источник");
        sourceTilesAdd.setOnClickListener(v -> showAddSourceTile());
        card.addView(sourceTilesAdd, buttonParams());
        sourceTilesReset = actionButton("Вернуть стандартный набор");
        sourceTilesReset.setOnClickListener(v -> saveSourceTiles(SourceTile.DEFAULT));
        card.addView(sourceTilesReset, buttonParams());
        playerIconsMono = new Switch(this);
        playerIconsMono.setText("Одноцветные иконки плееров");
        playerIconsMono.setTextColor(Ui.PRIMARY);
        playerIconsMono.setTextSize(15);
        playerIconsMono.setChecked(prefs.getBoolean(Prefs.KEY_PLAYER_ICONS_MONO, false));
        playerIconsMono.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            prefs.putBoolean(Prefs.KEY_PLAYER_ICONS_MONO, checked);
            refreshOverlayIfRunning();
        });
        LinearLayout.LayoutParams monoParams = fullWrap();
        monoParams.topMargin = Ui.dp(this, 14);
        card.addView(playerIconsMono, monoParams);
        TextView monoNote = text("Логотип плеера в цвете значков карточки. Для популярных плееров "
                        + "— готовые значки, для остальных логотип берётся из иконки приложения.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams monoNoteParams = fullWrap();
        monoNoteParams.topMargin = Ui.dp(this, 4);
        card.addView(monoNote, monoNoteParams);
        refreshSourceTiles();
        return card;
    }

    private void refreshSourceTiles() {
        if (sourceTilesList == null) return;
        List<SourceTile> tiles = prefs.sourceTiles();
        sourceTilesList.removeAllViews();
        for (int position = 0; position < tiles.size(); position++) {
            SourceTile tile = tiles.get(position);
            OnlinePlayerOption option = sourceTileOption(tile);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View label = createOnlinePlayerRow(option, false);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView remove = text("✕", 20, Ui.SECONDARY, Typeface.NORMAL);
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("Убрать «" + option.label + "»");
            int size = Ui.dp(this, 48);
            boolean removable = tiles.size() > 1;
            remove.setEnabled(removable);
            remove.setAlpha(removable ? 1f : 0.25f);
            final int index = position;
            remove.setOnClickListener(v -> {
                List<SourceTile> next = new ArrayList<>(prefs.sourceTiles());
                if (next.size() <= 1 || index >= next.size()) return;
                next.remove(index);
                saveSourceTiles(next);
            });
            row.addView(remove, new LinearLayout.LayoutParams(size, size));
            row.addView(playerActionDragHandle(sourceTilesList, row, option.label, index,
                    tiles.size(), to -> {
                        List<SourceTile> next = new ArrayList<>(prefs.sourceTiles());
                        if (index >= next.size() || to >= next.size()) return;
                        next.add(to, next.remove(index));
                        saveSourceTiles(next);
                    }));
            row.setTag(index);
            sourceTilesList.addView(row, fullWrap());
        }
        boolean full = tiles.size() >= SourceTile.MAX_TILES;
        sourceTilesAdd.setEnabled(!full);
        sourceTilesAdd.setAlpha(full ? 0.45f : 1f);
        sourceTilesAdd.setText(full ? "Добавлено " + SourceTile.MAX_TILES + " из "
                + SourceTile.MAX_TILES : "Добавить источник");
        sourceTilesReset.setVisibility(tiles.equals(SourceTile.DEFAULT) ? View.GONE : View.VISIBLE);
    }

    private OnlinePlayerOption sourceTileOption(SourceTile tile) {
        if (!tile.isPlayer()) return new OnlinePlayerOption(tile.wire(), tile.source.label());
        PlayerApps.Info app = PlayerApps.get(this, tile.playerPackage);
        return new OnlinePlayerOption(tile.wire(), app.installed() ? app.label()
                : "Не установлено: " + tile.playerPackage, app.newIcon());
    }

    private void showAddSourceTile() {
        List<SourceTile> tiles = prefs.sourceTiles();
        if (tiles.size() >= SourceTile.MAX_TILES) return;
        List<OnlinePlayerOption> options = new ArrayList<>();
        List<SourceTile> choices = new ArrayList<>();
        for (MediaSource.Id id : SourceTile.NATIVE_SOURCES) {
            SourceTile tile = SourceTile.source(id);
            if (tiles.contains(tile)) continue;
            choices.add(tile);
            options.add(sourceTileOption(tile));
        }
        for (OnlinePlayerOption player : queryOnlinePlayerOptions()) {
            if (!SourceTile.isPackageName(player.packageName)) continue;
            SourceTile tile = SourceTile.player(player.packageName);
            if (tiles.contains(tile)) continue;
            choices.add(tile);
            options.add(player);
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, "Все источники и плееры уже на карточке",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayAdapter<OnlinePlayerOption> adapter = new ArrayAdapter<OnlinePlayerOption>(
                this, android.R.layout.simple_list_item_1, options) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = createOnlinePlayerRow(getItem(position), true);
                row.setPadding(Ui.dp(MainActivity.this, 20), row.getPaddingTop(),
                        Ui.dp(MainActivity.this, 20), row.getPaddingBottom());
                return row;
            }
        };
        CompactDialog.show(new AlertDialog.Builder(this)
                .setTitle("Добавить источник")
                .setAdapter(adapter, (dialog, which) -> {
                    List<SourceTile> next = new ArrayList<>(prefs.sourceTiles());
                    if (next.size() >= SourceTile.MAX_TILES
                            || next.contains(choices.get(which))) return;
                    next.add(choices.get(which));
                    saveSourceTiles(next);
                })
                .setNegativeButton("Отмена", null));
    }

    private void saveSourceTiles(List<SourceTile> tiles) {
        prefs.putSourceTiles(tiles);
        refreshSourceTiles();
        refreshOverlayIfRunning();
    }

    private LinearLayout createPlayerActionsCard() {
        LinearLayout card = card();
        card.addView(text("Кнопки плеера", 20, Ui.PRIMARY, Typeface.BOLD));
        TextView hint = text("Собственные кнопки активного плеера (лайк, повтор и т. п.) "
                        + "в правом верхнем углу карточки. "
                        + "Ползунок задаёт, сколько кнопок помещать на карточку. Ниже можно "
                        + "скрыть и переставить кнопки играющего плеера — для каждого "
                        + "приложения отдельно. Если места меньше, лишние кнопки не "
                        + "показываются.",
                13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = fullWrap();
        hintParams.topMargin = Ui.dp(this, 8);
        card.addView(hint, hintParams);
        playerActionsValue = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        card.addView(playerActionsValue, labelParams());
        playerActions = sizeSeekBar(Prefs.MIN_PLAYER_ACTIONS, Prefs.MAX_PLAYER_ACTIONS);
        playerActions.setContentDescription("Количество кнопок плеера");
        playerActions.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updatePlayerActionsLabel();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.putPlayerActionsCount(seekBar.getProgress());
                refreshPlayerActionChoices(true);
                refreshOverlayIfRunning();
            }
        });
        card.addView(playerActions, fullWrap());

        playerActionsPlayer = text("", 16, Ui.PRIMARY, Typeface.BOLD);
        LinearLayout.LayoutParams playerParams = fullWrap();
        playerParams.topMargin = Ui.dp(this, 16);
        card.addView(playerActionsPlayer, playerParams);
        playerActionsList = new LinearLayout(this);
        playerActionsList.setOrientation(LinearLayout.VERTICAL);
        card.addView(playerActionsList, fullWrap());
        playerActionsReset = actionButton("Сбросить кнопки этого плеера");
        playerActionsReset.setOnClickListener(v -> {
            MediaSnapshot snapshot = playerActionsSnapshot;
            if (snapshot == null || snapshot.ownerPackage.isBlank()) return;
            List<String> ids = actionIds(snapshot.customActions);
            prefs.putPlayerActionRules(snapshot.ownerPackage, ids, List.of(), ids);
            refreshPlayerActionChoices(true);
            refreshOverlayIfRunning();
        });
        LinearLayout.LayoutParams resetParams = fullWrap();
        resetParams.topMargin = Ui.dp(this, 8);
        card.addView(playerActionsReset, resetParams);
        refreshPlayerActionsControl();
        return card;
    }

    /**
     * Lists the playing player's own buttons in the card's order: unchecking one hides it for that
     * player, the arrows move it. Rows are rebuilt only when the player, its buttons or the rules
     * change.
     */
    private void refreshPlayerActionChoices(boolean force) {
        if (playerActionsList == null) return;
        MediaSnapshot snapshot = playerActionsSnapshot;
        List<MediaCustomAction> actions = snapshot == null ? List.of() : snapshot.customActions;
        String owner = actions.isEmpty() ? "" : snapshot.ownerPackage;
        PlayerActionFilter.Rules rules = prefs.playerActionRules();
        List<MediaCustomAction> arranged = rules.arrange(actions);
        List<MediaCustomAction> hidden = rules.hiddenOf(actions);
        int limit = prefs.playerActionsCount();
        List<Object> key = new ArrayList<>();
        key.add(owner);
        key.add(limit);
        for (MediaCustomAction action : arranged) {
            key.add(List.of(action.action, action.label(), action.iconResId,
                    hidden.contains(action)));
        }
        if (!force && key.equals(playerActionsKey)) return;
        playerActionsKey = key;
        playerActionsList.removeAllViews();
        playerActionsReset.setVisibility(rules.configured(owner) ? View.VISIBLE : View.GONE);
        if (actions.isEmpty()) {
            playerActionsPlayer.setText("Настройка кнопок");
            playerActionsList.addView(playerActionsNote(snapshot == null
                    || snapshot.ownerPackage.isBlank()
                    ? "Включите музыку в плеере, чтобы настроить его кнопки."
                    : (snapshot.ownerApp.isBlank() ? snapshot.ownerPackage
                            : snapshot.ownerApp) + " не публикует своих кнопок."));
            return;
        }
        playerActionsPlayer.setText(snapshot.ownerApp.isBlank() ? owner : snapshot.ownerApp);
        List<MediaCustomAction> shown = rules.choose(actions, limit);
        int iconSize = Ui.dp(this, 24);
        for (int position = 0; position < arranged.size(); position++) {
            MediaCustomAction action = arranged.get(position);
            boolean visible = !hidden.contains(action);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox box = new CheckBox(this);
            box.setText(visible && !shown.contains(action)
                    ? action.label() + " — не помещается" : action.label());
            box.setTextSize(15);
            box.setTextColor(visible && shown.contains(action) ? Ui.PRIMARY : Ui.SECONDARY);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            box.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), 0, Ui.dp(this, 6));
            box.setCompoundDrawablePadding(Ui.dp(this, 12));
            android.graphics.drawable.Drawable icon = CustomActionIcons.load(this, action);
            if (icon != null) {
                icon.setBounds(0, 0, iconSize, iconSize);
                icon.setTint(visible ? Ui.PRIMARY : Ui.SECONDARY);
                box.setCompoundDrawablesRelative(icon, null, null, null);
            }
            box.setChecked(visible);
            box.setOnCheckedChangeListener((button, checked) -> {
                if (!button.isPressed()) return;
                List<MediaCustomAction> nextHidden = new ArrayList<>(hidden);
                nextHidden.remove(action);
                if (!checked) nextHidden.add(action);
                savePlayerActionRules(owner, actions, nextHidden, arranged);
            });
            row.addView(box, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final int index = position;
            row.addView(playerActionDragHandle(playerActionsList, row, action.label(), index,
                    arranged.size(),
                    to -> movePlayerAction(owner, actions, hidden, arranged, index, to)));
            row.setTag(index);
            playerActionsList.addView(row, fullWrap());
        }
        playerActionsList.addView(playerActionsNote(
                "Снятая галочка скрывает кнопку у этого плеера. Чтобы изменить порядок, "
                        + "перетащите строку за «≡». Новые кнопки плеера показываются в конце, "
                        + "пока их не скрыть."));
    }

    /**
     * A handle that drags its row within the player button list. The lifted row follows the
     * finger, the rows it passes slide aside, and the order is saved on release. TalkBack gets
     * «Выше» and «Ниже» actions instead of the gesture.
     */
    private TextView playerActionDragHandle(LinearLayout list, LinearLayout row, String label,
            int index, int count,
            java.util.function.IntConsumer moveTo) {
        TextView handle = text("≡", 24, Ui.SECONDARY, Typeface.NORMAL);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Переместить «" + label + "»");
        int size = Ui.dp(this, 48);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.leftMargin = Ui.dp(this, 8);
        handle.setLayoutParams(params);
        if (count < 2) {
            handle.setAlpha(0.25f);
            return handle;
        }
        int up = View.generateViewId();
        int down = View.generateViewId();
        handle.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (index > 0) info.addAction(new android.view.accessibility
                        .AccessibilityNodeInfo.AccessibilityAction(up, "Выше"));
                if (index < count - 1) info.addAction(new android.view.accessibility
                        .AccessibilityNodeInfo.AccessibilityAction(down, "Ниже"));
            }

            @Override public boolean performAccessibilityAction(View host, int action,
                    Bundle arguments) {
                if (action == up && index > 0) {
                    moveTo.accept(index - 1);
                    return true;
                }
                if (action == down && index < count - 1) {
                    moveTo.accept(index + 1);
                    return true;
                }
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
        float[] startY = new float[1];
        int[] target = {index};
        handle.setOnTouchListener((view, event) -> {
            int step = row.getHeight();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    startY[0] = event.getRawY();
                    target[0] = index;
                    row.setBackground(Ui.background(Ui.NESTED, 8, this));
                    // Elevation draws the lifted row above its neighbours without reordering them.
                    row.setElevation(Ui.dp(this, 8));
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    float offset = Math.max(-index * step,
                            Math.min((count - 1 - index) * step, event.getRawY() - startY[0]));
                    row.setTranslationY(offset);
                    int next = playerActionDragTarget(index, offset, step, count);
                    if (next != target[0]) {
                        target[0] = next;
                        for (int other = 0; other < count; other++) {
                            View sibling = playerActionRow(list, other);
                            if (sibling == null || sibling == row) continue;
                            sibling.animate().translationY(playerActionShift(other, index, next)
                                    * step).setDuration(120L).start();
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    for (int other = 0; other < count; other++) {
                        View sibling = playerActionRow(list, other);
                        if (sibling == null) continue;
                        sibling.animate().cancel();
                        sibling.setTranslationY(0f);
                    }
                    row.setElevation(0f);
                    row.setBackground(null);
                    row.setTranslationY(0f);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && target[0] != index) {
                        moveTo.accept(target[0]);
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
        });
        return handle;
    }

    /** The row for list position {@code index}; rows carry their position as a tag. */
    private View playerActionRow(LinearLayout list, int index) {
        for (int child = 0; child < list.getChildCount(); child++) {
            View view = list.getChildAt(child);
            if (Integer.valueOf(index).equals(view.getTag())) return view;
        }
        return null;
    }

    /** The position a dragged row lands on after moving {@code offset} pixels. */
    static int playerActionDragTarget(int from, float offset, int step, int count) {
        if (step <= 0) return from;
        return Math.max(0, Math.min(count - 1, from + Math.round(offset / step)));
    }

    /** How many rows the row at {@code position} shifts while {@code from} hovers at {@code to}. */
    static int playerActionShift(int position, int from, int to) {
        if (from < to && position > from && position <= to) return -1;
        if (to < from && position >= to && position < from) return 1;
        return 0;
    }

    private void movePlayerAction(String owner, List<MediaCustomAction> actions,
            List<MediaCustomAction> hidden, List<MediaCustomAction> arranged, int from, int to) {
        List<MediaCustomAction> order = new ArrayList<>(arranged);
        order.add(to, order.remove(from));
        savePlayerActionRules(owner, actions, hidden, order);
    }

    /** Stores the rules against the ids the player publishes right now. */
    private void savePlayerActionRules(String owner, List<MediaCustomAction> actions,
            List<MediaCustomAction> hidden, List<MediaCustomAction> order) {
        prefs.putPlayerActionRules(owner, actionIds(actions), actionIds(hidden), actionIds(order));
        refreshPlayerActionChoices(false);
        refreshOverlayIfRunning();
    }

    private static List<String> actionIds(List<MediaCustomAction> actions) {
        List<String> ids = new ArrayList<>();
        for (MediaCustomAction action : actions) ids.add(action.action);
        return ids;
    }

    private TextView playerActionsNote(String message) {
        TextView note = text(message, 13, Ui.SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams params = fullWrap();
        params.topMargin = Ui.dp(this, 6);
        note.setLayoutParams(params);
        return note;
    }

    private void refreshPlayerActionsControl() {
        if (playerActions == null) return;
        playerActions.setProgress(prefs.playerActionsCount());
        updatePlayerActionsLabel();
    }

    private void updatePlayerActionsLabel() {
        int count = playerActions.getProgress();
        playerActionsValue.setText(count == 0 ? "Не показывать" : "До " + count);
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
        thumbnailSizeSetting.value.setText(thumbnailSizeSetting.seek.getProgress() + " dp");
        cornerRadiusSetting.value.setText(cornerRadiusSetting.seek.getProgress() + " dp");
        backdropAlphaSetting.value.setText(backdropAlphaSetting.seek.getProgress() * 5 + " %");
        artworkAlphaSetting.value.setText(artworkAlphaSetting.seek.getProgress() * 5 + " %");
        contentAlphaSetting.value.setText(contentAlphaSetting.seek.getProgress() * 5 + " %");
        updateBackdropControls();
    }

    private void updateBackdropControls() {
        if (backdropSolidButton == null || coverDimTitle == null
                || artworkAlphaSection == null || contentAlphaSetting == null) return;
        boolean solid = selectedBackdrop().solidFor(currentStyle());
        backdropSolidOptions.setVisibility(backdropSolidButton.isChecked()
                ? View.VISIBLE : View.GONE);
        // Dimming applies only to the artwork backdrop.
        coverDimTitle.setVisibility(solid ? View.GONE : View.VISIBLE);
        coverDimPresetGroup.setVisibility(solid ? View.GONE : View.VISIBLE);
        artworkAlphaSection.setVisibility(solid ? View.GONE : View.VISIBLE);
        int contentStep = opacityStep(contentAlpha);
        if (contentAlphaSetting.seek.getProgress() != contentStep) {
            contentAlphaSetting.seek.setProgress(contentStep);
        }
        int artworkStep = opacityStep(artworkAlpha);
        if (artworkAlphaSetting.seek.getProgress() != artworkStep) {
            artworkAlphaSetting.seek.setProgress(artworkStep);
        }
        // The swatch shows the colour itself; opacity has its own slider.
        backdropColorOptions.setVisibility(backdropSolidButton.isChecked()
                && currentStyle() == CardStyle.COMPACT ? View.VISIBLE : View.GONE);
        backdropSwatch.setBackground(Ui.background(0xFF000000 | shownBackdropRgb(), 8, this));
        backdropColorButton.setText(shownBackdropColor() == CardBackdrop.THEME_COLOR
                ? "Цвет подложки  по теме" : "Цвет подложки");
        int opacityStep = opacityStep(shownBackdropAlpha());
        if (backdropAlphaSetting.seek.getProgress() != opacityStep) {
            backdropAlphaSetting.seek.setProgress(opacityStep);
        }
    }

    /** Stored as alpha 0–255, moved in steps of 5%; a stored value changes only when moved. */
    static int opacityStep(int alpha) {
        return Math.round(alpha * (float) OPACITY_STEPS / 255f);
    }

    static int opacityAlpha(int step) {
        return Math.round(step * 255f / OPACITY_STEPS);
    }

    private CardBackdrop selectedBackdrop() {
        if (backdropSolidButton == null) return prefs.backdrop(currentStyle());
        return new CardBackdrop(backdropSolidButton.isChecked(), backdropColor,
                backdropAlpha, backdropOpaqueControls.isChecked(),
                backdropOpaqueTopRow.isChecked(), backdropDayColor, backdropDayAlpha,
                artworkAlpha, contentAlpha);
    }

    private Switch backdropPanelSwitch(String label) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextColor(Ui.PRIMARY);
        view.setTextSize(15);
        view.setOnCheckedChangeListener((button, checked) -> {
            if (!refreshingStyle) saveAppearance();
        });
        backdropSolidOptions.addView(view, labelParams());
        return view;
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
                selectedCoverDimPreset(),
                thumbnailSizeSetting.seek.getProgress(),
                selectedBackdrop(),
                cornerRadiusSetting.seek.getProgress());
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
        AtlasMediaWidgetProvider.refresh(this);
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
        if (previewHost == null || widthSize == null || topInsetSetting == null
                || previewHost.getVisibility() != View.VISIBLE) return;
        if (previewHost.getWidth() <= 0) {
            previewHost.post(this::renderPreview);
            return;
        }
        if (prefs.isWidgetMode()) {
            renderSystemWidgetPreview();
            return;
        }
        widgetPreviewTitle.setText("Предпросмотр · демо");
        int configuredWidthPx = widthInput();
        int configuredHeightPx = heightInput();
        android.content.Context widgetContext = CardTheme.overlay(getApplicationContext(),
                cardColors);
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
                configuredWidthPx, configuredHeightPx, currentStyle(),
                currentAppearance(),
                prefs.getBoolean(Prefs.KEY_RADIO_SAVED_NAVIGATION, false),
                prefs.getBoolean(Prefs.KEY_DRAG_HANDLE_VISIBLE, true),
                favoriteColumns == null ? prefs.radioFavoritesColumns()
                        : favoriteColumns.getProgress(),
                favoriteRows == null ? prefs.radioFavoritesRows() : favoriteRows.getProgress(),
                WidgetPreview.INERT);
        preview.renderSnapshot(WidgetPreview.demoSnapshot(), true);
        preview.setArtwork(WidgetPreview.demoArtwork(this));
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
