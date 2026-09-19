package com.mmwtl.atlasmediaapi.media.bridge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCommandRouterTest {
    private class FakeSession(
        override val packageName: String,
        override var playbackState: SessionPlaybackState = SessionPlaybackState.NOT_PLAYING,
        override var capabilities: Long = MediaCapabilities.ALL,
    ) : MediaSessionCommandTarget {
        val calls = mutableListOf<String>()

        override fun play(): Boolean = true.also { calls += "play" }
        override fun pause(): Boolean = true.also { calls += "pause" }
        override fun next(): Boolean = true.also { calls += "next" }
        override fun previous(): Boolean = true.also { calls += "previous" }
        override fun seekTo(position: Long): Boolean = true.also { calls += "seek:$position" }
    }

    private class FakeHost : MediaCommandHost {
        var available = true
        var blocked = false
        var nativeResult: MediaCommandResult? = null
        var preferred: MediaSessionCommandTarget? = null
        var owned: MediaSessionCommandTarget? = null
        var allSessions = emptyList<MediaSessionCommandTarget>()
        var visiblePackage = ""
        var currentPackage = ""
        var defaultPackage = ""
        var beforePlayCalls = 0
        var beforePlayResult = true
        var startDefaultCalls = 0
        var fallbackCalls = mutableListOf<Pair<String, MediaCommand>>()
        var sourceResult = true
        var selectedSource: BridgeAudioSource? = null
        var tunedStation: RadioStationTarget? = null
        var tuneAutoplay: Boolean? = null

        override fun backendAvailable(): Boolean = available
        override fun blocksMediaCommands(): Boolean = blocked
        override fun ownedSession(): MediaSessionCommandTarget? = owned
        override suspend fun executeNative(request: MediaCommandRequest): MediaCommandResult? =
            nativeResult

        override fun preferredSession(): MediaSessionCommandTarget? = preferred
        override fun sessions(): List<MediaSessionCommandTarget> = allSessions
        override fun currentVisiblePackage(): String = visiblePackage
        override fun currentMediaPackage(): String = currentPackage
        override fun defaultMediaPackage(): String = defaultPackage
        override fun setCurrentMediaPackage(packageName: String) {
            currentPackage = packageName
        }

        override suspend fun beforeSessionPlay(): Boolean {
            beforePlayCalls++
            return beforePlayResult
        }

        override suspend fun sendFallback(packageName: String, command: MediaCommand): Boolean {
            fallbackCalls += packageName to command
            return true
        }

        override suspend fun startDefaultAndPlay(packageName: String): Boolean = true.also {
            startDefaultCalls++
        }

        override suspend fun setSource(
            source: BridgeAudioSource,
            appSource: String?,
            autoplay: Boolean,
        ): Boolean {
            selectedSource = source
            return sourceResult
        }

        override suspend fun tuneRadio(target: RadioStationTarget, autoplay: Boolean): Boolean {
            tunedStation = target
            tuneAutoplay = autoplay
            return sourceResult
        }
    }

    private fun request(command: MediaCommand) = MediaCommandRequest("test-req", command)

    @Test
    fun `native source routing wins before media sessions`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply {
            nativeResult = MediaCommandResult(MediaBridgeContract.Status.OK)
            preferred = session
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.NEXT))

        assertTrue(result.succeeded)
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun `playing external session wins over native source`() = runBlocking {
        val session = FakeSession("player", SessionPlaybackState.PLAYING)
        val host = FakeHost().apply {
            nativeResult = MediaCommandResult(MediaBridgeContract.Status.OK)
            preferred = session
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.NEXT))

        assertTrue(result.succeeded)
        assertEquals(listOf("next"), session.calls)
    }

    @Test
    fun `foreground external session wins even when paused`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply {
            nativeResult = MediaCommandResult(MediaBridgeContract.Status.OK)
            preferred = session
            visiblePackage = "player"
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.PREVIOUS))

        assertTrue(result.succeeded)
        assertEquals(listOf("previous"), session.calls)
    }

    @Test
    fun `current media package session wins over native source even when paused`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply {
            nativeResult = MediaCommandResult(MediaBridgeContract.Status.OK)
            preferred = session
            currentPackage = "player"
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.PLAY))

        assertTrue(result.succeeded)
        assertEquals(listOf("play"), session.calls)
    }

    @Test
    fun `unsupported native command falls through to external session`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply {
            nativeResult = MediaCommandResult(MediaBridgeContract.Status.NOT_SUPPORTED)
            preferred = session
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.NEXT))

        assertTrue(result.succeeded)
        assertEquals(listOf("next"), session.calls)
    }

    @Test
    fun `session without capability does not hide capable session`() = runBlocking {
        val missingNext = FakeSession("first", capabilities = MediaCapabilities.BASIC_PLAYBACK)
        val capable = FakeSession("second", capabilities = MediaCapabilities.TRACK_NAVIGATION)
        val host = FakeHost().apply {
            allSessions = listOf(missingNext, capable)
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.NEXT))

        assertTrue(result.succeeded)
        assertTrue(missingNext.calls.isEmpty())
        assertEquals(listOf("next"), capable.calls)
    }

    @Test
    fun `toggle pauses the preferred playing session`() = runBlocking {
        val session = FakeSession("player", SessionPlaybackState.PLAYING)
        val host = FakeHost().apply { preferred = session }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.TOGGLE))

        assertTrue(result.succeeded)
        assertEquals(listOf("pause"), session.calls)
        assertEquals("player", host.currentPackage)
        assertEquals(0, host.beforePlayCalls)
    }

    @Test
    fun `play prepares source before starting session`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply { preferred = session }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.PLAY))

        assertTrue(result.succeeded)
        assertEquals(1, host.beforePlayCalls)
        assertEquals(listOf("play"), session.calls)
    }

    @Test
    fun `play is not sent when source preparation is not confirmed`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply {
            preferred = session
            beforePlayResult = false
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.PLAY))

        assertEquals(MediaBridgeContract.Status.FAILED, result.status)
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun `seek is rejected when selected session lacks capability`() = runBlocking {
        val session = FakeSession(
            packageName = "player",
            capabilities = MediaCapabilities.BASIC_PLAYBACK,
        )
        val host = FakeHost().apply { preferred = session }

        val result = MediaCommandRouter(host).execute(
            request(MediaCommand.SEEK_TO).copy(position = 12_345L),
        )

        assertEquals(MediaBridgeContract.Status.NOT_SUPPORTED, result.status)
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun `blocked source never falls through to a session`() = runBlocking {
        val session = FakeSession("player")
        val host = FakeHost().apply {
            blocked = true
            preferred = session
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.PLAY))

        assertEquals(MediaBridgeContract.Status.NOT_SUPPORTED, result.status)
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun `blocked source routes commands to its owned session`() = runBlocking {
        val carPlay = FakeSession(
            packageName = "com.autolink.carplay.app",
            playbackState = SessionPlaybackState.PLAYING,
        )
        val host = FakeHost().apply {
            blocked = true
            owned = carPlay
            defaultPackage = "other.player"
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.NEXT))

        assertTrue(result.succeeded)
        assertEquals(listOf("next"), carPlay.calls)
        assertTrue(host.fallbackCalls.isEmpty())
        assertEquals(0, host.beforePlayCalls)
    }

    @Test
    fun `owned source without capability does not fall back to another player`() = runBlocking {
        val carPlay = FakeSession(
            packageName = "com.autolink.carplay.app",
            capabilities = MediaCapabilities.BASIC_PLAYBACK,
        )
        val host = FakeHost().apply {
            blocked = true
            owned = carPlay
            defaultPackage = "other.player"
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.NEXT))

        assertEquals(MediaBridgeContract.Status.NOT_SUPPORTED, result.status)
        assertTrue(carPlay.calls.isEmpty())
        assertTrue(host.fallbackCalls.isEmpty())
    }

    @Test
    fun `set source delegates through the same host`() = runBlocking {
        val host = FakeHost()
        val req = request(MediaCommand.SET_SOURCE).copy(source = BridgeAudioSource.USB)

        val result = MediaCommandRouter(host).execute(req)

        assertTrue(result.succeeded)
        assertEquals(BridgeAudioSource.USB, host.selectedSource)
    }

    @Test
    fun `tune radio bypasses media sessions and delegates exact station`() = runBlocking {
        val session = FakeSession("player", SessionPlaybackState.PLAYING)
        val station = RadioStationTarget(
            frequencyKHz = 100_100,
            band = 1,
            serviceName = "Vendor RDS",
            selector = "fm:100100",
        )
        val host = FakeHost().apply { preferred = session }

        val result = MediaCommandRouter(host).execute(
            request(MediaCommand.TUNE_RADIO).copy(
                radioStation = station,
                autoplay = false,
            ),
        )

        assertTrue(result.succeeded)
        assertEquals(station, host.tunedStation)
        assertFalse(checkNotNull(host.tuneAutoplay))
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun `tune radio rejects missing station payload`() = runBlocking {
        val host = FakeHost()

        val result = MediaCommandRouter(host).execute(request(MediaCommand.TUNE_RADIO))

        assertEquals(MediaBridgeContract.Status.INVALID_REQUEST, result.status)
    }

    @Test
    fun `unblocked source routes to executeNative if no active session`() = runBlocking {
        val host = FakeHost().apply {
            blocked = false
            nativeResult = MediaCommandResult(MediaBridgeContract.Status.OK)
        }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.PLAY))

        assertTrue(result.succeeded)
    }

    @Test
    fun `toggle delegates to custom target toggle method`() = runBlocking {
        var customToggleCalled = false
        val session = object : MediaSessionCommandTarget {
            override val packageName: String = "custom.player"
            override val playbackState: SessionPlaybackState = SessionPlaybackState.NOT_PLAYING
            override val capabilities: Long = MediaCapabilities.ALL
            override fun play(): Boolean = false
            override fun pause(): Boolean = false
            override fun toggle(): Boolean = true.also { customToggleCalled = true }
            override fun next(): Boolean = false
            override fun previous(): Boolean = false
            override fun seekTo(position: Long): Boolean = false
        }
        val host = FakeHost().apply { preferred = session }

        val result = MediaCommandRouter(host).execute(request(MediaCommand.TOGGLE))

        assertTrue(result.succeeded)
        assertTrue(customToggleCalled)
    }
}
