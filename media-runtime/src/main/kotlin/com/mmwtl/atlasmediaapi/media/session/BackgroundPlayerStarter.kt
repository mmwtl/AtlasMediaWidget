package com.mmwtl.atlasmediaapi.media.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
}

class AndroidBackgroundPlayerStarter(
    private val context: Context,
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
) : BackgroundPlayerStarter {
    companion object {
        const val CONNECT_TIMEOUT_MS = 3_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun connectAndPlay(packageName: String): AutoCloseable? {
        val service = context.packageManager.queryIntentServices(
            Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName),
            0,
        ).firstOrNull()?.serviceInfo ?: return null
        val component = ComponentName(service.packageName, service.name)

        // MediaBrowser delivers its callbacks on the thread that connects it.
        return withContext(Dispatchers.Main) {
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
                null,
            )
            val requested = runCatching {
                browser.connect()
                if (withTimeoutOrNull(connectTimeoutMs.coerceAtLeast(1L)) { connected.await() } != true) {
                    Timber.w("Media browser of %s refused or timed out", packageName)
                    return@runCatching false
                }
                MediaController(context, browser.sessionToken).transportControls.play()
                true
            }.onFailure {
                Timber.w(it, "Could not request playback through the media browser of $packageName")
            }.getOrDefault(false)
            if (!requested) {
                runCatching { browser.disconnect() }
                return@withContext null
            }
            AutoCloseable { mainHandler.post { runCatching { browser.disconnect() } } }
        }
    }

    override fun sendMediaButtonPlay(packageName: String): Boolean {
        val receiver = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName),
            0,
        ).firstOrNull()?.activityInfo ?: return false
        val component = ComponentName(receiver.packageName, receiver.name)
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
