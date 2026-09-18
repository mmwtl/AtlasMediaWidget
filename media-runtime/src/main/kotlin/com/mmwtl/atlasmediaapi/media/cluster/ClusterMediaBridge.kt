package com.mmwtl.atlasmediaapi.media.cluster

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.net.Uri
import android.media.session.PlaybackState
import com.mmwtl.atlasmediaapi.media.bridge.MediaSnapshot
import com.mmwtl.atlasmediaapi.media.bridge.BridgeAudioSource
import com.ecarx.xui.adaptapi.diminteraction.DimInteraction
import com.ecarx.xui.adaptapi.diminteraction.IMediaInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.math.roundToLong

internal enum class ReassertScheduleKind {
    FULL,
    DUPLICATE_REPAIR,
}

internal class ReassertWatchdogIntervalStore(
    private val prefs: SharedPreferences,
) {
    private val hasStoredValue = prefs.contains(
        ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS,
    )
    private val storedValue = prefs.getLong(
        ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS,
        ClusterMediaBridge.DEFAULT_REASSERT_WATCHDOG_INTERVAL_MS,
    )

    @Volatile
    var value: Long = ClusterMediaBridge.normalizeReassertWatchdogInterval(storedValue)
        private set

    init {
        if (!hasStoredValue || storedValue != value) {
            prefs.edit()
                .putLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, value)
                .apply()
        }
    }

    fun set(intervalMs: Long): Long {
        val normalized = ClusterMediaBridge.normalizeReassertWatchdogInterval(intervalMs)
        if (normalized == value) return normalized
        value = normalized
        prefs.edit()
            .putLong(ClusterMediaBridge.KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS, normalized)
            .apply()
        return normalized
    }
}

internal class ReassertBurstIntervalStore(
    private val prefs: SharedPreferences,
) {
    private val hasStoredValue = prefs.contains(ClusterMediaBridge.KEY_REASSERT_BURST_INTERVAL_MS)
    private val storedValue = prefs.getLong(
        ClusterMediaBridge.KEY_REASSERT_BURST_INTERVAL_MS,
        ClusterMediaBridge.DEFAULT_REASSERT_BURST_INTERVAL_MS,
    )

    @Volatile
    var value: Long = ClusterMediaBridge.normalizeReassertBurstInterval(storedValue)
        private set

    init {
        if (!hasStoredValue || storedValue != value) {
            prefs.edit()
                .putLong(ClusterMediaBridge.KEY_REASSERT_BURST_INTERVAL_MS, value)
                .apply()
        }
    }

    fun set(intervalMs: Long): Long {
        val normalized = ClusterMediaBridge.normalizeReassertBurstInterval(intervalMs)
        if (normalized == value) return normalized
        value = normalized
        prefs.edit()
            .putLong(ClusterMediaBridge.KEY_REASSERT_BURST_INTERVAL_MS, normalized)
            .apply()
        return normalized
    }
}

/**
 * Manages playback metadata and artwork broadcast to the vehicle digital instrument cluster (DIM/QNX)
 * via the ECarX DimInteraction hardware abstraction layer.
 */
