package com.mmwtl.atlasmediaapi.media.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import com.mmwtl.atlasmediaapi.media.bridge.PlayerSessionSelector
import com.mmwtl.atlasmediaapi.media.bridge.hasPlayerContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Requests playback from a player that has no active MediaSession through its public media
 * entry points, without opening its activity. Callers confirm the result through the session list.
 */
interface BackgroundPlayerStarter {
    /**
     * Connects to the player's MediaBrowserService and sends play to the returned session token.
     * Returns null when the player has no browser service or refuses the connection. Close the
     * result after playback is confirmed or abandoned.
     */
    suspend fun connectAndPlay(packageName: String): AutoCloseable?

    /** Sends an explicit MEDIA_PLAY key to the player's ACTION_MEDIA_BUTTON receiver. */
    fun sendMediaButtonPlay(packageName: String): Boolean

    /**
     * Connects to the player's MediaBrowserService without starting playback, so its session can
     * show the track it would resume. Returns null when the player has no browser service or
     * refuses the connection. With [allowPrepare] a session without a track is asked to prepare.
     */
    suspend fun connectAndPrepare(packageName: String, allowPrepare: Boolean): PreparedPlayer?
}

/**
 * A player session reached through its media browser and, when the session has no track, the
 * track of the player's recent root. Close [connection] to release the browser.
 */
class PreparedPlayer(
    val session: PreparedSession,
    val connection: AutoCloseable,
)

