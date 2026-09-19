package com.mmwtl.atlasmediaapi.media.session

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import com.mmwtl.atlasmediaapi.media.bridge.ArtworkRepository
import com.mmwtl.atlasmediaapi.media.bridge.BridgeAudioSource
import com.mmwtl.atlasmediaapi.media.bridge.MediaStateHub
import com.mmwtl.atlasmediaapi.media.bridge.MediaStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [MediaSessionObserverRecoveryTest.ShadowMediaSessionManager::class])
class MediaSessionObserverRecoveryTest {
    @Before
    fun setUp() {
        ShadowMediaSessionManager.reset()
    }

    @After
    fun tearDown() {
        ShadowMediaSessionManager.reset()
    }

    @Test
    fun `failed registration retries when notification listener reconnects`() {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
        )
        val observer = MediaSessionObserver(context, hub)
        ShadowMediaSessionManager.failRegistration = true
        observer.start()
        notifyNotificationListener(observer, true)
        waitFor { ShadowMediaSessionManager.addCount == 1 }
        assertEquals(1, ShadowMediaSessionManager.addCount)

        ShadowMediaSessionManager.failRegistration = false
        val session = MediaSession(context, "observer-test")
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Recovered title")
            .build()
        val playback = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .build()
        val controller = MediaController(context, session.sessionToken)
        shadowOf(controller).setPackageName("com.example.player")
        shadowOf(controller).setMetadata(metadata)
        shadowOf(controller).setPlaybackState(playback)
        ShadowMediaSessionManager.setControllers(listOf(controller))
        notifyNotificationListener(observer, false)
        notifyNotificationListener(observer, true)
        waitFor { ShadowMediaSessionManager.addCount == 2 }
        assertEquals(2, ShadowMediaSessionManager.addCount)
        waitFor { observer.getActiveControllers().size == 1 }
        assertEquals("com.example.player", observer.getActiveControllers().single().packageName)

        shadowOf(controller).executeOnMetadataChanged(
            MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Updated title").build(),
        )
        waitFor { repository.snapshot().title == "Updated title" }
        assertEquals("Updated title", repository.snapshot().title)

