package com.mmwtl.atlasmediaapi.media.session

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import com.mmwtl.atlasmediaapi.media.bridge.ArtworkRepository
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

    private fun waitFor(condition: () -> Boolean) {
        repeat(20) {
            if (condition()) return
            Thread.sleep(25)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun notifyNotificationListener(observer: MediaSessionObserver, connected: Boolean) {
        val field = MediaSessionObserver::class.java.getDeclaredField("notificationConnectionListener")
        field.isAccessible = true
        (field.get(observer) as (Boolean) -> Unit)(connected)
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
