package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.session.PlaybackState
import android.os.SystemClock
import com.mmwtl.atlasmediaapi.R

/**
 * Deterministic in-process backend for emulator/widget development.
 *
 * It deliberately lives behind the normal Messenger service: clients exercise the same public
 * protocol, URI grants and command/result flow as on a head unit, without attempting to emulate
 * proprietary OneOS binders.
 */
class DemoMediaBackend(
    private val context: Context,
    private val repository: MediaStateRepository,
    private val artworkRepository: ArtworkNormalizer,
    private val radioCatalogRepository: RadioCatalogRepository? = null,
    private val resourceArtworkLoader: (Int) -> Bitmap? = {
        BitmapFactory.decodeResource(context.resources, it)
    },
) {
    private data class Track(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long = 218_000L,
        val color: Int,
        val artworkResId: Int,
    )

    private val tracks = mapOf(
        BridgeAudioSource.USB to listOf(
            Track(
                "Liminal Hours (Extended Night Drive Version)",
                "Northern Signal Department feat. Elena Markova",
                "The Roads We Leave Behind",
                durationMs = 286_000L,
                color = Color.rgb(38, 166, 154),
                artworkResId = R.drawable.demo_neon_drive,
            ),
            Track(
                "Somewhere Between Exit Signs and Morning",
                "Violet Transit Authority",
                "Postcards from the Peripheral",
                durationMs = 243_000L,
                color = Color.rgb(0, 121, 107),
                artworkResId = R.drawable.demo_coastal_dawn,
            ),
        ),
        BridgeAudioSource.BT to listOf(
            Track(
                "If You Hear This, I Made It Home",
                "Mira Nova & The Long Distance Operators",
                "Voice Notes for an Empty Passenger Seat",
                durationMs = 231_000L,
                color = Color.rgb(66, 133, 244),
                artworkResId = R.drawable.demo_coastal_dawn,
            ),
            Track(
                "Static Between Two Familiar Cities",
                "Blue Circuit feat. H. Lumen",
                "Hands Free, Heart Open",
                durationMs = 259_000L,
                color = Color.rgb(25, 118, 210),
                artworkResId = R.drawable.demo_neon_drive,
            ),
        ),
        BridgeAudioSource.ONLINE to listOf(
            Track(
                "Rain on the Windshield, Lights on the Water",
                "Atlas Music Session feat. Kira Vale",
                "Late Night Algorithm: Selected Works",
                durationMs = 274_000L,
                color = Color.rgb(171, 71, 188),
                artworkResId = R.drawable.demo_neon_drive,
            ),
            Track(
                "The Last Train Never Leaves on Time",
                "Signal & Noise Orchestra",
                "Small Weather Systems",
                durationMs = 308_000L,
                color = Color.rgb(123, 31, 162),
                artworkResId = R.drawable.demo_afterhours_studio,
            ),
        ),
        BridgeAudioSource.YUNTING to listOf(
            Track(
                "Clouds Above the Ring Road (Morning Mix)",
                "YunTing Curated Radio feat. Li Wen",
                "A Map of Quiet Places",
                durationMs = 263_000L,
                color = Color.rgb(255, 112, 67),
                artworkResId = R.drawable.demo_coastal_dawn,
            ),
            Track(
                "Windows Down Until the City Wakes Up",
                "YunTing Curated Radio",
                "A Map of Quiet Places",
                durationMs = 249_000L,
                color = Color.rgb(230, 74, 25),
                artworkResId = R.drawable.demo_afterhours_studio,
            ),
        ),
        BridgeAudioSource.CPAA to listOf(
            Track(
                "A Better Route Home (feat. Midnight Radio)",
                "Avery Bloom & The Weekend Forecast",
                "Navigation Mixes, Vol. 4",
                durationMs = 287_000L,
                color = Color.rgb(236, 64, 122),
                artworkResId = R.drawable.demo_afterhours_studio,
            ),
            Track(
                "Take the Long Way Past the Ocean Again",
                "Avery Bloom",
                "Navigation Mixes, Vol. 4",
                durationMs = 297_000L,
                color = Color.rgb(194, 24, 91),
                artworkResId = R.drawable.demo_coastal_dawn,
            ),
        ),
    )

    private val fallbackStations = listOf(
        station("demo:radio:101700", 101_700, "101.7 MHz", "Atlas FM", "Atlas FM", "Pop", true, Color.rgb(255, 112, 67)),
        station("demo:radio:97500", 97_500, "97.5 MHz", "Drive Radio", "Drive Radio", "Rock", true, Color.rgb(255, 193, 7)),
        station("demo:radio:104300", 104_300, "104.3 MHz", "City Wave", "City Wave", "News", false, Color.rgb(3, 169, 244)),
        station("demo:radio:220352", 220_352, "220.352 MHz", "DAB Demo", "DAB Demo", "Digital", false, Color.rgb(124, 179, 66), band = 3),
    )

    private var started = false
    private var source = BridgeAudioSource.RADIO
    private var trackIndex = 0
    private var radioStation = fallbackStations.first()
    private var playing = true
    private var positionMs = 47_000L

    fun start() {
        started = true
        publish()
    }

    fun stop() {
        started = false
    }

    fun radioStationLists(): RadioStationLists {
        val stations = activeRadioStations()
        return RadioStationLists(
            saved = stations,
            // The imported catalog is the explicit demo selection, so expose every imported
            // station as a favorite. This lets a widget validate its real favorites UI.
            favorites = stations.filter(RadioStationSnapshot::favorite),
        )
    }

    fun execute(request: MediaCommandRequest): MediaCommandResult {
        if (!started) return MediaCommandResult(MediaBridgeContract.Status.BACKEND_UNAVAILABLE, "demo backend is stopped")
        when (request.command) {
            MediaCommand.PLAY -> playing = true
            MediaCommand.PAUSE -> playing = false
            MediaCommand.TOGGLE -> playing = !playing
            MediaCommand.NEXT -> selectTrack(+1)
            MediaCommand.PREVIOUS -> selectTrack(-1)
            MediaCommand.SEEK_TO -> {
                if (source == BridgeAudioSource.RADIO) return notSupported(request.command)
                positionMs = request.position!!.coerceIn(0L, currentTrack().durationMs)
            }
            MediaCommand.SET_SOURCE -> {
                val target = request.source ?: return MediaCommandResult(MediaBridgeContract.Status.INVALID_REQUEST, "source is required")
                if (target !in supportedSources) return notSupported(request.command)
                source = target
                trackIndex = 0
                positionMs = 0L
                playing = request.autoplay
            }
            MediaCommand.TUNE_RADIO -> {
                val target = request.radioStation ?: return MediaCommandResult(MediaBridgeContract.Status.INVALID_REQUEST, "station is required")
                source = BridgeAudioSource.RADIO
                radioStation = activeRadioStations().firstOrNull {
                    it.frequencyKHz == target.frequencyKHz && it.band == target.band
                } ?: station(
                    id = "demo:radio:${target.band}:${target.frequencyKHz}",
                    frequencyKHz = target.frequencyKHz,
                    formattedFrequency = formatRadioFrequency(target.frequencyKHz),
                    name = target.serviceName.ifBlank { formatRadioFrequency(target.frequencyKHz) },
                    serviceName = target.serviceName,
                    genre = target.genre,
                    favorite = false,
                    color = Color.rgb(96, 125, 139),
                    band = target.band,
                )
                playing = request.autoplay
            }
        }
        publish()
        return MediaCommandResult(MediaBridgeContract.Status.OK)
    }

    private fun selectTrack(delta: Int) {
        if (source == BridgeAudioSource.RADIO) {
            val stations = activeRadioStations()
            val next = (stations.indexOfFirst { it.id == radioStation.id }.coerceAtLeast(0) + delta)
                .floorMod(stations.size)
            radioStation = stations[next]
        } else {
            val sourceTracks = tracks.getValue(source)
            trackIndex = (trackIndex + delta).floorMod(sourceTracks.size)
        }
        positionMs = 0L
    }

    private fun publish() {
        val isRadio = source == BridgeAudioSource.RADIO
        if (isRadio) {
            radioStation = activeRadioStations().firstOrNull { it.id == radioStation.id }
                ?: activeRadioStations().first()
        }
        val track = if (isRadio) null else currentTrack()
        val title = if (isRadio) radioStation.name else track!!.title
        val artist = if (isRadio) radioStation.formattedFrequency else track!!.artist
        val album = if (isRadio) radioStation.genre else track!!.album
        val color = if (isRadio) colorForStation(radioStation) else track!!.color
        val mediaId = "demo:${source.name}:${if (isRadio) radioStation.id else trackIndex}"
        val actions = playbackActions(isRadio)
        val providedArtworkUri = if (isRadio) radioStation.artworkUri else ""
        repository.update { before ->
            before.copy(
                backendConnected = true,
                backendErrorCode = MediaBridgeContract.BackendError.NONE,
                backendErrorMessage = "",
                audioSource = source.name,
                appSource = "DEMO",
                sources = defaultMediaSources().map { item ->
                    item.copy(
                        connected = item.id in supportedSources.map(BridgeAudioSource::name),
                        available = item.id in supportedSources.map(BridgeAudioSource::name),
                        selected = item.id == source.name,
                    )
                },
                ownerPackage = "com.mmwtl.atlasmediaapi.demo.${source.name.lowercase()}",
                ownerApp = "Atlas demo ${source.name}",
                mediaId = mediaId,
                title = title,
                artist = artist,
                album = album,
                duration = if (isRadio) -1L else track!!.durationMs,
                position = if (isRadio) -1L else positionMs,
                updateElapsedRealtime = SystemClock.elapsedRealtime(),
                speed = if (playing) 1f else 0f,
                playbackState = if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                playbackErrorCode = 0,
                playbackErrorMessage = "",
                playbackActions = actions,
                capabilities = source.defaultCapabilities(),
                artworkUri = providedArtworkUri,
                artworkRevision = if (before.artworkUri != providedArtworkUri) before.artworkRevision + 1 else before.artworkRevision,
            )
        }
        if (providedArtworkUri.isNotBlank()) return
        val cover = artwork(title, artist, color, track?.artworkResId)
        artworkRepository.normalize(ArtworkInput(bitmap = cover)) { normalized ->
            try {
                if (normalized.uri.isBlank()) return@normalize
                repository.update { before ->
                    if (before.mediaId != mediaId) return@update before
                    before.copy(
                        artworkUri = normalized.uri,
                        artworkRevision = if (before.artworkUri == normalized.uri) before.artworkRevision else before.artworkRevision + 1,
                    )
                }
            } finally {
                cover.recycle()
            }
        }
    }

    private fun currentTrack(): Track = tracks.getValue(source)[trackIndex]

    private fun activeRadioStations(): List<RadioStationSnapshot> {
        val catalog = radioCatalogRepository ?: return fallbackStations
        val catalogStations = catalog.stations()
        if (catalogStations.isEmpty()) return fallbackStations
        return catalogStations.map { catalogStation ->
            RadioStationSnapshot(
                id = "demo:catalog:${catalogStation.frequencyKHz}",
                frequencyKHz = catalogStation.frequencyKHz,
                formattedFrequency = formatRadioFrequency(catalogStation.frequencyKHz),
                band = if (catalogStation.band == "AM") 2 else 1,
                bandName = catalogStation.band,
                name = catalogStation.name,
                serviceName = catalogStation.name,
                genre = "Demo favorite",
                favorite = true,
                signalQuality = 100,
                artworkUri = catalog.artworkUri(catalogStation),
            )
        }
    }

    private fun playbackActions(isRadio: Boolean): Long =
        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            (if (isRadio) 0L else PlaybackState.ACTION_SEEK_TO)

    private fun notSupported(command: MediaCommand) = MediaCommandResult(
        MediaBridgeContract.Status.NOT_SUPPORTED,
        "${source.name} does not support $command in demo mode",
    )

    private fun artwork(
        title: String,
        subtitle: String,
        coverColor: Int,
        artworkResId: Int?,
    ): Bitmap {
        artworkResId?.let { resourceId ->
            resourceArtworkLoader(resourceId)?.let { return it }
        }
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(coverColor)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            paint.textSize = 34f
            paint.isFakeBoldText = true
            drawText(ellipsize(title, paint, 452f), 30f, 390f, paint)
            paint.textSize = 22f
            paint.isFakeBoldText = false
            drawText(ellipsize(subtitle, paint, 452f), 30f, 430f, paint)
            paint.textSize = 18f
            drawText("ATLAS DEMO", 30f, 474f, paint)
        }
        return bitmap
    }

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(value) <= maxWidth) return value
        val suffix = "…"
        var end = value.length
        while (end > 0 && paint.measureText(value.take(end) + suffix) > maxWidth) end--
        return value.take(end) + suffix
    }

    private fun colorForStation(station: RadioStationSnapshot): Int = when (station.id) {
        "demo:radio:101700" -> Color.rgb(255, 112, 67)
        "demo:radio:97500" -> Color.rgb(255, 193, 7)
        "demo:radio:104300" -> Color.rgb(3, 169, 244)
        "demo:radio:220352" -> Color.rgb(124, 179, 66)
        else -> Color.rgb(96, 125, 139)
    }

    private fun station(
        id: String,
        frequencyKHz: Int,
        formattedFrequency: String,
        name: String,
        serviceName: String,
        genre: String,
        favorite: Boolean,
        color: Int,
        band: Int = 1,
    ): RadioStationSnapshot = RadioStationSnapshot(
        id = id,
        frequencyKHz = frequencyKHz,
        formattedFrequency = formattedFrequency,
        band = band,
        bandName = resolveRadioBandName(frequencyKHz, band),
        name = name,
        serviceName = serviceName,
        genre = genre,
        favorite = favorite,
        iconId = color,
        signalQuality = 100,
    )

    private companion object {
        val supportedSources = setOf(
            BridgeAudioSource.RADIO,
            BridgeAudioSource.USB,
            BridgeAudioSource.BT,
            BridgeAudioSource.ONLINE,
            BridgeAudioSource.YUNTING,
            BridgeAudioSource.CPAA,
        )

        fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
    }
}
