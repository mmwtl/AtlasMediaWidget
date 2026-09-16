package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.geely.lib.oneosapi.OneOSApiManager
import com.mmwtl.atlasmediaapi.media.session.MediaSessionObserver
import com.mmwtl.atlasmediaapi.settings.AtlasPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class AndroidMediaCommandHostTest {
    @Before
    fun clearPreferences() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("atlas_media_api_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `online default source works before OneOS connects`() = runBlocking {
        val fixture = fixture()

        assertTrue(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = false))
        assertEquals(BridgeAudioSource.ONLINE.name, fixture.repository.snapshot().audioSource)
    }

    @Test
    fun `configured online player launches and returns home`() = runBlocking {
        val launched = mutableListOf<String>()
        val fixture = fixture(
            configuredPackage = "com.example.player",
            launchPackage = {
                launched += it
                true
            },
        )

        assertTrue(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = false))
        assertEquals(listOf("com.example.player"), launched)
        val homeIntent = shadowOf(fixture.context).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, homeIntent.action)
        assertTrue(homeIntent.categories.contains(Intent.CATEGORY_HOME))
    }

    @Test
    fun `autoplay fails when configured player publishes no session`() = runBlocking {
        val fixture = fixture(
            configuredPackage = "com.example.no-session",
            launchPackage = { true },
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )

        assertFalse(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = true))
    }

    @Test
    fun `autoplay waits for configured player session`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val controllers = activeControllers(observer)
        val session = MediaSession(context, "delayed-online-session").apply {
            isActive = true
            setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PAUSED, 0L, 0f)
                    .build(),
            )
        }
        val controller = MediaController(context, session.sessionToken)
        shadowOf(controller).setPackageName("com.example.delayed")
        shadowOf(controller).setPlaybackState(session.controller.playbackState)
        val preferences = AtlasPreferences(context).apply {
            defaultMediaPackage = "com.example.delayed"
        }
        val host = host(
            context = context,
            observer = observer,
            preferences = preferences,
            hub = hub,
            launchPackage = {
                Thread {
                    Thread.sleep(30L)
                    controllers += controller
                }.start()
                true
            },
            sessionWaitTimeoutMs = 500L,
            sessionPollDelaysMs = listOf(5L, 10L, 20L),
        )

        assertTrue(host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = true))
        session.release()
    }

    private data class Fixture(
        val context: android.app.Application,
        val repository: MediaStateRepository,
        val host: AndroidMediaCommandHost,
    )

    private fun fixture(
        configuredPackage: String = "",
        launchPackage: ((String) -> Boolean)? = null,
        sessionWaitTimeoutMs: Long = AndroidMediaCommandHost.SESSION_WAIT_TIMEOUT_MS,
        sessionPollDelaysMs: List<Long> = AndroidMediaCommandHost.SESSION_POLL_DELAYS_MS,
    ): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val preferences = AtlasPreferences(context).apply {
            defaultMediaPackage = configuredPackage
        }
        return Fixture(
            context = context,
            repository = repository,
            host = host(
                context = context,
                observer = observer,
                preferences = preferences,
                hub = hub,
                launchPackage = launchPackage,
                sessionWaitTimeoutMs = sessionWaitTimeoutMs,
                sessionPollDelaysMs = sessionPollDelaysMs,
            ),
        )
    }

    private fun hub(
        context: Context,
        repository: MediaStateRepository,
    ): MediaStateHub = MediaStateHub(
        context = context,
        repository = repository,
        artworkRepository = ArtworkRepository(
            context,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ),
    )

    private fun host(
        context: Context,
        observer: MediaSessionObserver,
        preferences: AtlasPreferences,
        hub: MediaStateHub,
        launchPackage: ((String) -> Boolean)?,
        sessionWaitTimeoutMs: Long,
        sessionPollDelaysMs: List<Long>,
    ): AndroidMediaCommandHost = AndroidMediaCommandHost(
        context = context,
        apiManager = OneOSApiManager.getInstance(context),
        sessionObserver = observer,
        preferences = preferences,
        radioCatalogRepository = RadioCatalogRepository(context),
        stateHub = hub,
        launchPackage = launchPackage,
        sessionWaitTimeoutMs = sessionWaitTimeoutMs,
        sessionPollDelaysMs = sessionPollDelaysMs,
    )

    @Suppress("UNCHECKED_CAST")
    private fun activeControllers(
        observer: MediaSessionObserver,
    ): CopyOnWriteArrayList<MediaController> = MediaSessionObserver::class.java
        .getDeclaredField("activeControllers")
        .apply { isAccessible = true }
        .get(observer) as CopyOnWriteArrayList<MediaController>
}
