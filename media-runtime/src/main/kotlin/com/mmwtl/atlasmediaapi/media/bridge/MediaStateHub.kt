package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.bean.MediaData
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import com.autolink.carplay.common.data.iap.NowPlayingInfo
import com.mmwtl.atlasmediaapi.R
import com.mmwtl.atlasmediaapi.media.usb.UsbArtworkResolver
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Reduces Android MediaSession and OneOS callbacks into the single public snapshot. */
class MediaStateHub(
    private val context: Context,
    private val repository: MediaStateRepository,
    private val artworkRepository: ArtworkRepository,
    private val radioCatalogRepository: RadioCatalogRepository? = null,
    private val clusterMediaBridge: com.mmwtl.atlasmediaapi.media.cluster.ClusterMediaBridge? = null,
    private val carPlayArtworkProvider: () -> NormalizedArtwork? = { null },
    private val onActiveSourceLost: (lostSource: BridgeAudioSource, wasPlaying: Boolean) -> Unit = { _, _ -> },
) {
    private data class CachedRadioArtwork(
        val mediaId: String,
        val token: String,
        val uri: String,
    )

    private val latestArtworkRequest = AtomicLong()
    private val cachedRadioArtwork = AtomicReference<CachedRadioArtwork?>()
    private val onlineSourcePolicy = OnlineMediaSourcePolicy()
    private val cpaaArtworkFallback = CpaaArtworkFallback(repository, artworkRepository)
    private val usbArtworkResolver = UsbArtworkResolver(context)
    private val mediaCallbackLock = Any()
    private var lastPlayingRealtimeMs: Long = 0L
    private var lastRadioFrequency: Frequency? = null
    private var lastRadioPlaying: Boolean = false

    private fun markPlayingIfActive(isPlaying: Boolean) {
        if (isPlaying) {
            lastPlayingRealtimeMs = android.os.SystemClock.elapsedRealtime()
        }
    }

    fun onBackendConnecting() {
        repository.update {
            it.copy(
                backendConnected = false,
                backendErrorCode = MediaBridgeContract.BackendError.CONNECTING,
                backendErrorMessage = "OneOS MediaCenter connecting",
            )
        }
        cpaaArtworkFallback.onBridgeStateChanged()
    }

    fun onBackendConnected(
        audioSource: MediaCenterConstant.AudioSource,
        appSource: MediaCenterConstant.AppSource,
        availability: Map<BridgeAudioSource, Pair<Boolean, Boolean>>,
    ) {
        val selected = audioSource.toBridgeSource()
        clusterMediaBridge?.setActiveSource(selected)
        onlineSourcePolicy.onAudioSource(selected)
        val cachedCarPlay = if (selected == BridgeAudioSource.CPAA) carPlayArtworkProvider() else null
        repository.update { before ->
            val sourceChanged = before.audioSource != selected.name
            val newArtworkUri = when {
                cachedCarPlay != null && cachedCarPlay.uri.isNotBlank() -> cachedCarPlay.uri
                sourceChanged -> ""
                else -> before.artworkUri
            }
            before.copy(
                backendConnected = true,
                backendErrorCode = MediaBridgeContract.BackendError.NONE,
                backendErrorMessage = "",
                audioSource = selected.name,
                appSource = appSource.name,
                ownerPackage = if (sourceChanged) ownerPackageFor(selected) else before.ownerPackage,
                ownerApp = if (sourceChanged) ownerLabelFor(selected) else before.ownerApp,
                capabilities = if (sourceChanged) selected.defaultCapabilities() else before.capabilities,
                sources = before.sources.map { source ->
                    val state = availability[runCatching {
                        BridgeAudioSource.valueOf(source.id)
                    }.getOrDefault(BridgeAudioSource.UNKNOWN)]
                    source.copy(
                        connected = state?.first ?: source.connected,
                        available = state?.second ?: source.available,
                        selected = source.id == selected.name,
                    )
                },
                artworkUri = newArtworkUri,
                artworkRevision = if (newArtworkUri != before.artworkUri && (sourceChanged || newArtworkUri.isNotBlank())) {
                    before.artworkRevision + 1L
                } else before.artworkRevision,
            )
        }
        cpaaArtworkFallback.onBridgeStateChanged()
    }

    fun onBackendDisconnected(message: String) {
        synchronized(mediaCallbackLock) {
            clusterMediaBridge?.setActiveSource(null)
            val before = repository.snapshot()
            val preserveAndroidOnline = before.audioSource == BridgeAudioSource.ONLINE.name &&
                before.ownerPackage.isNotBlank() &&
                !onlineSourcePolicy.acceptOneOs(BridgeAudioSource.ONLINE, meaningful = true)
            if (!preserveAndroidOnline) {
                latestArtworkRequest.incrementAndGet()
                onlineSourcePolicy.onSessionGone()
            }
            repository.update {
                val base = if (preserveAndroidOnline) it else it.copy(
                    ownerPackage = "",
                    ownerApp = "",
                    mediaId = "",
                    title = "",
                    artist = "",
                    album = "",
                    duration = -1L,
                    position = -1L,
                    updateElapsedRealtime = 0L,
                    speed = 0f,
                    playbackState = PlaybackState.STATE_NONE,
                    playbackErrorCode = 0,
                    playbackErrorMessage = "",
                    playbackActions = 0L,
                    capabilities = MediaCapabilities.SET_SOURCE,
                    artworkUri = "",
                    artworkRevision = if (it.artworkUri.isNotBlank()) it.artworkRevision + 1L else it.artworkRevision,
                )
                base.copy(
                    backendConnected = false,
                    backendErrorCode = MediaBridgeContract.BackendError.ONE_OS_DISCONNECTED,
                    backendErrorMessage = message,
                    sources = it.sources.map { source ->
                        source.copy(
                            connected = false,
                            available = false,
                            selected = preserveAndroidOnline && source.id == BridgeAudioSource.ONLINE.name,
                        )
                    },
                )
            }
            if (!preserveAndroidOnline) cpaaArtworkFallback.onBridgeStateChanged()
        }
    }

    fun onSourceChanged(
        audioSource: MediaCenterConstant.AudioSource,
        appSource: MediaCenterConstant.AppSource,
    ) {
        val selected = audioSource.toBridgeSource()
        clusterMediaBridge?.setActiveSource(selected)
        onlineSourcePolicy.onAudioSource(selected)
        val cachedCarPlay = if (selected == BridgeAudioSource.CPAA) carPlayArtworkProvider() else null
        repository.update { before ->
            val sourceChanged = before.audioSource != selected.name
            val newArtworkUri = when {
                cachedCarPlay != null && cachedCarPlay.uri.isNotBlank() -> cachedCarPlay.uri
                sourceChanged -> ""
                else -> before.artworkUri
            }
            before.copy(
                audioSource = selected.name,
                appSource = appSource.name,
                sources = before.sources.map { source ->
                    source.copy(selected = source.id == selected.name)
                },
                ownerPackage = if (sourceChanged) ownerPackageFor(selected) else before.ownerPackage,
                ownerApp = if (sourceChanged) ownerLabelFor(selected) else before.ownerApp,
                mediaId = if (sourceChanged) "" else before.mediaId,
                title = if (sourceChanged) "" else before.title,
                artist = if (sourceChanged) "" else before.artist,
                album = if (sourceChanged) "" else before.album,
                duration = if (sourceChanged) -1L else before.duration,
                position = if (sourceChanged) -1L else before.position,
                updateElapsedRealtime = if (sourceChanged) 0L else before.updateElapsedRealtime,
                speed = if (sourceChanged) 0f else before.speed,
                playbackState = if (sourceChanged) PlaybackState.STATE_NONE else before.playbackState,
                playbackErrorCode = if (sourceChanged) 0 else before.playbackErrorCode,
                playbackErrorMessage = if (sourceChanged) "" else before.playbackErrorMessage,
                playbackActions = if (sourceChanged) 0L else before.playbackActions,
                capabilities = if (sourceChanged) selected.defaultCapabilities() else before.capabilities,
                artworkUri = newArtworkUri,
                artworkRevision = if (newArtworkUri != before.artworkUri && (sourceChanged || newArtworkUri.isNotBlank())) {
                    before.artworkRevision + 1L
                } else before.artworkRevision,
            )
        }
        cpaaArtworkFallback.onBridgeStateChanged()
        if (repository.snapshot().artworkUri.isBlank()) latestArtworkRequest.incrementAndGet()
    }

    fun onSourceAvailability(
        source: MediaCenterConstant.AudioSource,
        connected: Boolean,
        available: Boolean,
    ) {
        val bridgeSource = source.toBridgeSource()
        val snapshot = repository.snapshot()
        val wasActive = snapshot.audioSource == bridgeSource.name
        val now = android.os.SystemClock.elapsedRealtime()
        val wasRecentlyPlaying = (now - lastPlayingRealtimeMs) in 0..2500L
        val wasPlaying = (snapshot.playbackState == PlaybackState.STATE_PLAYING) || wasRecentlyPlaying
        val lost = wasActive && (!connected || !available)

        repository.update {
            it.copy(
                sources = it.sources.map { item ->
                    if (item.id == bridgeSource.name) {
                        item.copy(connected = connected, available = available)
                    } else item
                },
            )
        }

        if (lost) {
            onActiveSourceLost(bridgeSource, wasPlaying)
        }
    }

    fun onOneOsError(source: MediaCenterConstant.AudioSource, message: String) {
        repository.update {
            it.copy(
                backendErrorCode = MediaBridgeContract.BackendError.ONE_OS_ERROR,
                backendErrorMessage = "${source.toBridgeSource().name}: $message",
            )
        }
    }

    fun onMediaController(controller: MediaController?): BridgeAudioSource? {
        synchronized(mediaCallbackLock) {
            return onMediaControllerLocked(controller)
        }
    }

    private fun onMediaControllerLocked(controller: MediaController?): BridgeAudioSource? {
        if (controller == null) {
            val snapshot = repository.snapshot()
            val fallback = onlineSourcePolicy.onSessionGone(snapshot.audioSource.asBridgeAudioSource())
            val wasActiveOnline = snapshot.audioSource == BridgeAudioSource.ONLINE.name
            val hadOwner = snapshot.ownerPackage.isNotBlank()
            val now = android.os.SystemClock.elapsedRealtime()
            val wasRecentlyPlaying = (now - lastPlayingRealtimeMs) in 0..2500L
            val wasPlaying = (snapshot.playbackState == PlaybackState.STATE_PLAYING) || wasRecentlyPlaying

            if (wasActiveOnline) {
                clearPlayback()
                if (hadOwner) {
                    onActiveSourceLost(BridgeAudioSource.ONLINE, wasPlaying)
                }
            }
            return fallback
        }
        val bridgeState = repository.snapshot()
        if (bridgeState.backendConnected && bridgeState.audioSource !in setOf(
                BridgeAudioSource.ONLINE.name,
                BridgeAudioSource.UNKNOWN.name,
                BridgeAudioSource.OTHER.name,
            )
        ) return null

        val metadata = controller.metadata
        val state = controller.playbackState
        val ownerPackage = controller.packageName.orEmpty()
        val title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_TITLE) }
        val artist = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_ARTIST) }
        val mediaId = metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID)
            .ifBlank { stableMediaId(title, artist) }
        val actions = state?.actions ?: 0L
        val meaningful = ownerPackage.isNotBlank() &&
            (title.isNotBlank() || artist.isNotBlank() || metadata.text(
                MediaMetadata.METADATA_KEY_MEDIA_ID,
            ).isNotBlank())
        if (!onlineSourcePolicy.onSession(ownerPackage, meaningful)) return null

        repository.update { before ->
            val sameMedia = before.mediaId == mediaId && before.ownerPackage == ownerPackage
            val targetSource = if (!bridgeState.backendConnected || before.audioSource == BridgeAudioSource.UNKNOWN.name
                || before.audioSource == BridgeAudioSource.OTHER.name
            ) {
                BridgeAudioSource.ONLINE.name
            } else before.audioSource
            before.copy(
                audioSource = targetSource,
                sources = if (targetSource != before.audioSource) {
                    before.sources.map { source ->
                        source.copy(selected = source.id == targetSource)
                    }
                } else before.sources,
                ownerPackage = ownerPackage,
                ownerApp = appLabel(ownerPackage),
                mediaId = mediaId,
                title = title,
                artist = artist,
                album = metadata.text(MediaMetadata.METADATA_KEY_ALBUM),
                duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    ?.takeIf { value -> value > 0L } ?: -1L,
                position = state?.position ?: -1L,
                updateElapsedRealtime = state?.lastPositionUpdateTime
                    ?.takeIf { value -> value > 0L }
                    ?: android.os.SystemClock.elapsedRealtime(),
                speed = state?.playbackSpeed ?: 0f,
                playbackState = state?.state ?: PlaybackState.STATE_NONE,
                playbackErrorCode = 0,
                playbackErrorMessage = state?.errorMessage?.toString().orEmpty(),
                playbackActions = actions,
                capabilities = MediaCapabilities.fromPlaybackActions(actions),
                artworkUri = if (sameMedia) {
                    before.artworkUri
                } else "",
                artworkRevision = if (!sameMedia && before.artworkUri.isNotBlank()) {
                    before.artworkRevision + 1L
                } else before.artworkRevision,
            )
        }
        markPlayingIfActive(state?.state == PlaybackState.STATE_PLAYING)

        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val sourceUri = metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_ART_URI) }
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) }
        normalizeArtwork(ownerPackage, mediaId, bitmap, sourceUri)
        return null
    }

    fun onCpaaMediaController(controller: MediaController?) {
        synchronized(mediaCallbackLock) {
            val metadata = controller?.metadata
            cpaaArtworkFallback.onMediaSession(
                controller?.let {
                    SessionArtworkCandidate(
                        packageName = it.packageName.orEmpty(),
                        track = ArtworkTrackIdentity(
                            mediaId = metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID),
                            title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                                .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_TITLE) },
                            artist = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                                .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_ARTIST) },
                        ),
                        artwork = metadata.artworkInput(),
                    )
                },
            )
        }
    }

    fun onCarPlayNowPlaying(info: NowPlayingInfo) {
        synchronized(mediaCallbackLock) {
            val snapshot = repository.snapshot()
            if (snapshot.audioSource != BridgeAudioSource.CPAA.name) return
            val title = info.mediaItemTitle.orEmpty()
            val artist = info.mediaItemArtist.orEmpty()
            val mediaId = stableMediaId(title, artist)
            val duration = info.mediaItemPlaybackDurationMs.takeIf { it > 0L } ?: -1L
            val position = info.playbackElapsedTimeMs.takeIf { it >= 0L } ?: -1L
            val isPlaying = info.playbackStatus == 1
            val androidState = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            markPlayingIfActive(isPlaying)
            repository.update { before ->
                val sameMedia = before.mediaId == mediaId
                before.copy(
                    ownerPackage = "com.autolink.carplay",
                    ownerApp = BridgeAudioSource.CPAA.name,
                    mediaId = mediaId,
                    title = if (title.isNotBlank()) title else before.title,
                    artist = if (artist.isNotBlank()) artist else before.artist,
                    duration = if (duration > 0L) duration else before.duration,
                    position = if (position >= 0L) position else before.position,
                    updateElapsedRealtime = android.os.SystemClock.elapsedRealtime(),
                    speed = if (isPlaying) 1f else 0f,
                    playbackState = androidState,
                    capabilities = BridgeAudioSource.CPAA.defaultCapabilities(),
                    artworkUri = if (sameMedia) before.artworkUri else before.artworkUri,
                    artworkRevision = before.artworkRevision,
                )
            }
        }
    }

    fun onCarPlayNativeArtwork(artwork: NormalizedArtwork, nowPlaying: NowPlayingInfo?) {
        synchronized(mediaCallbackLock) {
            val snapshot = repository.snapshot()
            if (snapshot.audioSource == BridgeAudioSource.CPAA.name && artwork.uri.isNotBlank()) {
                repository.update { before ->
                    val updatedTitle = if (before.title.isBlank() && nowPlaying != null && !nowPlaying.mediaItemTitle.isNullOrBlank()) {
                        nowPlaying.mediaItemTitle.orEmpty()
                    } else before.title
                    val updatedArtist = if (before.artist.isBlank() && nowPlaying != null && !nowPlaying.mediaItemArtist.isNullOrBlank()) {
                        nowPlaying.mediaItemArtist.orEmpty()
                    } else before.artist
                    val sameUri = before.artworkUri == artwork.uri
                    before.copy(
                        title = updatedTitle,
                        artist = updatedArtist,
                        artworkUri = artwork.uri,
                        artworkRevision = if (sameUri) before.artworkRevision else before.artworkRevision + 1L,
                    )
                }
            }
        }
    }

    fun onOneOsMediaData(source: MediaCenterConstant.AudioSource, data: MediaData?) {
        synchronized(mediaCallbackLock) {
            onOneOsMediaDataLocked(source, data)
        }
    }

    private fun onOneOsMediaDataLocked(source: MediaCenterConstant.AudioSource, data: MediaData?) {
        if (data == null || repository.snapshot().audioSource != source.toBridgeSource().name) return
        val meaningful = !data.id.isNullOrBlank() || !data.name.isNullOrBlank() ||
            !data.artist.isNullOrBlank()
        if (!onlineSourcePolicy.acceptOneOs(source.toBridgeSource(), meaningful)) return
        val ownerPackage = nativeOwnerPackage(source)
        val mediaId = data.id?.takeIf(String::isNotBlank)
            ?: stableMediaId(data.name.orEmpty(), data.artist.orEmpty())
        repository.update { before ->
            val effectiveOwner = ownerPackage.ifBlank { before.ownerPackage }
            val sameMedia = before.mediaId == mediaId && before.ownerPackage == effectiveOwner
            before.copy(
                ownerPackage = effectiveOwner,
                ownerApp = if (ownerPackage.isNotBlank()) nativeOwnerLabel(source) else before.ownerApp,
                mediaId = mediaId,
                title = data.name.orEmpty(),
                artist = data.artist.orEmpty(),
                album = data.albumName.orEmpty(),
                duration = data.duration.takeIf { value -> value > 0L } ?: -1L,
                capabilities = source.toBridgeSource().defaultCapabilities(),
                artworkUri = if (sameMedia) {
                    before.artworkUri
                } else "",
                artworkRevision = if (!sameMedia && before.artworkUri.isNotBlank()) {
                    before.artworkRevision + 1L
                } else before.artworkRevision,
            )
        }
        val artwork: ArtworkInput = when (source) {
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB -> {
                usbArtworkResolver.resolveUsbArtwork(
                    uriString = data.uri.orEmpty(),
                    title = data.name.orEmpty(),
                    artist = data.artist.orEmpty(),
                    album = data.albumName.orEmpty(),
                ) ?: ArtworkInput(data.albumCover, if (isAudioFilePath(data.uri)) data.uri.orEmpty() else "")
            }
            else -> {
                val artworkSourceUri = when {
                    !data.albumCoverUri.isNullOrBlank() -> data.albumCoverUri.orEmpty()
                    else -> ""
                }
                ArtworkInput(data.albumCover, artworkSourceUri)
            }
        }
        if (source == MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA) {
            val cachedCarPlay = carPlayArtworkProvider()
            if (cachedCarPlay != null && cachedCarPlay.uri.isNotBlank()) {
                repository.update { before ->
                    if (before.artworkUri != cachedCarPlay.uri) {
                        before.copy(
                            artworkUri = cachedCarPlay.uri,
                            artworkRevision = before.artworkRevision + 1L,
                        )
                    } else before
                }
            }
            cpaaArtworkFallback.onOneOsTrack(artwork)
        } else {
            normalizeArtwork(ownerPackage, mediaId, artwork.bitmap, artwork.sourceUri)
        }
    }

    fun onOneOsPlayState(
        source: MediaCenterConstant.AudioSource,
        state: MediaCenterConstant.PlayState,
    ) {
        synchronized(mediaCallbackLock) {
            onOneOsPlayStateLocked(source, state)
        }
    }

    private fun onOneOsPlayStateLocked(
        source: MediaCenterConstant.AudioSource,
        state: MediaCenterConstant.PlayState,
    ) {
        if (repository.snapshot().audioSource != source.toBridgeSource().name) return
        if (!onlineSourcePolicy.acceptOneOs(source.toBridgeSource())) return
        val androidState = when (state) {
            MediaCenterConstant.PlayState.MUSIC_STATE_PLAY -> PlaybackState.STATE_PLAYING
            MediaCenterConstant.PlayState.MUSIC_STATE_PAUSE -> PlaybackState.STATE_PAUSED
            MediaCenterConstant.PlayState.MUSIC_STATE_STOP -> PlaybackState.STATE_STOPPED
        }
        markPlayingIfActive(androidState == PlaybackState.STATE_PLAYING)
        repository.update {
            it.copy(
                playbackState = androidState,
                playbackErrorCode = 0,
                playbackErrorMessage = "",
                speed = if (androidState == PlaybackState.STATE_PLAYING) 1f else 0f,
                updateElapsedRealtime = android.os.SystemClock.elapsedRealtime(),
                capabilities = source.toBridgeSource().defaultCapabilities(),
            )
        }
    }

    fun onOneOsProgress(
        source: MediaCenterConstant.AudioSource,
        position: Long,
        duration: Long,
    ) {
        synchronized(mediaCallbackLock) {
            onOneOsProgressLocked(source, position, duration)
        }
    }

    private fun onOneOsProgressLocked(
        source: MediaCenterConstant.AudioSource,
        position: Long,
        duration: Long,
    ) {
        if (repository.snapshot().audioSource != source.toBridgeSource().name) return
        if (!onlineSourcePolicy.acceptOneOs(source.toBridgeSource())) return
        val speed = if (repository.snapshot().playbackState == PlaybackState.STATE_PLAYING) 1f else 0f
        repository.updateProgress(position, duration, speed)
    }

    fun onOneOsRadioState(frequency: Frequency?, playing: Boolean) {
        lastRadioFrequency = frequency
        lastRadioPlaying = playing
        markPlayingIfActive(playing)

        val widgetBroadcastEnabled = radioCatalogRepository?.isWidgetBroadcastEnabled == true
        val catalogStation = frequency?.let { radioCatalogRepository?.lookup(it.frequency) }
        val clusterStation = frequency?.let {
            radioMetadata(
                frequency = it.frequency,
                band = it.band,
                serviceName = resolveRadioServiceName(
                    oneOsServiceName = it.serviceName,
                    catalogServiceName = catalogStation?.name.orEmpty(),
                    catalogBroadcastEnabled = true,
                ),
                ensembleName = it.ensembleName.orEmpty(),
                fallbackTitle = context.getString(R.string.audio_source_radio),
            )
        }
        val widgetStation = if (widgetBroadcastEnabled) {
            clusterStation
        } else {
            frequency?.let {
                radioMetadata(
                    frequency = it.frequency,
                    band = it.band,
                    serviceName = resolveRadioServiceName(
                        oneOsServiceName = it.serviceName,
                        catalogServiceName = catalogStation?.name.orEmpty(),
                        catalogBroadcastEnabled = false,
                    ),
                    ensembleName = it.ensembleName.orEmpty(),
                    fallbackTitle = context.getString(R.string.audio_source_radio),
                )
            }
        }

        val updated = repository.update {
            it.withRadioState(
                station = widgetStation,
                playing = playing,
                elapsedRealtime = android.os.SystemClock.elapsedRealtime(),
                artworkUri = if (widgetBroadcastEnabled) null else "",
            )
        }

        val requestId = latestArtworkRequest.incrementAndGet()
        if (clusterStation != null && widgetStation != null && updated.backendConnected &&
            updated.audioSource == BridgeAudioSource.RADIO.name
        ) {
            val clusterBroadcastEnabled = clusterMediaBridge?.isClusterCoversEnabled == true
            val cachedArtwork = cachedRadioArtwork.get()
                ?.takeIf {
                    it.mediaId == clusterStation.mediaId && artworkRepository.getCacheFile(it.token).exists()
                }
                ?: if (
                    widgetBroadcastEnabled &&
                    updated.mediaId == clusterStation.mediaId &&
                    updated.artworkUri.isNotBlank()
                ) {
                    val token = updated.artworkUri.substringAfterLast("/").substringBeforeLast(".")
                    artworkRepository.getCacheFile(token)
                        .takeIf { it.exists() }
                        ?.let {
                            CachedRadioArtwork(clusterStation.mediaId, token, updated.artworkUri)
                                .also(cachedRadioArtwork::set)
                        }
                } else null

            if (!widgetBroadcastEnabled && !clusterBroadcastEnabled) {
                return
            }

            if (cachedArtwork != null) {
                val coverFile = artworkRepository.getCacheFile(cachedArtwork.token).takeIf { it.exists() }
                if (widgetBroadcastEnabled && updated.artworkUri != cachedArtwork.uri) {
                    repository.update {
                        it.withRadioState(
                            widgetStation,
                            playing,
                            android.os.SystemClock.elapsedRealtime(),
                            artworkUri = cachedArtwork.uri,
                        )
                    }
                }
                if (clusterBroadcastEnabled) {
                    clusterMediaBridge?.updateRadioPlayback(
                        frequencyKHz = frequency.frequency,
                        band = frequency.band,
                        stationName = clusterStation.title,
                        isPlaying = playing,
                        coverFile = coverFile,
                        coverUri = android.net.Uri.parse(cachedArtwork.uri),
                    )
                }
            } else if (catalogStation != null && catalogStation.coverFileName.isNotBlank()) {
                var coverTriggered = false
                val stream = runCatching { radioCatalogRepository?.openCoverStream(catalogStation) }.getOrNull()
                if (stream != null) {
                    val bitmap = runCatching {
                        stream.use { s -> android.graphics.BitmapFactory.decodeStream(s) }
                    }.getOrNull()

                    if (bitmap != null) {
                        coverTriggered = true
                        artworkRepository.normalize(ArtworkInput(bitmap = bitmap)) { normalized ->
                            if (latestArtworkRequest.get() == requestId && normalized.uri.isNotBlank()) {
                                cachedRadioArtwork.set(
                                    CachedRadioArtwork(clusterStation.mediaId, normalized.token, normalized.uri),
                                )
                                if (widgetBroadcastEnabled) {
                                    repository.update { before ->
                                        if (
                                            before.audioSource == BridgeAudioSource.RADIO.name &&
                                            before.mediaId == widgetStation.mediaId
                                        ) {
                                            before.withRadioState(
                                                station = widgetStation,
                                                playing = playing,
                                                elapsedRealtime = android.os.SystemClock.elapsedRealtime(),
                                                artworkUri = normalized.uri,
                                            )
                                        } else before
                                    }
                                }
                                val coverFile = artworkRepository.getCacheFile(normalized.token).takeIf { it.exists() }
                                val coverUri = if (normalized.uri.isNotBlank()) android.net.Uri.parse(normalized.uri) else null
                                if (clusterBroadcastEnabled) {
                                    clusterMediaBridge?.updateRadioPlayback(
                                        frequencyKHz = frequency.frequency,
                                        band = frequency.band,
                                        stationName = clusterStation.title,
                                        isPlaying = playing,
                                        coverFile = coverFile,
                                        coverUri = coverUri,
                                    )
                                }
                            }
                        }
                    }
                }
                if (!coverTriggered && clusterBroadcastEnabled) {
                    clusterMediaBridge?.updateRadioPlayback(
                        frequencyKHz = frequency.frequency,
                        band = frequency.band,
                        stationName = clusterStation.title,
                        isPlaying = playing,
                        coverFile = null,
                        coverUri = null,
                    )
                }
            } else if (clusterBroadcastEnabled) {
                clusterMediaBridge?.updateRadioPlayback(
                    frequencyKHz = frequency.frequency,
                    band = frequency.band,
                    stationName = clusterStation.title,
                    isPlaying = playing,
                    coverFile = null,
                    coverUri = null,
                )
            }
        }
    }

    fun refreshRadioState() {
        if (repository.snapshot().audioSource == BridgeAudioSource.RADIO.name) {
            onOneOsRadioState(lastRadioFrequency, lastRadioPlaying)
        }
    }

    private fun clearPlayback() {
        latestArtworkRequest.incrementAndGet()
        repository.update { before ->
            before.copy(
                ownerPackage = "",
                ownerApp = "",
                mediaId = "",
                title = "",
                artist = "",
                album = "",
                duration = -1L,
                position = -1L,
                updateElapsedRealtime = 0L,
                speed = 0f,
                playbackState = PlaybackState.STATE_NONE,
                playbackErrorCode = 0,
                playbackErrorMessage = "",
                playbackActions = 0L,
                capabilities = MediaCapabilities.SET_SOURCE,
                artworkUri = "",
                artworkRevision = if (before.artworkUri.isNotBlank()) {
                    before.artworkRevision + 1L
                } else before.artworkRevision,
            )
        }
    }

    private fun normalizeArtwork(
        ownerPackage: String,
        mediaId: String,
        bitmap: android.graphics.Bitmap?,
        sourceUri: String,
    ) {
        val input = ArtworkInput(bitmap, sourceUri)
        if (input.bitmap == null && input.sourceUri.isBlank()) return
        val request = latestArtworkRequest.incrementAndGet()
        artworkRepository.normalize(input) { normalized ->
            if (latestArtworkRequest.get() != request) return@normalize
            if (normalized.uri.isBlank()) return@normalize
            val snapshot = repository.snapshot()
            if (snapshot.ownerPackage != ownerPackage || snapshot.mediaId != mediaId) return@normalize
            repository.update {
                val uri = normalized.uri
                if (it.artworkUri == uri) it else it.copy(
                    artworkUri = uri,
                    artworkRevision = it.artworkRevision + 1L,
                )
            }
        }
    }

    private fun appLabel(packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    private fun stableMediaId(title: String, artist: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest("$title\u0000$artist".toByteArray())
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun MediaMetadata?.text(key: String): String = this?.getString(key).orEmpty()

    private fun MediaMetadata?.artworkInput(): ArtworkInput = ArtworkInput(
        bitmap = this?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: this?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: this?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        sourceUri = text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            .ifBlank { text(MediaMetadata.METADATA_KEY_ART_URI) }
            .ifBlank { text(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) },
    )

    private fun nativeOwnerPackage(source: MediaCenterConstant.AudioSource): String = when (source) {
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT -> "com.android.bluetooth"
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB -> "com.geely.usbservice"
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO -> "com.geely.radio.service"
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA -> "com.autolink.carplay"
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_YUNTING -> "com.geely.mediacenterservice"
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_OTHER,
        MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN -> ""
    }

    private fun nativeOwnerLabel(source: MediaCenterConstant.AudioSource): String =
        source.toBridgeSource().name

    private fun ownerPackageFor(source: BridgeAudioSource): String = when (source) {
        BridgeAudioSource.BT -> "com.android.bluetooth"
        BridgeAudioSource.USB -> "com.geely.usbservice"
        BridgeAudioSource.RADIO -> "com.geely.radio.service"
        BridgeAudioSource.CPAA -> "com.autolink.carplay"
        BridgeAudioSource.YUNTING -> "com.geely.mediacenterservice"
        BridgeAudioSource.ONLINE,
        BridgeAudioSource.OTHER,
        BridgeAudioSource.UNKNOWN -> ""
    }

    private fun ownerLabelFor(source: BridgeAudioSource): String =
        if (ownerPackageFor(source).isBlank()) "" else source.name
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "wav", "wma", "ape", "opus", "alac",
)

internal fun isAudioFilePath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val clean = runCatching { Uri.decode(path) }.getOrDefault(path)
        .substringBefore('?').substringBefore('#').trim()
    if (clean.startsWith("content://")) return true
    val ext = clean.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return ext in AUDIO_EXTENSIONS
}

private fun String.asBridgeAudioSource(): BridgeAudioSource? =
    runCatching { BridgeAudioSource.valueOf(this) }.getOrNull()

fun MediaCenterConstant.AudioSource.toBridgeSource(): BridgeAudioSource = when (this) {
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_USB -> BridgeAudioSource.USB
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT -> BridgeAudioSource.BT
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_RADIO -> BridgeAudioSource.RADIO
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE -> BridgeAudioSource.ONLINE
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_OTHER -> BridgeAudioSource.OTHER
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_YUNTING -> BridgeAudioSource.YUNTING
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_CPAA -> BridgeAudioSource.CPAA
    MediaCenterConstant.AudioSource.AUDIO_SOURCE_UNKNOWN -> BridgeAudioSource.UNKNOWN
}
