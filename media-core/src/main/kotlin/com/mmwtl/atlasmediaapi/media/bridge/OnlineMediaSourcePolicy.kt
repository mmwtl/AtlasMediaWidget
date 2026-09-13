package com.mmwtl.atlasmediaapi.media.bridge

import java.util.concurrent.atomic.AtomicReference

/** Keeps transient/competing OneOS ONLINE callbacks from replacing a useful MediaSession. */
class OnlineMediaSourcePolicy {
    private val preferredSession = AtomicReference<String?>(null)
    private val lastNativeSource = AtomicReference<BridgeAudioSource?>(null)

    fun onSession(ownerPackage: String, meaningful: Boolean): Boolean {
        if (!meaningful) return false
        preferredSession.set(ownerPackage)
        return true
    }

    fun onSessionGone(currentSource: BridgeAudioSource? = null): BridgeAudioSource? {
        val hadSession = preferredSession.getAndSet(null) != null
        return if (hadSession && currentSource == BridgeAudioSource.ONLINE) {
            lastNativeSource.get()
        } else {
            null
        }
    }

    fun onAudioSource(source: BridgeAudioSource) {
        if (source in setOf(
                BridgeAudioSource.BT,
                BridgeAudioSource.USB,
                BridgeAudioSource.RADIO,
            )
        ) {
            lastNativeSource.set(source)
        }
        if (source != BridgeAudioSource.ONLINE) onSessionGone()
    }

    fun acceptOneOs(source: BridgeAudioSource, meaningful: Boolean = true): Boolean =
        source != BridgeAudioSource.ONLINE || meaningful && preferredSession.get() == null
}