class ClusterMediaBridge(
    private val context: Context,
) {
    data class Status(
        val available: Boolean,
        val initializationError: String,
        val lastUpdate: String,
        val lastUpdateError: String,
        val artworkFilePath: String,
        val artworkWirePath: String,
        val artworkQnxPath: String,
        val artworkGrantReport: String,
        val sendCount: Int,
        val directDimBound: Boolean,
        val directDimSendCount: Int,
        val directDimLastResult: String,
        val directDimLastError: String,
    )

    companion object {
        const val PREFS_NAME = "cluster_dim_prefs"
        const val KEY_CLUSTER_ONLINE_ENABLED = "cluster_dim_online_enabled"
        const val KEY_CLUSTER_ONLINE_PROGRESS_ENABLED = "cluster_dim_online_progress_enabled"
        private const val KEY_LEGACY_CLUSTER_ONLINE_FACADE_PROGRESS_ENABLED =
            "cluster_dim_online_facade_progress_enabled"
        const val KEY_CLUSTER_COVERS_ENABLED = "cluster_dim_covers_enabled"
        const val KEY_CLUSTER_RADIO_FACADE_ENABLED = "cluster_dim_radio_facade_enabled"
        const val KEY_ADAPTIVE_WATCHDOG_BASE_INTERVAL_MS = "adaptive_watchdog_base_interval_ms"
        const val KEY_REASSERT_BURST_INTERVAL_MS = "reassert_burst_interval_ms"
        private const val NFS_SHARED_DIR = "/data/vendor/nfs/shared"

        /**
         * The stock radio process is another DIM producer and can publish its FM-only
         * metadata after our update. A changed radio payload gets a bounded 1.5-second
         * repair burst; duplicate callbacks get only a short repair and cannot extend
         * the aggressive window indefinitely.
         */
        const val MIN_REASSERT_BURST_INTERVAL_MS = 50L
        const val MAX_REASSERT_BURST_INTERVAL_MS = 500L
        const val DEFAULT_REASSERT_BURST_INTERVAL_MS = 100L
        const val ONLINE_PROGRESS_INTERVAL_MS = 1_000L
        const val MIN_REASSERT_WATCHDOG_INTERVAL_MS = 1_000L
        const val MAX_REASSERT_WATCHDOG_INTERVAL_MS = 5_000L
        const val RECOMMENDED_REASSERT_WATCHDOG_INTERVAL_MS = 1_250L
        const val DEFAULT_REASSERT_WATCHDOG_INTERVAL_MS = RECOMMENDED_REASSERT_WATCHDOG_INTERVAL_MS
        const val REASSERT_WATCHDOG_JITTER_MS = 150L
        private const val MAX_REASSERT_RETRY_INTERVAL_MS = 30_000L

        internal fun normalizeReassertWatchdogInterval(intervalMs: Long): Long =
            intervalMs.coerceIn(
                MIN_REASSERT_WATCHDOG_INTERVAL_MS,
                MAX_REASSERT_WATCHDOG_INTERVAL_MS,
            )

        internal fun normalizeReassertBurstInterval(intervalMs: Long): Long =
            intervalMs.coerceIn(
                MIN_REASSERT_BURST_INTERVAL_MS,
                MAX_REASSERT_BURST_INTERVAL_MS,
            )

        internal fun reassertBurstDelaysMs(intervalMs: Long): List<Long> {
            val base = normalizeReassertBurstInterval(intervalMs)
            return listOf(base, base * 3L / 2L, base * 5L / 2L, base * 5L, base * 5L)
        }

        internal fun duplicateRepairDelaysMs(intervalMs: Long): List<Long> =
            reassertBurstDelaysMs(intervalMs).take(2)

        internal fun extrapolateOnlineProgress(
            positionMs: Long,
            durationMs: Long,
            speed: Float,
            updateElapsedRealtime: Long,
            nowElapsedRealtime: Long,
        ): Long? {
            if (positionMs < 0L) return null
            val elapsedMs = if (updateElapsedRealtime > 0L) {
                (nowElapsedRealtime - updateElapsedRealtime).coerceAtLeast(0L)
            } else 0L
            val progressed = positionMs + (elapsedMs * speed.coerceAtLeast(0f)).roundToLong()
            return progressed.coerceAtLeast(0L).let { value ->
                if (durationMs > 0L) value.coerceAtMost(durationMs) else value
            }
        }

        internal fun onlinePayloadDedupKey(
            payload: DirectDimMediaClient.Payload,
        ): DirectDimMediaClient.Payload = payload.copy(currentProgress = 0L)

        internal fun jitteredReassertWatchdogDelayMs(
            intervalMs: Long,
            jitterMs: Long,
        ): Long = normalizeReassertWatchdogInterval(intervalMs) +
            jitterMs.coerceIn(-REASSERT_WATCHDOG_JITTER_MS, REASSERT_WATCHDOG_JITTER_MS)

        internal fun reassertScheduleKind(
            currentPayloadKey: String,
            currentScheduleActive: Boolean,
            incomingPayloadKey: String,
        ): ReassertScheduleKind = if (
            currentScheduleActive && currentPayloadKey == incomingPayloadKey
        ) {
            ReassertScheduleKind.DUPLICATE_REPAIR
        } else {
            ReassertScheduleKind.FULL
        }

        internal fun reassertRetryDelayMs(intervalMs: Long, consecutiveFailures: Int): Long {
            val exponent = consecutiveFailures.coerceIn(1, 5)
            val multiplier = 1L shl exponent
            return (normalizeReassertWatchdogInterval(intervalMs) * multiplier)
                .coerceAtMost(MAX_REASSERT_RETRY_INTERVAL_MS)
        }

        /**
         * Known OneOS processes which can sit behind the ECarX DIM facade or consume its result.
         * Grants are limited to a single normalized artwork URI, not the whole provider.
         */
        private val ARTWORK_URI_GRANT_TARGETS = listOf(
            "android",
            "com.autolink.diminteraction",
            "com.geely.dimservice",
            "android.car.cluster",
            "com.geely.service.oneosapi",
            "com.geely.usbservice",
            "com.geely.radio.service",
            "com.geely.mediacenterservice",
            "com.geely.mediawidget",
            "com.android.launcher3",
            "com.android.systemui",
            "com.tencent.wecarflow",
        )

        internal fun qnxCoverWirePath(sharedFile: File): String {
            require(sharedFile.parentFile?.absolutePath == NFS_SHARED_DIR) {
                "Artwork must be inside $NFS_SHARED_DIR"
            }
            return "/${sharedFile.name}"
        }

        internal fun qnxCoverUriString(sharedFile: File): String =
            "file://${qnxCoverWirePath(sharedFile)}"

        internal fun radioFacadeArtworkUri(sharedFile: File?, fallbackUri: Uri?): Uri? =
            sharedFile?.let(Uri::fromFile) ?: fallbackUri

        private const val DIM_TRANSPORT_MARKER = "atlas_dim=online-qnx-owned-v9"

        /**
         * DIM caches artwork paths by the complete Uri string. A stable query marker
         * separates the device-independent ONLINE transport from previous USB tests,
         * while AndroidX FileProvider still resolves the same encoded path.
         */
        internal fun dimTransportUriString(uriString: String): String {
            if (uriString.contains(DIM_TRANSPORT_MARKER)) return uriString
            val separator = if ('?' in uriString) '&' else '?'
            return "$uriString$separator$DIM_TRANSPORT_MARKER"
        }

        /**
         * DIMInteraction V9.03 deliberately replaces artwork with "-999" for FM/AM.
         * ONLINE preserves our text and lets DIM convert the shared Android file into
         * the /images path consumed by QNX. This changes only the cluster presentation;
         * OneOS audio remains RADIO.
         */
        internal fun displaySourceType(radioSourceType: Int, hasArtwork: Boolean): Int =
            if (hasArtwork) IMediaInteraction.SOURCE_TYPE_ONLINE else radioSourceType
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reassertWatchdogIntervalStore = ReassertWatchdogIntervalStore(prefs)
    private val reassertBurstIntervalStore = ReassertBurstIntervalStore(prefs)

    @Volatile
    var isClusterCoversEnabled: Boolean = prefs.getBoolean(KEY_CLUSTER_COVERS_ENABLED, true)
        private set

    @Volatile
    var isClusterRadioFacadeEnabled: Boolean =
        prefs.getBoolean(KEY_CLUSTER_RADIO_FACADE_ENABLED, false)
        private set

    @Volatile
    var isClusterOnlineEnabled: Boolean = prefs.getBoolean(KEY_CLUSTER_ONLINE_ENABLED, false)
        private set

    // Kept in the settings snapshot for compatibility; progress now follows ONLINE transmission.
    val isClusterOnlineProgressEnabled: Boolean
        get() = isClusterOnlineEnabled

    private var activeSource: BridgeAudioSource? = null
    private var confirmedOnlineActive = false
    private var currentRadioInfo: IMediaInteraction.IPlaybackInfo? = null
    private var lastOnlinePayload: DirectDimMediaClient.Payload? = null
    private var lastOnlineProgress = -1L

    private data class OnlineProgressState(
        val positionMs: Long,
        val durationMs: Long,
        val speed: Float,
        val updateElapsedRealtime: Long,
        val playing: Boolean,
    )

    @Volatile
    private var onlineProgressState: OnlineProgressState? = null

    val reassertWatchdogIntervalMs: Long
        get() = reassertWatchdogIntervalStore.value

    val reassertBurstIntervalMs: Long
        get() = reassertBurstIntervalStore.value

    @Volatile
    private var lastUpdate = "none"

    @Volatile
    private var lastUpdateError = ""

    @Volatile
    private var lastArtworkFilePath = ""

    @Volatile
    private var lastArtworkWirePath = ""

    @Volatile
    private var lastArtworkQnxPath = ""

    @Volatile
    private var lastArtworkGrantReport = ""

    @Volatile
    private var dimInitializationError = ""

    @Volatile
    private var dimAvailable = false

    @Volatile
    private var radioActive = false

    private val sendCount = AtomicInteger(0)

    private val directDimMediaClient = DirectDimMediaClient(context)

    init {
        if (
            prefs.contains(KEY_CLUSTER_ONLINE_PROGRESS_ENABLED) ||
            prefs.contains(KEY_LEGACY_CLUSTER_ONLINE_FACADE_PROGRESS_ENABLED)
        ) {
            prefs.edit()
                .remove(KEY_CLUSTER_ONLINE_PROGRESS_ENABLED)
                .remove(KEY_LEGACY_CLUSTER_ONLINE_FACADE_PROGRESS_ENABLED)
                .apply()
        }
        directDimMediaClient.setTransmissionEnabled(isClusterCoversEnabled || isClusterOnlineEnabled)
    }

    private val reassertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val onlineProgressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var onlineProgressJob: Job? = null
    private val reassertLock = Any()
    private var reassertJob: Job? = null
    private var duplicateRepairJob: Job? = null
    private var reassertPayloadKey = ""
    private val reassertGeneration = AtomicInteger(0)

    private val dimInteraction: DimInteraction? by lazy {
        runCatching {
            DimInteraction.create(context)
        }.onFailure {
            dimInitializationError = it.diagnosticMessage()
            Timber.w(it, "DimInteraction initialization failed")
        }.getOrNull()
    }

    /**
     * Controls the complete custom radio DIM producer. When disabled, no metadata or
     * artwork packets are emitted so the stock radio remains the only DIM owner.
     */
    @Synchronized
    fun setClusterCoversEnabled(enabled: Boolean) {
        isClusterCoversEnabled = enabled
        prefs.edit().putBoolean(KEY_CLUSTER_COVERS_ENABLED, enabled).apply()
        directDimMediaClient.setTransmissionEnabled(isClusterCoversEnabled || isClusterOnlineEnabled)
        if (!enabled) {
            if (radioActive) directDimMediaClient.clearPending()
            currentRadioInfo = null
            cancelReassertions()
        }
    }

    @Synchronized
    fun setClusterRadioFacadeEnabled(enabled: Boolean) {
        if (isClusterRadioFacadeEnabled == enabled) return
        isClusterRadioFacadeEnabled = enabled
        prefs.edit().putBoolean(KEY_CLUSTER_RADIO_FACADE_ENABLED, enabled).apply()
        if (radioActive) directDimMediaClient.clearPending()
        currentRadioInfo = null
        cancelReassertions()
    }

    /**
     * Changes the adaptive watchdog base interval. Each steady delay adds random
     * +/- jitter so periodic stock refreshes cannot remain phase-locked with Atlas.
     */
    fun setReassertWatchdogIntervalMs(intervalMs: Long) {
        reassertWatchdogIntervalStore.set(intervalMs)
    }

    /** Changes the base interval used by the bounded startup and duplicate-repair bursts. */
    fun setReassertBurstIntervalMs(intervalMs: Long) {
        reassertBurstIntervalStore.set(intervalMs)
    }

    fun isDimAvailable(): Boolean {
        return dimAvailable
    }

    fun getStatus(): Status {
        val directStatus = directDimMediaClient.status()
        return Status(
            available = dimAvailable,
            initializationError = dimInitializationError,
            lastUpdate = lastUpdate,
            lastUpdateError = lastUpdateError,
            artworkFilePath = lastArtworkFilePath,
            artworkWirePath = lastArtworkWirePath,
            artworkQnxPath = lastArtworkQnxPath,
            artworkGrantReport = lastArtworkGrantReport,
            sendCount = sendCount.get(),
            directDimBound = directStatus.bound,
            directDimSendCount = directStatus.sendCount,
            directDimLastResult = directStatus.lastResult,
            directDimLastError = directStatus.lastError,
        )
    }

    @Synchronized
    fun setActiveSource(source: BridgeAudioSource?) {
        if (activeSource == source) return
        activeSource = source
        val active = source == BridgeAudioSource.RADIO
        confirmedOnlineActive = source == BridgeAudioSource.ONLINE
        lastOnlinePayload = null
        lastOnlineProgress = -1L
        onlineProgressState = null
        if (!confirmedOnlineActive) stopOnlineProgress()
        currentRadioInfo = null
        directDimMediaClient.clearPending()
        radioActive = active
        if (!active) {
            cancelReassertions()
        }
    }

    @Synchronized
    fun updateRadioPlayback(
        frequencyKHz: Int,
        band: Int,
        stationName: String,
        isPlaying: Boolean,
        coverFile: File? = null,
        coverUri: Uri? = null,
    ) {
        if (!isClusterCoversEnabled || !radioActive) {
            return
        }
        val mediaInteraction = runCatching {
            dimInteraction?.mediaInteraction
        }.onFailure {
            dimAvailable = false
            dimInitializationError = it.diagnosticMessage()
            lastUpdateError = it.diagnosticMessage()
            Timber.e(it, "Failed to initialize cluster DIM media interaction")
        }.getOrNull()
        if (mediaInteraction == null) {
            Timber.d("Cluster DIM mediaInteraction is null, skipping cluster update")
            return
        }

        runCatching {
            val isAm = band == 2 || (frequencyKHz in 500..1800)
            val formattedFreq = if (isAm) {
                "$frequencyKHz"
            } else {
                val mhz = if (frequencyKHz > 50000) frequencyKHz / 1000f else frequencyKHz / 100f
                String.format(Locale.US, "%.1f", mhz)
            }

            var clusterArtworkUri: Uri? = null
            var clusterArtworkFile: File? = null
            var artworkGrantReport = "none"
            if (isClusterCoversEnabled && coverFile != null && coverFile.exists()) {
                coverFile.setReadable(true, false)

                val nfsSharedDir = File(NFS_SHARED_DIR)
                if (nfsSharedDir.exists() && nfsSharedDir.isDirectory && nfsSharedDir.canWrite()) {
                    val nfsTarget = copyArtworkAtomically(coverFile, nfsSharedDir)
                    clusterArtworkFile = nfsTarget
                } else {
                    Timber.w("Cluster NFS share is unavailable or read-only: %s", nfsSharedDir)
                }
            }
            if (isClusterCoversEnabled && coverUri != null) {
                // Stock Android media APIs transport artwork as a content URI. Let the
                // privileged ECarX backend open the normalized JPEG and perform its own
                // Android -> QNX conversion instead of guessing the backend's wire path.
                clusterArtworkUri = Uri.parse(dimTransportUriString(coverUri.toString()))
                artworkGrantReport = grantArtworkReadAccess(clusterArtworkUri)
            } else if (isClusterCoversEnabled && clusterArtworkFile != null) {
                // Fallback for callers which only have a file. Two earlier firmware tests
                // showed that this representation is accepted, although not rendered.
                clusterArtworkUri = Uri.parse(qnxCoverUriString(clusterArtworkFile))
                artworkGrantReport = "fallback-file-uri"
            }

            val radioSourceType = if (isAm) IMediaInteraction.SOURCE_TYPE_AM else IMediaInteraction.SOURCE_TYPE_FM
            val displaySourceType = displaySourceType(radioSourceType, clusterArtworkUri != null)
            val playbackArtworkUri = if (isClusterRadioFacadeEnabled) {
                radioFacadeArtworkUri(clusterArtworkFile, clusterArtworkUri)
            } else {
                clusterArtworkUri
            }

            val playInfo = ClusterRadioPlaybackInfo(
                "atlas-radio:$band:$frequencyKHz",
                displaySourceType,
                formattedFreq,
                stationName,
                stationName,
                if (isAm) "$formattedFreq kHz" else "$formattedFreq MHz",
                if (isAm) "AM Radio" else "FM Radio",
                0L,
                if (isPlaying) {
                    IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PLAYING
                } else {
                    IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PAUSED
                },
                IMediaInteraction.IPlaybackInfo.RADIO_MODE_PLAYING,
                playbackArtworkUri,
            )

            currentRadioInfo = playInfo
            sendToDim(
                mediaInteraction = mediaInteraction,
                playInfo = playInfo,
                formattedFreq = formattedFreq,
                stationName = stationName,
                radioSourceType = radioSourceType,
                displaySourceType = displaySourceType,
                artworkUri = clusterArtworkUri,
                artworkFile = clusterArtworkFile,
                artworkGrantReport = artworkGrantReport,
                attempt = "initial",
            )
            scheduleReassertions(
                mediaInteraction = mediaInteraction,
                playInfo = playInfo,
                formattedFreq = formattedFreq,
                stationName = stationName,
                radioSourceType = radioSourceType,
                displaySourceType = displaySourceType,
                artworkUri = clusterArtworkUri,
                artworkFile = clusterArtworkFile,
                artworkGrantReport = artworkGrantReport,
                payloadKey = listOf(
                    frequencyKHz,
                    band,
                    stationName,
                    isPlaying,
                    clusterArtworkUri,
                ).joinToString("|"),
            )
        }.onFailure {
            dimAvailable = false
            dimInitializationError = it.diagnosticMessage()
            lastUpdateError = it.diagnosticMessage()
            Timber.e(it, "Failed to update cluster DIM radio playback info")
        }
    }

    @Synchronized
    fun setClusterOnlineEnabled(enabled: Boolean) {
        isClusterOnlineEnabled = enabled
        prefs.edit().putBoolean(KEY_CLUSTER_ONLINE_ENABLED, enabled).apply()
        lastOnlinePayload = null
        if (!enabled) {
            lastOnlineProgress = -1L
            onlineProgressState = null
            stopOnlineProgress()
            if (confirmedOnlineActive) directDimMediaClient.clearPending()
        }
        directDimMediaClient.setTransmissionEnabled(isClusterCoversEnabled || enabled)
        if (enabled) ensureOnlineProgress()
    }

    /** Sends only resolved ONLINE snapshots; synthetic UNKNOWN/OTHER sessions are excluded. */
    @Synchronized
    fun updateOnlinePlayback(snapshot: MediaSnapshot, coverFile: File?) {
        if (!isClusterOnlineEnabled || !confirmedOnlineActive || !snapshot.backendConnected ||
            snapshot.audioSource != BridgeAudioSource.ONLINE.name || snapshot.ownerPackage.isBlank() ||
            snapshot.mediaId.isBlank()
        ) {
            if (lastOnlinePayload != null) directDimMediaClient.clearPending()
            lastOnlinePayload = null
            lastOnlineProgress = -1L
            onlineProgressState = null
            stopOnlineProgress()
            return
        }
        onlineProgressState = OnlineProgressState(
            positionMs = snapshot.position,
            durationMs = snapshot.duration,
            speed = snapshot.speed,
            updateElapsedRealtime = snapshot.updateElapsedRealtime,
            playing = snapshot.playbackState == PlaybackState.STATE_PLAYING,
        )
        val artworkFile = coverFile?.takeIf { it.isFile }?.let { source ->
            val shared = File(NFS_SHARED_DIR)
            if (shared.isDirectory && shared.canWrite()) copyArtworkAtomically(source, shared) else source
        }
        val payload = DirectDimMediaClient.Payload(
            sourceType = IMediaInteraction.SOURCE_TYPE_ONLINE,
            uuid = "atlas-online:${snapshot.ownerPackage}:${snapshot.mediaId}",
            title = snapshot.title, album = snapshot.album, artist = snapshot.artist,
            artworkUri = artworkFile?.let(Uri::fromFile),
            duration = snapshot.duration.coerceAtLeast(0L),
            playbackStatus = if (snapshot.playbackState == PlaybackState.STATE_PLAYING) {
                IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PLAYING
            } else IMediaInteraction.IPlaybackInfo.PLAYBACK_STATUS_PAUSED,
            radioFrequency = "", radioMode = 0, radioStationName = "",
            currentProgress = extrapolateOnlineProgress(
                positionMs = snapshot.position,
                durationMs = snapshot.duration,
                speed = snapshot.speed,
                updateElapsedRealtime = snapshot.updateElapsedRealtime,
                nowElapsedRealtime = SystemClock.elapsedRealtime(),
            ) ?: 0L,
        )
        if (lastOnlinePayload?.let(::onlinePayloadDedupKey) != onlinePayloadDedupKey(payload)) {
            stopOnlineProgress()
            val result = sendOnlineFacadePayload(payload)
            if (!result.startsWith("send-failed")) {
                lastOnlinePayload = payload
                lastOnlineProgress = payload.currentProgress
            }
            lastUpdate = "${System.currentTimeMillis()}: ONLINE / ${snapshot.title} / $result"
            if (!result.startsWith("send-failed")) {
                lastUpdateError = ""
            }
        }
        if (onlineProgressState?.playing == true) ensureOnlineProgress() else stopOnlineProgress()
    }

    @Synchronized
    private fun ensureOnlineProgress() {
        if (!isClusterOnlineEnabled || !confirmedOnlineActive ||
            onlineProgressState?.playing != true || lastOnlinePayload == null ||
            onlineProgressJob?.isActive == true
        ) return
        onlineProgressJob = onlineProgressScope.launch {
            delay(ONLINE_PROGRESS_INTERVAL_MS)
            while (isActive) {
                val state = onlineProgressState ?: break
                if (!state.playing) break
                val progress = extrapolateOnlineProgress(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    speed = state.speed,
                    updateElapsedRealtime = state.updateElapsedRealtime,
                    nowElapsedRealtime = SystemClock.elapsedRealtime(),
                )
                if (progress != null) {
                    sendOnlineProgress(progress)
                }
                delay(ONLINE_PROGRESS_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    private fun sendOnlineProgress(progress: Long) {
        if (!isClusterOnlineEnabled || !confirmedOnlineActive ||
            onlineProgressState?.playing != true || lastOnlinePayload == null || lastOnlineProgress == progress
        ) return

        val result = runCatching {
            dimInteraction?.mediaInteraction?.updateCurrentProgress(progress)
                ?: error("DIM mediaInteraction is unavailable")
            "facade-progress"
        }.getOrElse { error ->
            lastUpdateError = error.diagnosticMessage()
            Timber.e(error, "Failed to update facade DIM online progress")
            "send-failed:$lastUpdateError"
        }
        if (!result.startsWith("send-failed")) {
            lastOnlineProgress = progress
            lastUpdateError = ""
        }
        lastUpdate = "${System.currentTimeMillis()}: ONLINE progress=$progress / $result"
    }

    private fun sendOnlineFacadePayload(payload: DirectDimMediaClient.Payload): String = runCatching {
        val mediaInteraction = dimInteraction?.mediaInteraction
            ?: error("DIM mediaInteraction is unavailable")
        mediaInteraction.updatePlaybackInfo(
            ClusterRadioPlaybackInfo(
                payload.uuid,
                payload.sourceType,
                payload.radioFrequency,
                payload.radioStationName,
                payload.title,
                payload.artist,
                payload.album,
                payload.duration,
                payload.playbackStatus,
                payload.radioMode,
                payload.artworkUri,
            ),
        )
        mediaInteraction.updateCurrentProgress(payload.currentProgress)
        dimAvailable = true
        dimInitializationError = ""
        "facade-sent"
    }.getOrElse { error ->
        dimAvailable = false
        dimInitializationError = error.diagnosticMessage()
        lastUpdateError = error.diagnosticMessage()
        Timber.e(error, "Failed to send facade DIM online playback")
        "send-failed:$lastUpdateError"
    }

    @Synchronized
    private fun stopOnlineProgress() {
        onlineProgressJob?.cancel()
        onlineProgressJob = null
    }

    private fun scheduleReassertions(
        mediaInteraction: IMediaInteraction,
        playInfo: IMediaInteraction.IPlaybackInfo,
        formattedFreq: String,
        stationName: String,
        radioSourceType: Int,
        displaySourceType: Int,
        artworkUri: Uri?,
        artworkFile: File?,
        artworkGrantReport: String,
        payloadKey: String,
    ) {
        synchronized(reassertLock) {
            val scheduleKind = reassertScheduleKind(
                currentPayloadKey = reassertPayloadKey,
                currentScheduleActive = reassertJob?.isActive == true,
                incomingPayloadKey = payloadKey,
            )
            if (scheduleKind == ReassertScheduleKind.DUPLICATE_REPAIR) {
                // A duplicate callback is still evidence of a stock DIM write. Repair it,
                // but do not restart the full burst or postpone the steady watchdog.
                val generation = reassertGeneration.get()
                duplicateRepairJob?.cancel()
                duplicateRepairJob = reassertScope.launch {
                    duplicateRepairDelaysMs(reassertBurstIntervalMs).forEachIndexed { index, waitMs ->
                        delay(waitMs)
                        if (!isCurrentReassertion(generation)) return@launch
                        val attempt = "duplicate-repair-${index + 1}"
                        runCatching {
                            sendToDim(
                                mediaInteraction = mediaInteraction,
                                playInfo = playInfo,
                                formattedFreq = formattedFreq,
                                stationName = stationName,
                                radioSourceType = radioSourceType,
                                displaySourceType = displaySourceType,
                                artworkUri = artworkUri,
                                artworkFile = artworkFile,
                                artworkGrantReport = artworkGrantReport,
                                attempt = attempt,
                            )
                        }.onFailure { recordReassertionFailure(it, attempt) }
                    }
                }
                return
            }

            val generation = reassertGeneration.incrementAndGet()
            reassertPayloadKey = payloadKey
            reassertJob?.cancel()
            duplicateRepairJob?.cancel()
            duplicateRepairJob = null
            reassertJob = reassertScope.launch {
                var consecutiveFailures = 0
                reassertBurstDelaysMs(reassertBurstIntervalMs).forEachIndexed { index, waitMs ->
                    delay(waitMs)
                    if (!isCurrentReassertion(generation)) return@launch
                    val attempt = "adaptive-burst-${index + 1}"
                    val failure = runCatching {
                        sendToDim(
                            mediaInteraction = mediaInteraction,
                            playInfo = playInfo,
                            formattedFreq = formattedFreq,
                            stationName = stationName,
                            radioSourceType = radioSourceType,
                            displaySourceType = displaySourceType,
                            artworkUri = artworkUri,
                            artworkFile = artworkFile,
                            artworkGrantReport = artworkGrantReport,
                            attempt = attempt,
                        )
                    }.exceptionOrNull()
                    if (failure == null) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(30)
                        recordReassertionFailure(failure, attempt)
                    }
                }

                // Jitter avoids phase-locking with periodic stock producer refreshes.
                var watchdogAttempt = 1
                while (isCurrentReassertion(generation)) {
                    val waitMs = if (consecutiveFailures == 0) {
                        jitteredReassertWatchdogDelayMs(
                            reassertWatchdogIntervalMs,
                            Random.nextLong(
                                -REASSERT_WATCHDOG_JITTER_MS,
                                REASSERT_WATCHDOG_JITTER_MS + 1L,
                            ),
                        )
                    } else {
                        reassertRetryDelayMs(reassertWatchdogIntervalMs, consecutiveFailures)
                    }
                    delay(waitMs)
                    if (!isCurrentReassertion(generation)) return@launch
                    val attempt = "watchdog-${watchdogAttempt++}"
                    val failure = runCatching {
                        sendToDim(
                            mediaInteraction = mediaInteraction,
                            playInfo = playInfo,
                            formattedFreq = formattedFreq,
                            stationName = stationName,
                            radioSourceType = radioSourceType,
                            displaySourceType = displaySourceType,
                            artworkUri = artworkUri,
                            artworkFile = artworkFile,
                            artworkGrantReport = artworkGrantReport,
                            attempt = attempt,
                        )
                    }.exceptionOrNull()
                    if (failure == null) {
                        consecutiveFailures = 0
                    } else {
                        consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(30)
                        recordReassertionFailure(failure, attempt)
                    }
                }
            }
        }
    }

    private fun isCurrentReassertion(generation: Int): Boolean =
        radioActive && isClusterCoversEnabled && reassertGeneration.get() == generation

    private fun recordReassertionFailure(error: Throwable, attempt: String) {
        dimAvailable = false
        dimInitializationError = error.diagnosticMessage()
        lastUpdateError = error.diagnosticMessage()
        Timber.e(error, "Cluster DIM %s failed; retrying with backoff", attempt)
    }

    private fun cancelReassertions() {
        synchronized(reassertLock) {
            reassertGeneration.incrementAndGet()
            reassertJob?.cancel()
            reassertJob = null
            duplicateRepairJob?.cancel()
            duplicateRepairJob = null
            reassertPayloadKey = ""
        }
    }

    @Synchronized
    private fun sendToDim(
        mediaInteraction: IMediaInteraction,
        playInfo: IMediaInteraction.IPlaybackInfo,
        formattedFreq: String,
        stationName: String,
        radioSourceType: Int,
        displaySourceType: Int,
        artworkUri: Uri?,
        artworkFile: File?,
        artworkGrantReport: String,
        attempt: String,
    ) {
        if (!isClusterCoversEnabled || !radioActive || currentRadioInfo !== playInfo) return
        // ONLINE's worker uses BitmapFactory.decodeFile(uri.path), not ContentResolver.
        // Give it the complete Android NFS path; file:///radio_cover.jpg points at the
        // Android root and can never resolve to /data/vendor/nfs/shared.
        val directArtworkUri = artworkFile?.let(Uri::fromFile) ?: artworkUri
        val directPayload = directArtworkUri?.let { uri ->
            DirectDimMediaClient.Payload(
                sourceType = displaySourceType,
                uuid = playInfo.uuid,
                title = playInfo.title,
                album = playInfo.album,
                artist = playInfo.artist,
                artworkUri = uri,
                duration = playInfo.duration,
                playbackStatus = playInfo.playbackStatus,
                radioFrequency = playInfo.radioFrequency,
                radioMode = playInfo.radioMode,
                radioStationName = playInfo.radioStationName,
            )
        }
        val directDimResult = if (isClusterRadioFacadeEnabled) {
            "facade-forced"
        } else {
            directPayload?.let(directDimMediaClient::sendOrQueue) ?: "not-used"
        }
        val qnxArtworkPath = artworkFile?.let(::qnxCoverWirePath).orEmpty()
        val directDimSent = directDimResult.startsWith("sent-")
        var sourceUpdateError: Throwable? = null
        if (isClusterRadioFacadeEnabled || !directDimSent) {
            // Forced facade mode repeats both calls during repair bursts and watchdog ticks
            // so a later stock-radio publication can be overwritten by the same full card.
            sourceUpdateError = runCatching {
                mediaInteraction.updateCurrentSourceType(displaySourceType)
            }.exceptionOrNull()
            if (sourceUpdateError != null) {
                Timber.w(sourceUpdateError, "Cluster DIM source update failed; still sending playback info")
            }
            mediaInteraction.updatePlaybackInfo(playInfo)
        }
        dimAvailable = true
        dimInitializationError = ""
        lastArtworkFilePath = artworkFile?.absolutePath.orEmpty()
        lastArtworkWirePath = directArtworkUri?.toString().orEmpty()
        lastArtworkQnxPath = qnxArtworkPath
        lastArtworkGrantReport = artworkGrantReport
        sendCount.incrementAndGet()
        lastUpdate =
            "${System.currentTimeMillis()}: $formattedFreq / $stationName / " +
                "audioSource=$radioSourceType / displaySource=$displaySourceType / $attempt"
        lastUpdateError = sourceUpdateError?.let {
            "source update warning: ${it.diagnosticMessage()}"
        }.orEmpty()
        Timber.i(
            "Cluster DIM playback sent: freq=%s, name=%s, audioSource=%d, displaySource=%d, artwork=%s, direct=%s, attempt=%s",
            formattedFreq,
            stationName,
            radioSourceType,
            displaySourceType,
            artworkUri,
            directDimResult,
            attempt,
        )
    }

    private fun grantArtworkReadAccess(uri: Uri): String {
        if (uri.scheme != "content") return "not-content-uri"

        val granted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        ARTWORK_URI_GRANT_TARGETS.forEach { packageName ->
            runCatching {
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onSuccess {
                granted += packageName
            }.onFailure { error ->
                failed += "$packageName:${error.javaClass.simpleName}"
                Timber.d(error, "Unable to grant cluster artwork URI to %s", packageName)
            }
        }
        return buildString {
            append("granted=")
            append(if (granted.isEmpty()) "none" else granted.joinToString(","))
            if (failed.isNotEmpty()) {
                append("; failed=")
                append(failed.joinToString(","))
            }
        }
    }

    private fun copyArtworkAtomically(source: File, sharedDir: File): File {
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val suffix = digest.digest().take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val target = File(sharedDir, "radio_cover_$suffix.jpg")
        if (target.isFile && target.length() == source.length()) {
            target.setReadable(true, false)
            return target
        }

        val temporary = File(sharedDir, ".radio_cover_$suffix.${android.os.Process.myPid()}.tmp")
        try {
            source.copyTo(temporary, overwrite = true)
            temporary.setReadable(true, false)
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            target.setReadable(true, false)
            return target
        } finally {
            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    private fun Throwable.diagnosticMessage(): String {
        val cause = generateSequence(this) { it.cause }.last()
        return "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}"
    }
}
