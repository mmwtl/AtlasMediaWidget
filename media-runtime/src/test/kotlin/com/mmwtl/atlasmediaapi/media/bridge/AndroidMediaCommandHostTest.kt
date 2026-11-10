package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.geely.lib.oneosapi.OneOSApiManager
import com.mmwtl.atlasmediaapi.media.session.BackgroundPlayerStarter
import com.mmwtl.atlasmediaapi.media.session.MediaSessionObserver
import com.mmwtl.atlasmediaapi.media.session.PreparedPlayer
import com.mmwtl.atlasmediaapi.media.session.PreparedSession
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
    fun `player tile refuses an app that is not installed`() = runBlocking {
        val launched = mutableListOf<String>()
        val fixture = fixture(launchPackage = { launched += it; true })

        assertFalse(fixture.host.selectPlayer("com.example.missing", autoplay = true))
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `player tile shows the chosen playing player and keeps it for commands`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        install(context, "com.example.chosen")
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub)
        val playing = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
            .build()
        val chosen = MediaSession(context, "chosen-session").apply {
            isActive = true
            setPlaybackState(playing)
        }
        val chosenController = controller(context, chosen, "com.example.chosen")
        shadowOf(chosenController).setPlaybackState(playing)
        shadowOf(chosenController).setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Chosen track")
                .build(),
        )
        activeControllers(observer) += chosenController
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context).apply {
                defaultMediaPackage = "com.example.default"
            },
            hub = hub,
            launchPackage = { false },
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )

        assertTrue(host.selectPlayer("com.example.chosen", autoplay = true))
        assertEquals("com.example.chosen", repository.snapshot().ownerPackage)
        assertEquals(BridgeAudioSource.ONLINE.name, repository.snapshot().audioSource)
        assertEquals("com.example.chosen", host.currentMediaPackage())
        assertEquals("com.example.chosen", host.preferredOnlineSession()?.packageName)
        chosen.release()
    }

    @Test
    fun `player tile without a session launches that player, not the default one`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        install(context, "com.example.chosen")
        val launched = mutableListOf<String>()
        val fixture = fixture(
            configuredPackage = "com.example.default",
            launchPackage = { launched += it; true },
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
        )

        assertFalse(fixture.host.selectPlayer("com.example.chosen", autoplay = true))
        assertEquals(listOf("com.example.chosen"), launched)
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
    fun `commands reach the shown paused player instead of a newer session`() {
        val context = RuntimeEnvironment.getApplication()
        val hub = hub(context, MediaStateRepository())
        val observer = MediaSessionObserver(context, hub)
        val shownSession = MediaSession(context, "shown-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 100L))
        }
        val newerSession = MediaSession(context, "newer-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 200L))
        }
        activeControllers(observer) += controller(context, newerSession, "com.example.newer")
        activeControllers(observer) += controller(context, shownSession, "com.example.shown")
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context),
            hub = hub,
            launchPackage = null,
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
            displayedMediaPackage = "com.example.shown",
        )

        assertEquals("com.example.shown", host.preferredSession()?.packageName)
        shownSession.release()
        newerSession.release()
    }

    @Test
    fun `commands reach the shown player that just stopped while another player plays`() {
        assertEquals("com.example.shown", holdFixturePreferredPackage(stoppedAgoMs = 100L))
    }

    @Test
    fun `commands reach the playing player once the shown player hold expires`() {
        assertEquals(
            "com.example.playing",
            holdFixturePreferredPackage(stoppedAgoMs = ShownPlayerHold.GRACE_MS + 100L),
        )
    }

    private fun holdFixturePreferredPackage(stoppedAgoMs: Long): String? {
        val context = RuntimeEnvironment.getApplication()
        val hub = hub(context, MediaStateRepository())
        val observer = MediaSessionObserver(context, hub)
        val shownSession = MediaSession(context, "shown-session").apply {
            isActive = true
            setPlaybackState(pausedState(lastUpdateTime = 100L))
        }
        val playingState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .setActions(PlaybackState.ACTION_PAUSE)
            .build()
        val playingSession = MediaSession(context, "playing-session").apply {
            isActive = true
            setPlaybackState(playingState)
        }
        activeControllers(observer) += controller(context, playingSession, "com.example.playing").also {
            shadowOf(it).setPlaybackState(playingState)
        }
        activeControllers(observer) += controller(context, shownSession, "com.example.shown")
        stoppedPlayingAt(observer)["com.example.shown"] =
            android.os.SystemClock.elapsedRealtime() - stoppedAgoMs
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context),
            hub = hub,
            launchPackage = null,
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
            displayedMediaPackage = "com.example.shown",
        )

        return host.preferredSession()?.packageName.also {
            shownSession.release()
            playingSession.release()
        }
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

    @Test
    fun `play without a session starts the player through its media browser`() = runBlocking {
        val (host, starter, launched, session) =
            backgroundStartFixture("com.example.browser", browserStarts = true)

        assertTrue(host.startDefaultAndPlay("com.example.browser"))
        // The connection that started playback stays open for players bound to their client.
        assertEquals(listOf("browser:com.example.browser"), starter.calls)
        assertTrue(host.startDefaultAndPlay("com.example.browser"))
        assertEquals("close:com.example.browser", starter.calls.last())
        assertTrue(launched.isEmpty())
        assertEquals("com.example.browser", host.currentMediaPackage())
        session.release()
    }

    @Test
    fun `slow player that accepted the media browser gets the longer session wait`() = runBlocking {
        val (host, starter, launched, session) = backgroundStartFixture(
            "com.example.slow",
            browserStarts = true,
            browserSessionDelayMs = 150L,
            browserSessionWaitTimeoutMs = 2_000L,
        )

        assertTrue(host.startDefaultAndPlay("com.example.slow"))
        assertEquals(listOf("browser:com.example.slow"), starter.calls)
        assertTrue(launched.isEmpty())
        session.release()
    }

    @Test
    fun `refused media browser falls back to the media button receiver`() = runBlocking {
        val (host, starter, launched, session) =
            backgroundStartFixture("com.example.button", mediaButtonStarts = true)

        assertTrue(host.startDefaultAndPlay("com.example.button"))
        assertEquals(listOf("browser:com.example.button", "button:com.example.button"), starter.calls)
        assertTrue(launched.isEmpty())
        session.release()
    }

    @Test
    fun `player without background entry points is launched through its activity`() = runBlocking {
        val (host, starter, launched, session) = backgroundStartFixture("com.example.activity")

        assertFalse(host.startDefaultAndPlay("com.example.activity"))
        assertEquals(listOf("browser:com.example.activity", "button:com.example.activity"), starter.calls)
        assertEquals(listOf("com.example.activity"), launched)
        session.release()
    }

    @Test
    fun `online default without autoplay shows the prepared track instead of opening the player`() = runBlocking {
        val fixture = prepareFixture("com.example.resume", prepares = true)

        assertTrue(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = false))

        assertEquals(listOf("prepare:com.example.resume"), fixture.starter.calls)
        assertTrue(fixture.launched.isEmpty())
        assertNull(shadowOf(fixture.context).nextStartedActivity)
        val shown = fixture.repository.snapshot()
        assertEquals("Last track", shown.title)
        assertEquals("com.example.resume", shown.ownerPackage)
        assertEquals(PlaybackState.STATE_STOPPED, shown.playbackState)
        assertTrue(shown.capabilities and MediaCapabilities.PLAY != 0L)
        fixture.release()
    }

    @Test
    fun `online default without autoplay launches a player that cannot be prepared`() = runBlocking {
        val fixture = prepareFixture("com.example.resume", prepares = false)

        assertTrue(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = false))

        assertEquals(listOf("prepare:com.example.resume"), fixture.starter.calls)
        assertEquals(listOf("com.example.resume"), fixture.launched)
        fixture.release()
    }

    @Test
    fun `online default without autoplay launches a prepared player that shows no track`() = runBlocking {
        val fixture = prepareFixture("com.example.resume", prepares = true, recentTitle = null)

        assertTrue(fixture.host.setDefaultSource(BridgeAudioSource.ONLINE, autoplay = false))

        assertEquals(listOf("prepare:com.example.resume"), fixture.starter.calls)
        assertEquals(listOf("com.example.resume"), fixture.launched)
        fixture.release()
    }

    @Test
    fun `player tile without autoplay does not launch a prepared player that shows no track`() = runBlocking {
        val fixture = prepareFixture("com.example.resume", prepares = true, recentTitle = null)
        install(fixture.context, "com.example.resume")

        assertTrue(fixture.host.selectPlayer("com.example.resume", autoplay = false))

        assertEquals(listOf("prepare:com.example.resume"), fixture.starter.calls)
        assertTrue(fixture.launched.isEmpty())
        fixture.release()
    }

    @Test
    fun `startup prepare shows the configured player when MediaCenter is unavailable`() = runBlocking {
        val fixture = prepareFixture("com.example.resume", prepares = true)

        assertTrue(fixture.host.prepareCurrentOnlinePlayer())

        assertEquals(listOf("prepare:com.example.resume"), fixture.starter.calls)
        assertEquals("Last track", fixture.repository.snapshot().title)
        fixture.release()
    }

    @Test
    fun `player tile without autoplay prepares a player that has no session`() = runBlocking {
        val fixture = prepareFixture("com.example.default", prepares = true, preparedPackage = "com.example.resume")
        install(fixture.context, "com.example.resume")

        assertTrue(fixture.host.selectPlayer("com.example.resume", autoplay = false))

        assertEquals(listOf("prepare:com.example.resume"), fixture.starter.calls)
        assertEquals("com.example.resume", fixture.repository.snapshot().ownerPackage)
        assertEquals("Last track", fixture.repository.snapshot().title)
        assertEquals("com.example.resume", fixture.host.preferredSession()?.packageName)
        assertTrue(fixture.host.preferredSession()!!.capabilities and MediaCapabilities.PLAY != 0L)
        fixture.release()
    }

    private class PrepareFixture(
        val context: android.app.Application,
        val repository: MediaStateRepository,
        val observer: MediaSessionObserver,
        val host: AndroidMediaCommandHost,
        val starter: FakeBackgroundStarter,
        val launched: List<String>,
        val session: MediaSession,
    ) {
        fun release() {
            observer.stop()
            session.release()
        }
    }

    /** A player whose browser session has no track and whose recent root names [recentTitle]. */
    private fun prepareFixture(
        configuredPackage: String,
        prepares: Boolean,
        preparedPackage: String = configuredPackage,
        recentTitle: String? = "Last track",
    ): PrepareFixture {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = hub(context, repository)
        val observer = MediaSessionObserver(context, hub).apply { start() }
        val session = MediaSession(context, "prepared-$preparedPackage")
        val controller = MediaController(context, session.sessionToken)
        shadowOf(controller).setPackageName(preparedPackage)
        val recent = recentTitle?.let {
            MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, it).build()
        }
        val starter = FakeBackgroundStarter(
            onPrepare = { PreparedSession(controller, recent) }.takeIf { prepares },
        )
        val launched = mutableListOf<String>()
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context).apply { defaultMediaPackage = configuredPackage },
            hub = hub,
            launchPackage = {
                launched += it
                true
            },
            sessionWaitTimeoutMs = 20L,
            sessionPollDelaysMs = listOf(1L),
            backgroundPlayerStarter = starter,
        )
        return PrepareFixture(context, repository, observer, host, starter, launched, session)
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
        backgroundPlayerStarter: BackgroundPlayerStarter? = null,
        browserSessionWaitTimeoutMs: Long = sessionWaitTimeoutMs,
        displayedMediaPackage: String = "",
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
        backgroundSessionWaitTimeoutMs = sessionWaitTimeoutMs,
        browserSessionWaitTimeoutMs = browserSessionWaitTimeoutMs,
        backgroundPlayerStarter = backgroundPlayerStarter,
        displayedMediaPackage = { displayedMediaPackage },
    )

    private class FakeBackgroundStarter(
        private val onBrowserPlay: (() -> Unit)? = null,
        private val onMediaButton: (() -> Unit)? = null,
        private val onPrepare: (() -> PreparedSession)? = null,
    ) : BackgroundPlayerStarter {
        val calls = mutableListOf<String>()

        override suspend fun connectAndPlay(packageName: String): AutoCloseable? {
            calls += "browser:$packageName"
            val play = onBrowserPlay ?: return null
            play()
            return AutoCloseable { calls += "close:$packageName" }
        }

        override fun sendMediaButtonPlay(packageName: String): Boolean {
            calls += "button:$packageName"
            val press = onMediaButton ?: return false
            press()
            return true
        }

        override suspend fun connectAndPrepare(
            packageName: String,
            allowPrepare: Boolean,
        ): PreparedPlayer? {
            calls += "prepare:$packageName"
            val session = onPrepare?.invoke() ?: return null
            return PreparedPlayer(session, AutoCloseable { calls += "close:$packageName" })
        }
    }

    private data class BackgroundStartFixture(
        val host: AndroidMediaCommandHost,
        val starter: FakeBackgroundStarter,
        val launched: List<String>,
        val session: MediaSession,
    )

    /** The player publishes a playing session only through the entry points that are enabled. */
    private fun backgroundStartFixture(
        packageName: String,
        browserStarts: Boolean = false,
        mediaButtonStarts: Boolean = false,
        browserSessionDelayMs: Long = 0L,
        browserSessionWaitTimeoutMs: Long = 50L,
    ): BackgroundStartFixture {
        val context = RuntimeEnvironment.getApplication()
        val hub = hub(context, MediaStateRepository())
        val observer = MediaSessionObserver(context, hub)
        val playingState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .build()
        val session = MediaSession(context, "background-$packageName").apply {
            isActive = true
            setPlaybackState(playingState)
        }
        val controller = controller(context, session, packageName)
        shadowOf(controller).setPlaybackState(playingState)
        val publishSession: () -> Unit = { activeControllers(observer) += controller }
        val publishBrowserSession: () -> Unit = {
            if (browserSessionDelayMs == 0L) {
                publishSession()
            } else {
                Thread {
                    Thread.sleep(browserSessionDelayMs)
                    publishSession()
                }.start()
            }
        }
        val starter = FakeBackgroundStarter(
            onBrowserPlay = publishBrowserSession.takeIf { browserStarts },
            onMediaButton = publishSession.takeIf { mediaButtonStarts },
        )
        val launched = mutableListOf<String>()
        val host = host(
            context = context,
            observer = observer,
            preferences = AtlasPreferences(context).apply { defaultMediaPackage = packageName },
            hub = hub,
            launchPackage = {
                launched += it
                false
            },
            sessionWaitTimeoutMs = 50L,
            sessionPollDelaysMs = listOf(1L),
            autoplayConfirmDelaysMs = listOf(1L),
            autoplayMediaKeyConfirmDelayMs = 1L,
            backgroundPlayerStarter = starter,
            browserSessionWaitTimeoutMs = browserSessionWaitTimeoutMs,
        )
        return BackgroundStartFixture(host, starter, launched, session)
    }

    private fun install(context: Context, packageName: String) {
        shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    this.packageName = packageName
                }
            },
        )
    }

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
    private fun stoppedPlayingAt(observer: MediaSessionObserver): MutableMap<String, Long> =
        MediaSessionObserver::class.java
            .getDeclaredField("stoppedPlayingAt")
            .apply { isAccessible = true }
            .get(observer) as MutableMap<String, Long>

    @Suppress("UNCHECKED_CAST")
    private fun activeControllers(
        observer: MediaSessionObserver,
    ): CopyOnWriteArrayList<MediaController> = MediaSessionObserver::class.java
        .getDeclaredField("activeControllers")
        .apply { isAccessible = true }
        .get(observer) as CopyOnWriteArrayList<MediaController>
}