        observer.stop()
        notifyNotificationListener(observer, false)
        notifyNotificationListener(observer, true)
        Thread.sleep(100)
        assertEquals(2, ShadowMediaSessionManager.addCount)
        assertTrue(observer.getActiveControllers().isEmpty())
        session.release()
    }

    @Test
    fun `native bluetooth session does not replace lost online session`() {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val losses = java.util.concurrent.CopyOnWriteArrayList<BridgeAudioSource>()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
            onActiveSourceLost = { source, _ -> losses += source },
        )
        hub.onBackendConnected(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
            MediaCenterConstant.AppSource.UNKNOWN,
            emptyMap(),
        )
        val observer = MediaSessionObserver(context, hub)
        val onlineSession = createController(context, "com.example.online", "Online title")
        val bluetoothSession = createController(context, "com.android.bluetooth", "BT title")

        ShadowMediaSessionManager.setControllers(listOf(onlineSession.controller, bluetoothSession.controller))
        observer.start()
        notifyNotificationListener(observer, true)
        waitFor { repository.snapshot().ownerPackage == "com.example.online" }

        ShadowMediaSessionManager.setControllers(listOf(bluetoothSession.controller))
        ShadowMediaSessionManager.dispatchControllers()
        waitFor { losses == listOf(BridgeAudioSource.ONLINE) }

        assertEquals(listOf(BridgeAudioSource.ONLINE), losses)
        observer.stop()
        onlineSession.session.release()
        bluetoothSession.session.release()
    }

    @Test
    fun `confirmed online source refreshes an existing session without another session callback`() {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
        )
        hub.onBackendConnected(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_BT,
            MediaCenterConstant.AppSource.UNKNOWN,
            emptyMap(),
        )
        val observer = MediaSessionObserver(context, hub)
        val onlineSession = createController(context, "com.example.online", "Online title")
        ShadowMediaSessionManager.setControllers(listOf(onlineSession.controller))
        observer.start()
        notifyNotificationListener(observer, true)
        waitFor { observer.getActiveControllers().size == 1 }
        assertEquals("", repository.snapshot().title)

        hub.onSourceChanged(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
            MediaCenterConstant.AppSource.UNKNOWN,
        )
        observer.refreshActiveController()

        assertEquals("Online title", repository.snapshot().title)
        assertEquals("com.example.online", repository.snapshot().ownerPackage)
        observer.stop()
        onlineSession.session.release()
    }

    @Test
    fun `online metadata refresh does not report loss when only native bluetooth session exists`() {
        val context = RuntimeEnvironment.getApplication()
        val repository = MediaStateRepository()
        val losses = java.util.concurrent.CopyOnWriteArrayList<BridgeAudioSource>()
        val hub = MediaStateHub(
            context = context,
            repository = repository,
            artworkRepository = ArtworkRepository(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
            onActiveSourceLost = { source, _ -> losses += source },
        )
        hub.onBackendConnected(
            MediaCenterConstant.AudioSource.AUDIO_SOURCE_ONLINE,
            MediaCenterConstant.AppSource.UNKNOWN,
            emptyMap(),
        )
        val observer = MediaSessionObserver(context, hub)
        val onlineSession = createController(context, "com.example.online", "Online title")
        val bluetoothSession = createController(context, "com.android.bluetooth", "BT title")
        ShadowMediaSessionManager.setControllers(listOf(onlineSession.controller))
        observer.start()
        notifyNotificationListener(observer, true)
        waitFor { repository.snapshot().ownerPackage == "com.example.online" }

        replaceActiveControllers(observer, listOf(bluetoothSession.controller))
        observer.refreshActiveController()

        assertTrue(losses.isEmpty())
        observer.stop()
        onlineSession.session.release()
        bluetoothSession.session.release()
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(20) {
            if (condition()) return
            Thread.sleep(25)
        }
    }

    private data class TestController(
        val session: MediaSession,
        val controller: MediaController,
    )

    private fun createController(
        context: android.content.Context,
        packageName: String,
        title: String,
    ): TestController {
        val session = MediaSession(context, "observer-test-$packageName")
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .build()
        val playback = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
            .build()
        val controller = MediaController(context, session.sessionToken)
        shadowOf(controller).setPackageName(packageName)
        shadowOf(controller).setMetadata(metadata)
        shadowOf(controller).setPlaybackState(playback)
        return TestController(session, controller)
    }

    @Suppress("UNCHECKED_CAST")
    private fun notifyNotificationListener(observer: MediaSessionObserver, connected: Boolean) {
        val field = MediaSessionObserver::class.java.getDeclaredField("notificationConnectionListener")
        field.isAccessible = true
        (field.get(observer) as (Boolean) -> Unit)(connected)
    }

    @Suppress("UNCHECKED_CAST")
    private fun replaceActiveControllers(
        observer: MediaSessionObserver,
        controllers: List<MediaController>,
    ) {
        val field = MediaSessionObserver::class.java.getDeclaredField("activeControllers")
        field.isAccessible = true
        val activeControllers = field.get(observer) as java.util.concurrent.CopyOnWriteArrayList<MediaController>
        activeControllers.clear()
        activeControllers.addAll(controllers)
    }

    @Implements(MediaSessionManager::class)
    class ShadowMediaSessionManager {
        companion object {
            var failRegistration = false
            var addCount = 0
            private val listeners = mutableSetOf<MediaSessionManager.OnActiveSessionsChangedListener>()
            private var controllers = emptyList<MediaController>()

            fun reset() {
                failRegistration = false
                addCount = 0
                listeners.clear()
                controllers = emptyList()
            }

            fun setControllers(value: List<MediaController>) {
                controllers = value
            }

            fun dispatchControllers() {
                listeners.toList().forEach { listener ->
                    listener.onActiveSessionsChanged(controllers)
                }
            }
        }

        @Implementation
        fun addOnActiveSessionsChangedListener(
            listener: MediaSessionManager.OnActiveSessionsChangedListener,
            component: ComponentName?,
            handler: Handler?,
        ) {
            addCount++
            if (failRegistration) throw SecurityException("notification access denied")
            listeners += listener
        }

        @Implementation
        fun removeOnActiveSessionsChangedListener(
            listener: MediaSessionManager.OnActiveSessionsChangedListener,
        ) {
            listeners -= listener
        }

        @Implementation
        fun getActiveSessions(component: ComponentName?): List<MediaController> = controllers
    }
}
