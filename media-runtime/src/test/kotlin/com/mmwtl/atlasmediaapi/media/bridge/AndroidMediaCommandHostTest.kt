package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
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
import org.junit.Assert.assertNull
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
    fun `confirmed online source republishes metadata from an existing playing session`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val playingState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .build()
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Existing online track")
            .build()
        val session = MediaSession(context, "existing-online-session").apply {
            isActive = true
            setMetadata(metadata)
            setPlaybackState(playingState)
        }
        val controller = controller(context, session, "com.example.existing")
        shadowOf(controller).setMetadata(metadata)
        shadowOf(controller).setPlaybackState(playingState)
        activeControllers(observer) += controller
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context).apply {
                defaultMediaPackage = "com.example.existing"
            },
            hub = hub,
            launchPackage = null,
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )

        assertTrue(host.setSource(BridgeAudioSource.ONLINE, appSource = null, autoplay = true))
        assertEquals("Existing online track", repository.snapshot().title)
        assertEquals("com.example.existing", repository.snapshot().ownerPackage)
        session.release()
    }

    @Test
    fun `configured online player launches and returns home`() = runBlocking {
        val launched = mutableListOf<String>()
        val fixture = fixture(
            configuredPackage = "com.example.player",
            minimizeAfterAutostart = true,
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
    fun `configured online player stays foreground when auto minimize is disabled`() = runBlocking {
        val fixture = fixture(
            configuredPackage = "com.example.player",
            launchPackage = { true },
        )

        assertTrue(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = false))
        assertNull(shadowOf(fixture.context).nextStartedActivity)
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
    fun `autoplay waits for configured player session and playing state`() = runBlocking {
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
                    Thread.sleep(30L)
                    shadowOf(controller).setPlaybackState(
                        PlaybackState.Builder()
                            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                            .build(),
                    )
                }.start()
                true
            },
            sessionWaitTimeoutMs = 500L,
            sessionPollDelaysMs = listOf(5L, 10L, 20L),
            autoplayConfirmDelaysMs = listOf(10L, 30L),
            autoplayMediaKeyConfirmDelayMs = 10L,
        )

        assertTrue(host.setSource(BridgeAudioSource.ONLINE, appSource = null, autoplay = true))
        assertNull(shadowOf(context).nextStartedActivity)
        session.release()
    }

    @Test
    fun `autoplay fails when configured session stays paused`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val session = MediaSession(context, "paused-online-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 100L))
        }
        activeControllers(observer) += controller(context, session, "com.example.paused")
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context).apply {
                defaultMediaPackage = "com.example.paused"
            },
            hub = hub,
            launchPackage = { true },
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
            autoplayConfirmDelaysMs = listOf(1L, 1L),
            autoplayMediaKeyConfirmDelayMs = 1L,
        )

        assertFalse(host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = true))
        session.release()
    }

    @Test
    fun `returning to online prefers remembered session instead of newer session`() {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val controllers = activeControllers(observer)
        val rememberedSession = MediaSession(context, "remembered-online-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 100L))
        }
        val newerSession = MediaSession(context, "newer-online-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 200L))
        }
        controllers += controller(context, newerSession, "com.example.newer")
        controllers += controller(context, rememberedSession, "com.example.remembered")
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context),
            hub = hub,
            launchPackage = null,
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )
        assertEquals("com.example.newer", host.preferredSession()?.packageName)

        host.setCurrentMediaPackage("com.example.remembered")

        assertEquals("com.example.remembered", host.preferredOnlineSession()?.packageName)
        rememberedSession.release()
        newerSession.release()
    }

    @Test
    fun `online does not reuse an unrelated session when no online session was remembered`() {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val unrelatedSession = MediaSession(context, "unrelated-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 200L))
        }
        activeControllers(observer) += controller(context, unrelatedSession, "com.example.unrelated")
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context).apply {
                defaultMediaPackage = "com.example.default"
            },
            hub = hub,
            launchPackage = null,
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )

        assertNull(host.preferredOnlineSession())
        unrelatedSession.release()
    }

    @Test
    fun `startup autoplay without a selected source plays the current session`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val session = MediaSession(context, "current-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 100L))
        }
        activeControllers(observer) += controller(context, session, "com.example.current")
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context),
            hub = hub,
            launchPackage = null,
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )

        assertTrue(host.autoplayCurrentSource())
        assertEquals("com.example.current", host.currentMediaPackage())
        session.release()
    }

    @Test
    fun `startup autoplay without a selected source does not launch default online player`() =
        runBlocking {
            val launched = mutableListOf<String>()
            val fixture = fixture(
                configuredPackage = "com.example.default",
                launchPackage = {
                    launched += it
                    true
                },
            )

            assertFalse(fixture.host.autoplayCurrentSource())
            assertTrue(launched.isEmpty())
        }

    private data class Fixture(
        val context: android.app.Application,
        val repository: MediaStateRepository,
        val host: AndroidMediaCommandHost,
    )

    private fun fixture(
        configuredPackage: String = "",
        minimizeAfterAutostart: Boolean = false,
        launchPackage: ((String) -> Boolean)? = null,
        sessionWaitTimeoutMs: Long = AndroidMediaCommandHost.SESSION_WAIT_TIMEOUT_MS,
        sessionPollDelaysMs: List<Long> = AndroidMediaCommandHost.SESSION_POLL_DELAYS_MS,
        autoplayConfirmDelaysMs: List<Long> = AndroidMediaCommandHost.AUTOPLAY_CONFIRM_DELAYS_MS,
        autoplayMediaKeyConfirmDelayMs: Long = AndroidMediaCommandHost.AUTOPLAY_MEDIA_KEY_CONFIRM_DELAY_MS,
    ): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val preferences = AtlasPreferences(context).apply {
            defaultMediaPackage = configuredPackage
            minimizeOnlinePlayerAfterAutostart = minimizeAfterAutostart
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
                autoplayConfirmDelaysMs = autoplayConfirmDelaysMs,
                autoplayMediaKeyConfirmDelayMs = autoplayMediaKeyConfirmDelayMs,
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
        autoplayConfirmDelaysMs: List<Long> = AndroidMediaCommandHost.AUTOPLAY_CONFIRM_DELAYS_MS,
        autoplayMediaKeyConfirmDelayMs: Long = AndroidMediaCommandHost.AUTOPLAY_MEDIA_KEY_CONFIRM_DELAY_MS,
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
        autoplayConfirmDelaysMs = autoplayConfirmDelaysMs,
        autoplayMediaKeyConfirmDelayMs = autoplayMediaKeyConfirmDelayMs,
    )

    private fun pausedState(lastUpdateTime: Long): PlaybackState = PlaybackState.Builder()
        .setState(PlaybackState.STATE_PAUSED, 0L, 0f, lastUpdateTime)
        .setActions(PlaybackState.ACTION_PLAY)
        .build()

    private fun controller(
        context: Context,
        session: MediaSession,
        packageName: String,
    ): MediaController = MediaController(context, session.sessionToken).also { controller ->
        shadowOf(controller).setPackageName(packageName)
        shadowOf(controller).setPlaybackState(session.controller.playbackState)
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeControllers(
        observer: MediaSessionObserver,
    ): CopyOnWriteArrayList<MediaController> = MediaSessionObserver::class.java
        .getDeclaredField("activeControllers")
        .apply { isAccessible = true }
        .get(observer) as CopyOnWriteArrayList<MediaController>
}
