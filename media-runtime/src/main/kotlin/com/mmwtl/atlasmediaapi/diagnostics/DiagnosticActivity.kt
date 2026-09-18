package com.mmwtl.atlasmediaapi.diagnostics

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mmwtl.atlasmediaapi.MediaRuntime
import com.mmwtl.atlasmediaapi.diagnostics.DiagnosticUi.updateTileState
import com.mmwtl.atlasmediaapi.media.bridge.MediaBridgeContract
import com.mmwtl.atlasmediaapi.media.bridge.RadioCatalogType
import com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge
import com.mmwtl.atlasmediaapi.media.session.MediaNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class DiagnosticActivity : Activity() {
    companion object {
        private const val RC_IMPORT_RADIO_ZIP = 1002
        private const val RC_IMPORT_MEDIA_BACKUP_ZIP = 1003
        private const val SEEK_BAR_COMMIT_DEBOUNCE_MS = 250L
    }

    private data class SourceOption(val label: String, val id: String)

    private val sourceOptions = listOf(
        SourceOption("Отключено", ""),
        SourceOption("Radio", "RADIO"),
        SourceOption("Bluetooth", "BT"),
        SourceOption("USB", "USB"),
        SourceOption("Online", "ONLINE"),
        SourceOption("CarPlay", "CPAA"),
    )

    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(activityJob + Dispatchers.Main.immediate)

    private lateinit var demoModeSwitch: Switch

    private val sourceTileButtons = mutableMapOf<String, Button>()
    private lateinit var delayTitle: TextView
    private lateinit var delayValueLabel: TextView
    private lateinit var delaySeekBar: SeekBar
    private lateinit var startupAutoplaySwitch: Switch
    private lateinit var sourceLostSwitch: Switch
    private lateinit var sourceLostAutoplaySwitch: Switch
    private var delaySeekBarTracking = false
    private var pendingDelaySec: Int? = null
    private var delayCommitJob: Job? = null

    private lateinit var radioWidgetBroadcastSwitch: Switch
    private lateinit var clusterDimCoversSwitch: Switch
    private lateinit var clusterGuardIntervalTitle: TextView
    private lateinit var clusterGuardIntervalValueLabel: TextView
    private lateinit var clusterGuardIntervalSeekBar: SeekBar
    private lateinit var clusterBurstIntervalTitle: TextView
    private lateinit var clusterBurstIntervalValueLabel: TextView
    private lateinit var clusterBurstIntervalSeekBar: SeekBar
    private lateinit var clusterGuardIntervalWarning: TextView
    private lateinit var radioCatalogInfoView: TextView
    private lateinit var exportSampleZipButton: Button
    private lateinit var importCustomZipButton: Button
    private lateinit var restoreDefaultCatalogButton: Button
    private lateinit var radioStationPreviewView: TextView
    private lateinit var radioCoverThumbnailView: ImageView
    private var clusterGuardSeekBarTracking = false
    private var pendingClusterGuardIntervalMs: Long? = null
    private var clusterGuardCommitJob: Job? = null
    private var clusterBurstSeekBarTracking = false
    private var pendingClusterBurstIntervalMs: Long? = null
    private var clusterBurstCommitJob: Job? = null
    private var radioCoverLoadJob: Job? = null
    private var renderedRadioArtworkKey: String? = null

    private lateinit var statusView: TextView
    private lateinit var refreshButton: Button
    private lateinit var registerCallbacksButton: Button
    private lateinit var unregisterCallbacksButton: Button
    private lateinit var shareReportButton: Button
    private lateinit var reportView: TextView

    private var probeJob: Job? = null
    private var snapshotJob: Job? = null
    private var probe: OneOsProbe? = null
    private var lastProbeReport: OneOsProbeReport? = null

    private val coordinator
        get() = MediaRuntime.coordinator(this)

    private val isIntegrated
        get() = packageName != "com.mmwtl.atlasmediaapi"

    private var appliedScaleTenths: Int = ScaledContextHelper.DEFAULT_SCALE_TENTHS

    override fun attachBaseContext(newBase: Context) {
        val scale = ScaledContextHelper.resolveScaleTenths(newBase)
        appliedScaleTenths = scale
        super.attachBaseContext(ScaledContextHelper.wrap(newBase, scale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentScale = ScaledContextHelper.extractAndPersistIntentScale(this, intent)
        if (intentScale != null && intentScale != appliedScaleTenths) {
            recreate()
            return
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(DiagnosticUi.BACKGROUND)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                DiagnosticUi.dp(this@DiagnosticActivity, 24f),
                DiagnosticUi.dp(this@DiagnosticActivity, 20f),
                DiagnosticUi.dp(this@DiagnosticActivity, 24f),
                DiagnosticUi.dp(this@DiagnosticActivity, 40f),
            )
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val title = DiagnosticUi.heading(this, "Atlas Media API", 28f)
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val note = DiagnosticUi.text(
            this,
            "Медиа‑сервис OneOS для AtlasMediaWidget и внешних контроллеров.",
            14f,
            DiagnosticUi.SECONDARY,
        ).apply { setLineSpacing(0f, 1.12f) }
        root.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        DiagnosticUi.topMargin(note, this, 8f)

        val demoCard = DiagnosticUi.card(this)
        root.addView(demoCard)
        demoCard.addView(DiagnosticUi.heading(this, "Режим демо для эмулятора", 20f), DiagnosticUi.fullWrap())
        val demoHint = DiagnosticUi.text(
            this,
            "Не подключается к OneOS. Через штатный IPC выдаёт данные и обложки для RADIO, BT, USB, ONLINE, YUNTING и CPAA.",
            14f,
            DiagnosticUi.SECONDARY,
        ).apply { setLineSpacing(0f, 1.12f) }
        demoCard.addView(demoHint, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(demoHint, this, 8f)
        demoModeSwitch = DiagnosticUi.switch(
            this,
            "Включить demo backend",
            coordinator.isDemoMode(),
        ) { checked ->
            coordinator.setDemoMode(checked)
            render()
        }
        demoCard.addView(demoModeSwitch, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(demoModeSwitch, this, 12f)

        if (isIntegrated) {
            val infoCard = DiagnosticUi.card(this)
            root.addView(infoCard)
            infoCard.addView(DiagnosticUi.heading(this, "Настройки медиа и радио", 20f), DiagnosticUi.fullWrap())
            val infoText = DiagnosticUi.text(
                this,
                "Настройки источника звука по умолчанию, задержки, приборной панели и каталога радио перенесены на главный экран AtlasMediaWidget.",
                14f,
                DiagnosticUi.SECONDARY,
            ).apply { setLineSpacing(0f, 1.15f) }
            infoCard.addView(infoText, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(infoText, this, 8f)

            val openWidgetBtn = DiagnosticUi.button(this, "Открыть настройки виджета").apply {
                setOnClickListener {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) startActivity(launchIntent)
                }
            }
            infoCard.addView(openWidgetBtn, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(openWidgetBtn, this, 12f)
        } else {
            // 2. Source Settings Card (Full-width tiles + delay + switches)
            val settingsCard = DiagnosticUi.card(this)
            root.addView(settingsCard)
            settingsCard.addView(DiagnosticUi.heading(this, "Настройки источника", 20f), DiagnosticUi.fullWrap())

            val sourceTitle = DiagnosticUi.text(this, "Источник звука по умолчанию", 15f, DiagnosticUi.SECONDARY).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            settingsCard.addView(sourceTitle, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(sourceTitle, this, 14f)

            fun createTileRow(options: List<SourceOption>): LinearLayout {
                val row = LinearLayout(this@DiagnosticActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                options.forEachIndexed { index, option ->
                    val isSelected = coordinator.preferences.defaultAudioSource == option.id
                    val btn = DiagnosticUi.tileButton(this@DiagnosticActivity, option.label, isSelected) {
                        coordinator.preferences.defaultAudioSource = option.id
                        updateSettingsUiState()
                        render()
                    }
                    sourceTileButtons[option.id] = btn
                    val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) leftMargin = DiagnosticUi.dp(this@DiagnosticActivity, 6f)
                    }
                    row.addView(btn, p)
                }
                return row
            }

            val row1 = createTileRow(sourceOptions.subList(0, 3))
            settingsCard.addView(row1)
            DiagnosticUi.topMargin(row1, this, 8f)

            val row2 = createTileRow(sourceOptions.subList(3, 6))
            settingsCard.addView(row2)
            DiagnosticUi.topMargin(row2, this, 6f)

            delayTitle = DiagnosticUi.text(this, "Задержка переключения на старте", 15f, DiagnosticUi.SECONDARY).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            settingsCard.addView(delayTitle, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(delayTitle, this, 14f)

            delayValueLabel = DiagnosticUi.text(this, "${coordinator.preferences.defaultAudioSourceDelaySec} сек", 18f, DiagnosticUi.PRIMARY).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            settingsCard.addView(delayValueLabel, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(delayValueLabel, this, 4f)

            delaySeekBar = DiagnosticUi.sizeSeekBar(
                this,
                min = 0,
                max = 30,
                initial = coordinator.preferences.defaultAudioSourceDelaySec,
            ).apply {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            pendingDelaySec = progress
                            delayValueLabel.text = "$progress сек"
                            if (!delaySeekBarTracking) scheduleDelayCommit()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        delaySeekBarTracking = true
                        delayCommitJob?.cancel()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        delaySeekBarTracking = false
                        commitPendingDelay()
                    }
                })
            }
            settingsCard.addView(delaySeekBar, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(delaySeekBar, this, 6f)

            startupAutoplaySwitch = DiagnosticUi.switch(
                this,
                "Автовоспроизведение при старте",
                coordinator.preferences.defaultAudioSourceAutoplayOnStartup,
            ) { checked ->
                coordinator.preferences.defaultAudioSourceAutoplayOnStartup = checked
                render()
            }
            settingsCard.addView(startupAutoplaySwitch, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(startupAutoplaySwitch, this, 12f)

            sourceLostSwitch = DiagnosticUi.switch(
                this,
                "Автопереключение при потере источника",
                coordinator.preferences.autoSwitchToDefaultOnSourceLost,
            ) { checked ->
                coordinator.preferences.autoSwitchToDefaultOnSourceLost = checked
                updateSettingsUiState()
                render()
            }
            settingsCard.addView(sourceLostSwitch, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(sourceLostSwitch, this, 12f)

            sourceLostAutoplaySwitch = DiagnosticUi.switch(
                this,
                "Автовоспроизведение при потере источника",
                coordinator.preferences.autoSwitchToDefaultAutoplayOnSourceLost,
            ) { checked ->
                coordinator.preferences.autoSwitchToDefaultAutoplayOnSourceLost = checked
                render()
            }
            settingsCard.addView(sourceLostAutoplaySwitch, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(sourceLostAutoplaySwitch, this, 12f)

            updateSettingsUiState()

            // 3. Radio Catalog & Covers Card
            val radioCard = DiagnosticUi.card(this)
            root.addView(radioCard)
            radioCard.addView(DiagnosticUi.heading(this, "Каталог радио и обложки", 20f), DiagnosticUi.fullWrap())

            radioWidgetBroadcastSwitch = DiagnosticUi.switch(
                this,
                "Трансляция радио в виджет (название и обложка)",
                coordinator.radioCatalogRepository.isWidgetBroadcastEnabled,
            ) { checked ->
                coordinator.radioCatalogRepository.setWidgetBroadcastEnabled(checked)
                coordinator.stateHub.refreshRadioState()
                render()
            }
            radioCard.addView(radioWidgetBroadcastSwitch, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(radioWidgetBroadcastSwitch, this, 12f)

            clusterDimCoversSwitch = DiagnosticUi.switch(
                this,
                "Трансляция радио на приборку (название и обложка)",
                coordinator.clusterMediaBridge.isClusterCoversEnabled,
            ) { checked ->
                coordinator.clusterMediaBridge.setClusterCoversEnabled(checked)
                coordinator.stateHub.refreshRadioState()
                render()
            }
            radioCard.addView(clusterDimCoversSwitch, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterDimCoversSwitch, this, 8f)

            clusterGuardIntervalTitle = DiagnosticUi.text(
                this,
                "Базовый интервал adaptive watchdog",
                15f,
                DiagnosticUi.SECONDARY,
            ).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            radioCard.addView(clusterGuardIntervalTitle, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterGuardIntervalTitle, this, 12f)

            clusterGuardIntervalValueLabel = DiagnosticUi.text(
                this,
                "${coordinator.clusterMediaBridge.reassertWatchdogIntervalMs} мс " +
                    "±${ClusterMediaBridge.REASSERT_WATCHDOG_JITTER_MS}",
                18f,
                DiagnosticUi.PRIMARY,
            ).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            radioCard.addView(clusterGuardIntervalValueLabel, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterGuardIntervalValueLabel, this, 4f)

            clusterGuardIntervalSeekBar = DiagnosticUi.sizeSeekBar(
                this,
                min = (ClusterMediaBridge.MIN_REASSERT_WATCHDOG_INTERVAL_MS / 10L).toInt(),
                max = (ClusterMediaBridge.MAX_REASSERT_WATCHDOG_INTERVAL_MS / 10L).toInt(),
                initial = (coordinator.clusterMediaBridge.reassertWatchdogIntervalMs / 10L).toInt(),
            ).apply {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val intervalMs = progress * 10L
                            pendingClusterGuardIntervalMs = intervalMs
                            clusterGuardIntervalValueLabel.text =
                                "$intervalMs мс ±${ClusterMediaBridge.REASSERT_WATCHDOG_JITTER_MS}"
                            if (!clusterGuardSeekBarTracking) scheduleClusterGuardCommit()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        clusterGuardSeekBarTracking = true
                        clusterGuardCommitJob?.cancel()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        clusterGuardSeekBarTracking = false
                        commitPendingClusterGuardInterval()
                    }
                })
            }
            radioCard.addView(clusterGuardIntervalSeekBar, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterGuardIntervalSeekBar, this, 6f)

            clusterBurstIntervalTitle = DiagnosticUi.text(
                this,
                "Базовый интервал быстрых повторов",
                15f,
                DiagnosticUi.SECONDARY,
            ).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            radioCard.addView(clusterBurstIntervalTitle, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterBurstIntervalTitle, this, 12f)

            clusterBurstIntervalValueLabel = DiagnosticUi.text(
                this,
                "${coordinator.clusterMediaBridge.reassertBurstIntervalMs} мс",
                18f,
                DiagnosticUi.PRIMARY,
            ).apply {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            radioCard.addView(clusterBurstIntervalValueLabel, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterBurstIntervalValueLabel, this, 4f)

            clusterBurstIntervalSeekBar = DiagnosticUi.sizeSeekBar(
                this,
                min = (ClusterMediaBridge.MIN_REASSERT_BURST_INTERVAL_MS / 10L).toInt(),
                max = (ClusterMediaBridge.MAX_REASSERT_BURST_INTERVAL_MS / 10L).toInt(),
                initial = (coordinator.clusterMediaBridge.reassertBurstIntervalMs / 10L).toInt(),
            ).apply {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val intervalMs = progress * 10L
                            pendingClusterBurstIntervalMs = intervalMs
                            clusterBurstIntervalValueLabel.text = "$intervalMs мс"
                            if (!clusterBurstSeekBarTracking) scheduleClusterBurstCommit()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        clusterBurstSeekBarTracking = true
                        clusterBurstCommitJob?.cancel()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        clusterBurstSeekBarTracking = false
                        commitPendingClusterBurstInterval()
                    }
                })
            }
            radioCard.addView(clusterBurstIntervalSeekBar, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterBurstIntervalSeekBar, this, 6f)

            clusterGuardIntervalWarning = DiagnosticUi.text(
                this,
                "Быстрые повторы используют профиль x1/x1,5/x2,5/x5/x5; " +
                    "повторный callback — x1/x1,5. Watchdog использует jitter против синхронизации.",
                13f,
                DiagnosticUi.ERROR,
            )
            radioCard.addView(clusterGuardIntervalWarning, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(clusterGuardIntervalWarning, this, 4f)

            radioCatalogInfoView = DiagnosticUi.text(this, "", 14f, DiagnosticUi.SECONDARY).apply {
                setLineSpacing(0f, 1.12f)
            }
            radioCard.addView(radioCatalogInfoView, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(radioCatalogInfoView, this, 10f)

            val radioButtonsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            radioCard.addView(radioButtonsLayout, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(radioButtonsLayout, this, 12f)

            exportSampleZipButton = DiagnosticUi.button(this, "Экспортировать каталог радио (ZIP)").apply {
                setOnClickListener { exportSampleZip() }
            }
            radioButtonsLayout.addView(exportSampleZipButton, DiagnosticUi.fullWrap())

            importCustomZipButton = DiagnosticUi.button(this, "Импортировать свой архив (ZIP)", primary = true).apply {
                setOnClickListener { importCustomZip() }
            }
            radioButtonsLayout.addView(importCustomZipButton, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(importCustomZipButton, this, 8f)

            val exportBackupBtn = DiagnosticUi.button(this, "Экспортировать настройки медиа (ZIP)").apply {
                setOnClickListener { exportMediaBackupZip() }
            }
            radioButtonsLayout.addView(exportBackupBtn, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(exportBackupBtn, this, 8f)

            val importBackupBtn = DiagnosticUi.button(this, "Импортировать настройки медиа (ZIP)").apply {
                setOnClickListener { importMediaBackupZip() }
            }
            radioButtonsLayout.addView(importBackupBtn, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(importBackupBtn, this, 8f)

            restoreDefaultCatalogButton = DiagnosticUi.outlinedButton(this, "Восстановить стандартный каталог", destructive = true).apply {
                setOnClickListener {
                    coordinator.settingsController.restoreDefaultCatalog()
                    Toast.makeText(this@DiagnosticActivity, "Стандартный каталог Пензы восстановлен", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            radioButtonsLayout.addView(restoreDefaultCatalogButton, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(restoreDefaultCatalogButton, this, 8f)

            val previewCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    DiagnosticUi.dp(this@DiagnosticActivity, 12f),
                    DiagnosticUi.dp(this@DiagnosticActivity, 12f),
                    DiagnosticUi.dp(this@DiagnosticActivity, 12f),
                    DiagnosticUi.dp(this@DiagnosticActivity, 12f),
                )
                background = DiagnosticUi.background(this@DiagnosticActivity, DiagnosticUi.NESTED, 8f)
            }
            radioCard.addView(previewCard, DiagnosticUi.fullWrap())
            DiagnosticUi.topMargin(previewCard, this, 14f)

            radioCoverThumbnailView = ImageView(this).apply {
                val size = DiagnosticUi.dp(this@DiagnosticActivity, 56f)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = DiagnosticUi.dp(this@DiagnosticActivity, 12f)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = DiagnosticUi.outlinedBackground(this@DiagnosticActivity, strokeColor = DiagnosticUi.OUTLINE)
            }
            previewCard.addView(radioCoverThumbnailView)

            radioStationPreviewView = DiagnosticUi.text(this, "Радио не активно", 13f, DiagnosticUi.PRIMARY).apply {
                setLineSpacing(0f, 1.15f)
            }
            previewCard.addView(radioStationPreviewView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // 4. Service Status Card
        val statusCard = DiagnosticUi.card(this)
        root.addView(statusCard)
        statusCard.addView(DiagnosticUi.heading(this, "Состояние сервиса", 20f), DiagnosticUi.fullWrap())

        statusView = DiagnosticUi.text(this, "Проверка состояния…", 14f, DiagnosticUi.SECONDARY).apply {
            setLineSpacing(0f, 1.12f)
        }
        statusCard.addView(statusView, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(statusView, this, 10f)

        // 4. Control & Verification Card
        val actionsCard = DiagnosticUi.card(this)
        root.addView(actionsCard)
        actionsCard.addView(DiagnosticUi.heading(this, "Управление и проверка", 20f), DiagnosticUi.fullWrap())

        refreshButton = DiagnosticUi.button(this, "Запустить проверку OneOS").apply {
            setOnClickListener { runProbe() }
        }
        actionsCard.addView(refreshButton, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(refreshButton, this, 12f)

        registerCallbacksButton = DiagnosticUi.button(this, "Зарегистрировать callbacks сосуществования").apply {
            setOnClickListener {
                isEnabled = false
                activityScope.launch {
                    try {
                        getOrCreateProbe().registerCoexistenceCallbacks()
                    } finally {
                        isEnabled = true
                    }
                }
            }
        }
        actionsCard.addView(registerCallbacksButton, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(registerCallbacksButton, this, 8f)

        unregisterCallbacksButton = DiagnosticUi.outlinedButton(this, "Отменить callbacks сосуществования", destructive = true).apply {
            setOnClickListener { probe?.unregisterCoexistenceCallbacks() }
        }
        actionsCard.addView(unregisterCallbacksButton, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(unregisterCallbacksButton, this, 8f)

        shareReportButton = DiagnosticUi.outlinedButton(this, "Экспортировать диагностический отчёт").apply {
            setOnClickListener { shareDiagnostic() }
        }
        actionsCard.addView(shareReportButton, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(shareReportButton, this, 8f)

        // 5. Diagnostic Report Card
        val reportCard = DiagnosticUi.card(this)
        root.addView(reportCard)
        reportCard.addView(DiagnosticUi.heading(this, "Диагностический отчёт", 20f), DiagnosticUi.fullWrap())

        reportView = DiagnosticUi.text(this, "", 12f, DiagnosticUi.SECONDARY).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setLineSpacing(0f, 1.08f)
            setPadding(
                DiagnosticUi.dp(this@DiagnosticActivity, 14f),
                DiagnosticUi.dp(this@DiagnosticActivity, 14f),
                DiagnosticUi.dp(this@DiagnosticActivity, 14f),
                DiagnosticUi.dp(this@DiagnosticActivity, 14f),
            )
            background = DiagnosticUi.background(this@DiagnosticActivity, DiagnosticUi.NESTED, 8f)
        }
        reportCard.addView(reportView, DiagnosticUi.fullWrap())
        DiagnosticUi.topMargin(reportView, this, 12f)

        setContentView(scroll)
        DiagnosticUi.applySystemBarInsets(scroll)

        snapshotJob = activityScope.launch {
            coordinator.stateRepository.snapshots.collectLatest {
                render()
            }
        }

        runProbe()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val intentScale = ScaledContextHelper.extractAndPersistIntentScale(this, intent)
        if (intentScale != null && intentScale != appliedScaleTenths) {
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        if (!isIntegrated) {
            commitPendingDelay()
            commitPendingClusterGuardInterval()
            commitPendingClusterBurstInterval()
        }
        super.onPause()
    }

    override fun onDestroy() {
        snapshotJob?.cancel()
        probeJob?.cancel()
        radioCoverLoadJob?.cancel()
        probe?.close()
        activityJob.cancel()
        super.onDestroy()
    }

    private fun runProbe() {
        probeJob?.cancel()
        refreshButton.isEnabled = false
        val activeProbe = getOrCreateProbe()
        probeJob = activityScope.launch {
            try {
                activeProbe.run()
            } finally {
                refreshButton.isEnabled = true
                render()
            }
        }
    }

    private fun getOrCreateProbe(): OneOsProbe {
        probe?.let { return it }
        return OneOsProbe(this) { report ->
            lastProbeReport = report
            render()
        }.also { probe = it }
    }

    private fun isStoragePermissionGranted(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager() || readGranted
        }
        return readGranted
    }

    private fun exportSampleZip() {
        activityScope.launch(Dispatchers.IO) {
            val file = runCatching { coordinator.radioCatalogRepository.createCatalogZipFile() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file != null && file.isFile) {
                    val uri = FileProvider.getUriForFile(
                        this@DiagnosticActivity,
                        "$packageName.fileprovider",
                        file,
                    )
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "application/zip"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(sendIntent, "Экспорт образца каталога радио"))
                } else {
                    Toast.makeText(this@DiagnosticActivity, "Ошибка экспорта архива", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportMediaBackupZip() {
        activityScope.launch(Dispatchers.IO) {
            val file = runCatching {
                val exportDir = java.io.File(cacheDir, "exports").apply { mkdirs() }
                val target = java.io.File(exportDir, "AtlasMediaApi-backup.zip")
                target.delete()
                java.io.FileOutputStream(target).use { fos ->
                    coordinator.settingsController.exportMediaBackup(fos)
                }
                target
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (file != null && file.isFile) {
                    val uri = FileProvider.getUriForFile(
                        this@DiagnosticActivity,
                        "$packageName.fileprovider",
                        file,
                    )
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "application/zip"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(sendIntent, "Экспорт резервной копии медиа"))
                } else {
                    Toast.makeText(this@DiagnosticActivity, "Ошибка создания архива", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importMediaBackupZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        startActivityForResult(intent, RC_IMPORT_MEDIA_BACKUP_ZIP)
    }

    private fun importCustomZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        startActivityForResult(intent, RC_IMPORT_RADIO_ZIP)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_IMPORT_RADIO_ZIP && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            activityScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        coordinator.radioCatalogRepository.importCustomZip(stream)
                    }?.getOrThrow() ?: throw IOException("Не удалось прочитать файл")
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess { count ->
                        coordinator.settingsController.onRadioCatalogChanged()
                        Toast.makeText(this@DiagnosticActivity, "Успешно импортировано $count станций (каталог заменён)", Toast.LENGTH_LONG).show()
                        render()
                    }.onFailure { error ->
                        Toast.makeText(this@DiagnosticActivity, "Ошибка импорта: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else if (requestCode == RC_IMPORT_MEDIA_BACKUP_ZIP && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            activityScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val opId = java.util.UUID.randomUUID().toString()
                        val prep = coordinator.settingsController.prepareMediaImport(opId, stream)
                        if (prep.status != MediaBridgeContract.Status.OK) {
                            throw IOException(prep.warnings.joinToString("; ").ifBlank { "Ошибка валидации архива (статус ${prep.status})" })
                        }
                        val commitResult = coordinator.settingsController.commitMediaImport(opId, prep.stagingToken)
                        if (commitResult.status != MediaBridgeContract.Status.OK) {
                            throw IOException("Ошибка применения архива: статус ${commitResult.status}")
                        }
                        prep.stationCount
                    } ?: throw IOException("Не удалось открыть файл")
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess { _ ->
                        coordinator.stateHub.refreshRadioState()
                        Toast.makeText(this@DiagnosticActivity, "Настройки медиа успешно импортированы; каталог радио сохранён", Toast.LENGTH_LONG).show()
                        render()
                    }.onFailure { error ->
                        Toast.makeText(this@DiagnosticActivity, "Ошибка импорта: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun shareDiagnostic() {
        val text = generateDiagnosticText()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
    }

    private fun scheduleDelayCommit() {
        delayCommitJob?.cancel()
        delayCommitJob = activityScope.launch {
            delay(SEEK_BAR_COMMIT_DEBOUNCE_MS)
            delayCommitJob = null
            commitPendingDelay()
        }
    }

    private fun commitPendingDelay() {
        delayCommitJob?.cancel()
        delayCommitJob = null
        val delaySec = pendingDelaySec ?: return
        pendingDelaySec = null
        if (coordinator.preferences.defaultAudioSourceDelaySec == delaySec) return
        coordinator.preferences.defaultAudioSourceDelaySec = delaySec
        reportView.text = generateDiagnosticText()
    }

    private fun scheduleClusterGuardCommit() {
        clusterGuardCommitJob?.cancel()
        clusterGuardCommitJob = activityScope.launch {
            delay(SEEK_BAR_COMMIT_DEBOUNCE_MS)
            clusterGuardCommitJob = null
            commitPendingClusterGuardInterval()
        }
    }

    private fun commitPendingClusterGuardInterval() {
        clusterGuardCommitJob?.cancel()
        clusterGuardCommitJob = null
        val intervalMs = pendingClusterGuardIntervalMs ?: return
        pendingClusterGuardIntervalMs = null
        if (coordinator.clusterMediaBridge.reassertWatchdogIntervalMs == intervalMs) return
        coordinator.clusterMediaBridge.setReassertWatchdogIntervalMs(intervalMs)
        reportView.text = generateDiagnosticText()
    }

    private fun scheduleClusterBurstCommit() {
        clusterBurstCommitJob?.cancel()
        clusterBurstCommitJob = activityScope.launch {
            delay(SEEK_BAR_COMMIT_DEBOUNCE_MS)
            clusterBurstCommitJob = null
            commitPendingClusterBurstInterval()
        }
    }

    private fun commitPendingClusterBurstInterval() {
        clusterBurstCommitJob?.cancel()
        clusterBurstCommitJob = null
        val intervalMs = pendingClusterBurstIntervalMs ?: return
        pendingClusterBurstIntervalMs = null
        if (coordinator.clusterMediaBridge.reassertBurstIntervalMs == intervalMs) return
        coordinator.clusterMediaBridge.setReassertBurstIntervalMs(intervalMs)
        reportView.text = generateDiagnosticText()
    }

    private fun render() {
        if (demoModeSwitch.isChecked != coordinator.isDemoMode()) {
            demoModeSwitch.isChecked = coordinator.isDemoMode()
        }
        if (!isIntegrated) {
            renderRadioCatalog()
        }
        reportView.text = generateDiagnosticText()
        statusView.text = generateStatusText()
    }

    private fun renderRadioCatalog() {
        val catalogInfo = coordinator.radioCatalogRepository.getCatalogInfo()
        radioWidgetBroadcastSwitch.isChecked = catalogInfo.isWidgetBroadcastEnabled
        clusterDimCoversSwitch.isChecked = coordinator.clusterMediaBridge.isClusterCoversEnabled
        val clusterGuardEnabled = coordinator.clusterMediaBridge.isClusterCoversEnabled
        val clusterGuardIntervalMs = coordinator.clusterMediaBridge.reassertWatchdogIntervalMs
        clusterGuardIntervalTitle.isEnabled = clusterGuardEnabled
        clusterGuardIntervalTitle.alpha = if (clusterGuardEnabled) 1f else 0.45f
        if (pendingClusterGuardIntervalMs == null) {
            clusterGuardIntervalValueLabel.text =
                "$clusterGuardIntervalMs мс ±${ClusterMediaBridge.REASSERT_WATCHDOG_JITTER_MS}"
            clusterGuardIntervalSeekBar.progress = (clusterGuardIntervalMs / 10L).toInt()
        }
        clusterGuardIntervalValueLabel.isEnabled = clusterGuardEnabled
        clusterGuardIntervalValueLabel.alpha = if (clusterGuardEnabled) 1f else 0.45f
        clusterGuardIntervalSeekBar.isEnabled = clusterGuardEnabled
        clusterGuardIntervalSeekBar.alpha = if (clusterGuardEnabled) 1f else 0.45f
        val clusterBurstIntervalMs = coordinator.clusterMediaBridge.reassertBurstIntervalMs
        clusterBurstIntervalTitle.isEnabled = clusterGuardEnabled
        clusterBurstIntervalTitle.alpha = if (clusterGuardEnabled) 1f else 0.45f
        if (pendingClusterBurstIntervalMs == null) {
            clusterBurstIntervalValueLabel.text = "$clusterBurstIntervalMs мс"
            clusterBurstIntervalSeekBar.progress = (clusterBurstIntervalMs / 10L).toInt()
        }
        clusterBurstIntervalValueLabel.isEnabled = clusterGuardEnabled
        clusterBurstIntervalValueLabel.alpha = if (clusterGuardEnabled) 1f else 0.45f
        clusterBurstIntervalSeekBar.isEnabled = clusterGuardEnabled
        clusterBurstIntervalSeekBar.alpha = if (clusterGuardEnabled) 1f else 0.45f
        clusterGuardIntervalWarning.isEnabled = clusterGuardEnabled
        clusterGuardIntervalWarning.alpha = if (clusterGuardEnabled) 1f else 0.45f
        radioCatalogInfoView.text = catalogInfo.description
        val isCustom = catalogInfo.type == RadioCatalogType.CUSTOM
        restoreDefaultCatalogButton.isEnabled = isCustom
        restoreDefaultCatalogButton.alpha = if (isCustom) 1f else 0.45f

        val snapshot = coordinator.stateRepository.snapshot()
        if (snapshot.audioSource == "RADIO") {
            radioStationPreviewView.text = buildString {
                appendLine("Текущая станция: ${snapshot.title.ifBlank { "Частота не выбрана" }}")
                appendLine("Диапазон / подзаголовок: ${snapshot.artist.ifBlank { "FM" }}")
                append("Обложка: ${if (snapshot.artworkUri.isNotBlank()) "Загружена" else "Отсутствует"}")
            }
            if (snapshot.artworkUri.isNotBlank()) {
                renderRadioCover(snapshot.artworkUri, snapshot.artworkRevision)
            } else {
                renderRadioCover("", snapshot.artworkRevision)
            }
        } else {
            radioStationPreviewView.text = "Радио сейчас не воспроизводится\n(Активен источник: ${snapshot.audioSource.ifBlank { "NONE" }})"
            renderRadioCover("", snapshot.artworkRevision)
        }
    }

    private fun renderRadioCover(artworkUri: String, artworkRevision: Long) {
        val artworkKey = if (artworkUri.isBlank()) "" else "$artworkUri#$artworkRevision"
        if (renderedRadioArtworkKey == artworkKey) return
        renderedRadioArtworkKey = artworkKey
        radioCoverLoadJob?.cancel()
        radioCoverThumbnailView.setImageDrawable(null)
        if (artworkUri.isBlank()) return

        val targetSizePx = DiagnosticUi.dp(this, 56f)
        radioCoverLoadJob = activityScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeRadioCoverThumbnail(Uri.parse(artworkUri), targetSizePx)
            }
            if (renderedRadioArtworkKey == artworkKey) {
                radioCoverThumbnailView.setImageBitmap(bitmap)
            }
        }
    }

    private fun decodeRadioCoverThumbnail(uri: Uri, targetSizePx: Int) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > targetSizePx * 2 ||
            bounds.outHeight / sampleSize > targetSizePx * 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

    private fun updateSettingsUiState() {
        if (isIntegrated) return
        val selectedId = coordinator.preferences.defaultAudioSource
        sourceTileButtons.forEach { (id, button) ->
            button.updateTileState(id == selectedId)
        }

        val hasDefaultSource = selectedId.isNotBlank()
        val delaySec = coordinator.preferences.defaultAudioSourceDelaySec
        delayValueLabel.text = "$delaySec сек"
        delayTitle.isEnabled = hasDefaultSource
        delayTitle.alpha = if (hasDefaultSource) 1f else 0.45f
        delayValueLabel.isEnabled = hasDefaultSource
        delayValueLabel.alpha = if (hasDefaultSource) 1f else 0.45f
        delaySeekBar.isEnabled = hasDefaultSource
        delaySeekBar.alpha = if (hasDefaultSource) 1f else 0.45f

        startupAutoplaySwitch.isEnabled = hasDefaultSource
        startupAutoplaySwitch.alpha = if (hasDefaultSource) 1f else 0.45f

        sourceLostSwitch.isEnabled = hasDefaultSource
        sourceLostSwitch.alpha = if (hasDefaultSource) 1f else 0.45f

        val canAutoplayOnLost = hasDefaultSource && sourceLostSwitch.isChecked
        sourceLostAutoplaySwitch.isEnabled = canAutoplayOnLost
        sourceLostAutoplaySwitch.alpha = if (canAutoplayOnLost) 1f else 0.45f
    }

    private fun generateStatusText(): String {
        val snapshot = coordinator.stateRepository.snapshot()
        val listenerEnabled = MediaNotificationListenerService.isListenerEnabled(this)
        val storageGranted = isStoragePermissionGranted()
        val backend = if (coordinator.isBackendRunning()) "запущен" else "ожидает клиента"
        val connection = when {
            coordinator.isDemoMode() -> "demo backend активен"
            snapshot.backendConnected -> "OneOS подключён"
            snapshot.backendErrorCode == MediaBridgeContract.BackendError.CONNECTING -> "подключается к OneOS"
            else -> "OneOS не подключён"
        }
        val listener = if (listenerEnabled) "доступ к уведомлениям включён" else "нужен доступ к уведомлениям"
        val storage = if (storageGranted) "хранилище доступно" else "нужен доступ к хранилищу"
        return "Backend $backend  ·  ${coordinator.activeClientCount()} IPC-клиентов\n$connection  ·  $listener  ·  $storage"
    }

    private fun generateDiagnosticText(): String {
        val snapshot = coordinator.stateRepository.snapshot()
        val notifEnabled = MediaNotificationListenerService.isListenerEnabled(this)
        val notifConnected = MediaNotificationListenerService.isConnected()
        val storageGranted = isStoragePermissionGranted()
        val report = lastProbeReport
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return buildString {
            appendLine("=== AtlasMediaApi Diagnostic ===")
            appendLine("Application: $packageName")
            appendLine("Version: ${packageInfo.versionName} ($versionCode)")
            appendLine("Protocol version: ${MediaBridgeContract.PROTOCOL_VERSION}")
            appendLine("Backend running: ${coordinator.isBackendRunning()}")
            appendLine("Demo mode: ${coordinator.isDemoMode()}")
            appendLine("Active IPC clients: ${coordinator.activeClientCount()}")
            appendLine("Notification listener enabled: $notifEnabled")
            appendLine("Notification listener connected: $notifConnected")
            appendLine("Storage permission granted: $storageGranted")
            appendLine("Default media package: ${coordinator.preferences.defaultMediaPackage.ifBlank { "none" }}")
            appendLine("Default audio source: ${coordinator.preferences.defaultAudioSource.ifBlank { "disabled" }}")
            appendLine("Default audio source delay: ${coordinator.preferences.defaultAudioSourceDelaySec}s")
            appendLine("Autoplay on startup: ${coordinator.preferences.defaultAudioSourceAutoplayOnStartup}")
            appendLine("Auto-switch on source lost: ${coordinator.preferences.autoSwitchToDefaultOnSourceLost}")
            appendLine("Autoplay on source lost: ${coordinator.preferences.autoSwitchToDefaultAutoplayOnSourceLost}")
            appendLine()
            appendLine("--- MediaSnapshot State ---")
            appendLine("generation: ${snapshot.generation}")
            appendLine("backendConnected: ${snapshot.backendConnected}")
            appendLine("backendErrorCode: ${snapshot.backendErrorCode}")
            appendLine("audioSource: ${snapshot.audioSource}")
            appendLine("appSource: ${snapshot.appSource}")
            appendLine("ownerPackage: ${snapshot.ownerPackage.ifBlank { "none" }}")
            appendLine("ownerApp: ${snapshot.ownerApp.ifBlank { "none" }}")
            appendLine("playbackState: ${snapshot.playbackState}")
            appendLine("speed: ${snapshot.speed}")
            appendLine("duration: ${snapshot.duration}")
            appendLine("position: ${snapshot.position}")
            appendLine("capabilities: 0x${snapshot.capabilities.toString(16)}")
            appendLine("artworkRevision: ${snapshot.artworkRevision}")
            appendLine("artworkUri: ${if (snapshot.artworkUri.isNotBlank()) "present" else "none"}")
            appendLine()
            val activeSessions = coordinator.sessionObserver.getActiveControllers()
            appendLine("--- Active Media Sessions (${activeSessions.size}) ---")
            if (activeSessions.isEmpty()) {
                appendLine("  none")
            } else {
                activeSessions.forEach { ctrl ->
                    appendLine("  package: ${ctrl.packageName} (playbackState: ${ctrl.playbackState?.state ?: 0})")
                }
            }
            appendLine()

            val cpBridge = coordinator.carPlayBridge
            appendLine("--- CarPlay Native AIDL Bridge ---")
            appendLine("connected: ${cpBridge.isConnected}")
            appendLine("lastCoverTimestamp: ${if (cpBridge.lastCoverTimestamp > 0) cpBridge.lastCoverTimestamp else "none"}")
            val cpNp = cpBridge.lastNowPlaying
            if (cpNp != null) {
                appendLine("carplayTrack: title='${cpNp.mediaItemTitle}', artist='${cpNp.mediaItemArtist}'")
            }
            appendLine()

            val catalogInfo = coordinator.radioCatalogRepository.getCatalogInfo()
            appendLine("--- Radio Catalog & Cluster ---")
            appendLine("catalogType: ${catalogInfo.type}")
            appendLine("stationCount: ${catalogInfo.stationCount}")
            appendLine("radioWidgetBroadcastEnabled: ${catalogInfo.isWidgetBroadcastEnabled}")
            appendLine("clusterDimBroadcastEnabled: ${coordinator.clusterMediaBridge.isClusterCoversEnabled}")
            appendLine("clusterDimAdaptiveWatchdogBaseMs: ${coordinator.clusterMediaBridge.reassertWatchdogIntervalMs}")
            appendLine("clusterDimAdaptiveWatchdogJitterMs: ${ClusterMediaBridge.REASSERT_WATCHDOG_JITTER_MS}")
            appendLine(
                "clusterDimAdaptiveWatchdogCustom: " +
                    (coordinator.clusterMediaBridge.reassertWatchdogIntervalMs !=
                        ClusterMediaBridge.RECOMMENDED_REASSERT_WATCHDOG_INTERVAL_MS),
            )
            val clusterStatus = coordinator.clusterMediaBridge.getStatus()
            appendLine("clusterDimAvailable: ${clusterStatus.available}")
            appendLine("clusterDimInitializationError: ${clusterStatus.initializationError.ifBlank { "none" }}")
            appendLine("clusterDimLastUpdate: ${clusterStatus.lastUpdate}")
            appendLine("clusterDimLastUpdateError: ${clusterStatus.lastUpdateError.ifBlank { "none" }}")
            appendLine("clusterDimArtworkFile: ${clusterStatus.artworkFilePath.ifBlank { "none" }}")
            appendLine("clusterDimArtworkWirePath: ${clusterStatus.artworkWirePath.ifBlank { "none" }}")
            appendLine("clusterDimArtworkQnxPath: ${clusterStatus.artworkQnxPath.ifBlank { "none" }}")
            appendLine("clusterDimArtworkUriGrants: ${clusterStatus.artworkGrantReport.ifBlank { "none" }}")
            appendLine("clusterDimSendCount: ${clusterStatus.sendCount}")
            appendLine("description: ${catalogInfo.description}")
            appendLine()

            if (report != null) {
                appendLine("--- OneOS Probe Report ---")
                appendLine("probe status: ${report.status}")
                appendLine("elapsedMs: ${report.elapsedMillis}")
                appendLine("mediaCenter.isAlive: ${report.mediaCenterAlive}")
                appendLine("probe audioSource: ${report.currentAudioSource}")
                appendLine("probe appSource: ${report.currentAppSource}")
                appendLine("radioManager.isAlive: ${report.radioManagerAlive}")
                appendLine("sourceCallbackRegistered: ${report.sourceCallbackRegistered}")
                appendLine("sourceCallbackCount: ${report.sourceCallbackCount}")
                appendLine("lastSourceCallback: ${report.lastSourceCallback}")
                appendLine("callbackProbeRegistered: ${report.callbackProbeRegistered}")
                appendLine("musicCallbackCount: ${report.musicCallbackCount}")
                appendLine("lastMusicCallback: ${report.lastMusicCallback}")
                appendLine("deviceCallbackCount: ${report.deviceCallbackCount}")
                appendLine("lastDeviceCallback: ${report.lastDeviceCallback}")
                appendLine("radioCallbackCount: ${report.radioCallbackCount}")
                appendLine("lastRadioCallback: ${report.lastRadioCallback}")
                appendLine("callbackRegistrationError: ${report.callbackRegistrationError ?: "none"}")
                appendLine("errorCode: ${report.errorCode ?: "none"}")
            }
        }
    }
}