class AndroidBackgroundPlayerStarter(
    private val context: Context,
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
) : BackgroundPlayerStarter {
    companion object {
        // The bind starts the player's process; on a cold head unit boot its Application and
        // service setup can exceed 3 s, and disconnecting then unbinds the service mid-start.
        const val CONNECT_TIMEOUT_MS = 10_000L
        // A prepared player loads its last queue, from disk or the network.
        const val PREPARE_WAIT_MS = 4_000L
        val PREPARE_POLL_DELAYS_MS = listOf(100L, 200L, 300L, 500L)
        const val RECENT_ITEM_TIMEOUT_MS = 3_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun browserComponent(packageName: String): ComponentName? {
        val service = context.packageManager.queryIntentServices(
            Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName),
            0,
        ).firstOrNull()?.serviceInfo ?: run {
            Timber.i("%s has no media browser service", packageName)
            return null
        }
        return ComponentName(service.packageName, service.name)
    }

    override suspend fun connectAndPlay(packageName: String): AutoCloseable? {
        val component = browserComponent(packageName) ?: return null
        Timber.i("Connecting to the media browser %s", component.flattenToShortString())
        val browser = (connect(component, rootHints = null) as? BrowserConnection.Connected)
            ?.browser
            ?: run {
                Timber.w("Media browser of %s refused or timed out", packageName)
                return null
            }
        return withContext(Dispatchers.Main) {
            runCatching {
                Timber.i("Media browser of %s connected; requesting playback", packageName)
                MediaController(context, browser.sessionToken).transportControls.play()
                AutoCloseable { mainHandler.post { runCatching { browser.disconnect() } } }
            }.onFailure {
                Timber.w(it, "Could not request playback through the media browser of $packageName")
                runCatching { browser.disconnect() }
            }.getOrNull()
        }
    }

    override suspend fun connectAndPrepare(
        packageName: String,
        allowPrepare: Boolean,
    ): PreparedPlayer? {
        val component = browserComponent(packageName) ?: return null
        // The Android 11 media resumption hint: a service that supports it answers with a root
        // whose only child is the track it would resume.
        val recentHints = Bundle().apply {
            putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true)
        }
        Timber.i("Connecting to the media browser %s without playback", component.flattenToShortString())
        var hinted = true
        var connection = connect(component, recentHints)
        if (connection is BrowserConnection.Refused) {
            // A service without resumption may refuse the hint instead of ignoring it.
            Timber.i("Media browser of %s refused the recent root; connecting to its root", packageName)
            hinted = false
            connection = connect(component, null)
        }
        val browser = (connection as? BrowserConnection.Connected)?.browser ?: run {
            Timber.w("Media browser of %s refused or timed out", packageName)
            return null
        }
        val close = AutoCloseable { mainHandler.post { runCatching { browser.disconnect() } } }
        return runCatching {
            val token = withContext(Dispatchers.Main) { browser.sessionToken }
            // The service must return the hint in its root extras when the root holds recent media.
            val recentRoot = hinted && withContext(Dispatchers.Main) {
                browser.extras?.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT) == true
            }
            val session = MediaController(context, token)
            var hasTrack = withContext(Dispatchers.IO) { session.metadata.hasPlayerContent() }
            if (!hasTrack && allowPrepare) {
                hasTrack = withContext(Dispatchers.IO) { prepareAndAwaitTrack(packageName, session) }
            }
            val recent = if (!hasTrack && recentRoot) recentTrack(packageName, browser) else null
            Timber.i(
                "Media browser of %s connected without playback (track=%b, recentRoot=%b, recentTrack=%b)",
                packageName,
                hasTrack,
                recentRoot,
                recent != null,
            )
            PreparedPlayer(PreparedSession(session, recent), close)
        }.onFailure {
            Timber.w(it, "Could not read the session of $packageName through its media browser")
            close.close()
        }.getOrNull()
    }

    /**
     * Asks the session to load the queue it would play, without audio focus or playback, and waits
     * for its track. A player that starts playing on prepare is paused again: playback was not
     * requested.
     */
    private suspend fun prepareAndAwaitTrack(packageName: String, session: MediaController): Boolean {
        val wasPlaying = PlayerSessionSelector.isPlaying(session.playbackState?.state)
        Timber.i("Asking %s to prepare its last queue", packageName)
        session.transportControls.prepare()
        val hasTrack = withTimeoutOrNull(PREPARE_WAIT_MS) {
            var poll = 0
            while (!session.metadata.hasPlayerContent()) {
                delay(PREPARE_POLL_DELAYS_MS.getOrElse(poll++) { PREPARE_POLL_DELAYS_MS.last() })
            }
            true
        } ?: false
        if (!wasPlaying && PlayerSessionSelector.isPlaying(session.playbackState?.state)) {
            Timber.w("%s started playing on prepare; pausing it", packageName)
            session.transportControls.pause()
        }
        return hasTrack
    }

    /** The first playable child of a recent root: the track the player would resume. */
    private suspend fun recentTrack(packageName: String, browser: MediaBrowser): MediaMetadata? =
        withContext(Dispatchers.Main) {
            val root = browser.root
            val children = CompletableDeferred<List<MediaBrowser.MediaItem>>()
            val callback = object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, items: List<MediaBrowser.MediaItem>) {
                    children.complete(items)
                }

                override fun onError(parentId: String) {
                    children.complete(emptyList())
                }
            }
            try {
                browser.subscribe(root, callback)
                withTimeoutOrNull(RECENT_ITEM_TIMEOUT_MS) { children.await() }
                    ?.firstOrNull { it.isPlayable }
                    ?.description
                    ?.toRecentMetadata()
                    ?.takeIf { it.hasPlayerContent() }
            } catch (failure: RuntimeException) {
                Timber.w(failure, "Could not read the recent root of $packageName")
                null
            } finally {
                runCatching { browser.unsubscribe(root, callback) }
            }
        }

    private sealed interface BrowserConnection {
        class Connected(val browser: MediaBrowser) : BrowserConnection
        object Refused : BrowserConnection
        object TimedOut : BrowserConnection
    }

    /** MediaBrowser delivers its callbacks on the thread that connects it, here the main one. */
    private suspend fun connect(component: ComponentName, rootHints: Bundle?): BrowserConnection =
        withContext(Dispatchers.Main) {
            val connected = CompletableDeferred<Boolean>()
            val browser = MediaBrowser(
                context,
                component,
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        connected.complete(true)
                    }

                    override fun onConnectionFailed() {
                        connected.complete(false)
                    }

                    override fun onConnectionSuspended() {
                        connected.complete(false)
                    }
                },
                rootHints,
            )
            val result = runCatching {
                browser.connect()
                when (withTimeoutOrNull(connectTimeoutMs.coerceAtLeast(1L)) { connected.await() }) {
                    true -> BrowserConnection.Connected(browser)
                    false -> BrowserConnection.Refused
                    null -> BrowserConnection.TimedOut
                }
            }.onFailure {
                Timber.w(it, "Could not connect to the media browser ${component.flattenToShortString()}")
            }.getOrDefault(BrowserConnection.Refused)
            if (result !is BrowserConnection.Connected) runCatching { browser.disconnect() }
            result
        }

    override fun sendMediaButtonPlay(packageName: String): Boolean {
        val receiver = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName),
            0,
        ).firstOrNull()?.activityInfo ?: run {
            Timber.i("%s has no media button receiver", packageName)
            return false
        }
        val component = ComponentName(receiver.packageName, receiver.name)
        Timber.i("Sending MEDIA_PLAY to %s", component.flattenToShortString())
        return runCatching {
            val eventTime = SystemClock.uptimeMillis()
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON)
                        .setComponent(component)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .putExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent(eventTime, eventTime, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0),
                        ),
                )
            }
        }.onFailure {
            Timber.w(it, "Could not send MEDIA_PLAY to the media button receiver of $packageName")
        }.isSuccess
    }
}

/** The recent item as session metadata; its icon stands in for the artwork. */
internal fun MediaDescription.toRecentMetadata(): MediaMetadata = MediaMetadata.Builder().apply {
    mediaId?.let { putString(MediaMetadata.METADATA_KEY_MEDIA_ID, it) }
    title?.let { putText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, it) }
    subtitle?.let { putText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, it) }
    iconBitmap?.let { putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it) }
    iconUri?.let { putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, it.toString()) }
}.build()
