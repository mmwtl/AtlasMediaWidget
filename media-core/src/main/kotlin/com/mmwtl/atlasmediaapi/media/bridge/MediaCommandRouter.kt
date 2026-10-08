package com.mmwtl.atlasmediaapi.media.bridge

enum class SessionPlaybackState {
    PLAYING,
    NOT_PLAYING,
}

interface MediaSessionCommandTarget {
    val packageName: String
    val playbackState: SessionPlaybackState
    val capabilities: Long
    val lastPositionUpdateTime: Long get() = 0L

    fun play(): Boolean
    fun pause(): Boolean
    fun toggle(): Boolean = if (playbackState == SessionPlaybackState.PLAYING) pause() else play()
    fun next(): Boolean
    fun previous(): Boolean
    fun seekTo(position: Long): Boolean
}

interface MediaCommandHost {
    fun backendAvailable(): Boolean
    fun blocksMediaCommands(): Boolean
    fun ownedSession(): MediaSessionCommandTarget? = null
    suspend fun executeNative(request: MediaCommandRequest): MediaCommandResult?
    fun preferredSession(): MediaSessionCommandTarget? = null
    fun sessions(): List<MediaSessionCommandTarget>
    /** Package of the session the card currently shows; commands go to it before native sources. */
    fun currentVisiblePackage(): String = ""
    fun currentMediaPackage(): String
    fun defaultMediaPackage(): String
    fun setCurrentMediaPackage(packageName: String)
    suspend fun sendFallback(packageName: String, command: MediaCommand): Boolean
    suspend fun startDefaultAndPlay(packageName: String): Boolean
    suspend fun setSource(
        source: BridgeAudioSource,
        appSource: String?,
        autoplay: Boolean,
    ): Boolean
    suspend fun tuneRadio(target: RadioStationTarget, autoplay: Boolean): Boolean = false
    /** Sends a player-defined action to the session that currently publishes it. */
    suspend fun sendCustomAction(action: String): Boolean = false
}

/** Standalone command arbitration for Media Bridge IPC requests. */
class MediaCommandRouter(private val host: MediaCommandHost) {
    suspend fun execute(request: MediaCommandRequest): MediaCommandResult {
        if (!host.backendAvailable()) return result(MediaBridgeContract.Status.BACKEND_UNAVAILABLE)

        if (request.command == MediaCommand.SET_SOURCE) {
            val source = request.source ?: return result(MediaBridgeContract.Status.INVALID_REQUEST)
            return if (host.setSource(source, request.appSource, request.autoplay)) {
                result(MediaBridgeContract.Status.OK)
            } else {
                result(MediaBridgeContract.Status.FAILED, "source switch failed")
            }
        }

        if (request.command == MediaCommand.TUNE_RADIO) {
            val target = request.radioStation
                ?: return result(MediaBridgeContract.Status.INVALID_REQUEST)
            return if (host.tuneRadio(target, request.autoplay)) {
                result(MediaBridgeContract.Status.OK)
            } else {
                result(MediaBridgeContract.Status.FAILED, "radio tune failed")
            }
        }

        if (request.command == MediaCommand.CUSTOM_ACTION) {
            val action = request.customAction
                ?: return result(MediaBridgeContract.Status.INVALID_REQUEST)
            return if (host.sendCustomAction(action)) {
                result(MediaBridgeContract.Status.OK)
            } else {
                result(MediaBridgeContract.Status.NOT_SUPPORTED, "no session publishes this action")
            }
        }

        if (host.blocksMediaCommands()) {
            val ownedSession = host.ownedSession()
            return if (ownedSession != null) {
                executeOnSession(ownedSession, request)
            } else {
                result(MediaBridgeContract.Status.NOT_SUPPORTED, "source owns media keys")
            }
        }

        val target = selectSession(request.command)
        if (target != null && shouldPreferSession(target)) {
            return executeOnSession(target, request)
        }

        host.executeNative(request)?.let { nativeResult ->
            if (nativeResult.status != MediaBridgeContract.Status.NOT_SUPPORTED) {
                return nativeResult
            }
        }

        if (target != null) return executeOnSession(target, request)

        // Without a session, play starts the configured player; the last used one only when none
        // is configured. Other commands can reach only a player that still has a session.
        val startsPlayback = request.command == MediaCommand.PLAY ||
            request.command == MediaCommand.TOGGLE
        val fallbackPackage = if (startsPlayback) {
            host.defaultMediaPackage().ifBlank { host.currentMediaPackage() }
        } else {
            host.currentMediaPackage().ifBlank { host.defaultMediaPackage() }
        }
        if (fallbackPackage.isBlank()) {
            return result(MediaBridgeContract.Status.NOT_SUPPORTED, "no media target")
        }

        val sent = if (startsPlayback) {
            host.startDefaultAndPlay(fallbackPackage)
        } else if (request.command == MediaCommand.SEEK_TO) {
            false
        } else {
            host.sendFallback(fallbackPackage, request.command)
        }
        if (sent) host.setCurrentMediaPackage(fallbackPackage)
        return if (sent) result(MediaBridgeContract.Status.OK) else {
            result(MediaBridgeContract.Status.NOT_SUPPORTED, "target rejected command")
        }
    }

    private fun selectSession(command: MediaCommand): MediaSessionCommandTarget? {
        val requiredCapability = requiredCapability(command)
        host.preferredSession()
            ?.takeIf { it.supports(requiredCapability) }
            ?.let { return it }

        val sessions = host.sessions()
        sessions.firstOrNull {
            it.playbackState == SessionPlaybackState.PLAYING &&
                    it.supports(requiredCapability)
        }?.let { return it }

        val currentPackage = host.currentMediaPackage()
        if (currentPackage.isNotBlank()) {
            sessions.firstOrNull {
                it.packageName == currentPackage && it.supports(requiredCapability)
            }?.let { return it }
        }

        return sessions.firstOrNull { it.supports(requiredCapability) }
            ?: host.defaultMediaPackage().takeIf(String::isNotBlank)?.let { packageName ->
                sessions.firstOrNull {
                    it.packageName == packageName && it.supports(requiredCapability)
                }
            }
    }

    private fun shouldPreferSession(target: MediaSessionCommandTarget): Boolean =
        (host.currentVisiblePackage().isNotBlank() && target.packageName == host.currentVisiblePackage()) ||
                target.playbackState == SessionPlaybackState.PLAYING ||
                (host.currentMediaPackage().isNotBlank() && target.packageName == host.currentMediaPackage())

    private fun requiredCapability(command: MediaCommand): Long = when (command) {
        MediaCommand.PLAY -> MediaCapabilities.PLAY
        MediaCommand.PAUSE -> MediaCapabilities.PAUSE
        MediaCommand.TOGGLE -> MediaCapabilities.TOGGLE
        MediaCommand.NEXT -> MediaCapabilities.NEXT
        MediaCommand.PREVIOUS -> MediaCapabilities.PREVIOUS
        MediaCommand.SEEK_TO -> MediaCapabilities.SEEK_TO
        MediaCommand.SET_SOURCE -> MediaCapabilities.SET_SOURCE
        MediaCommand.TUNE_RADIO -> MediaCapabilities.TUNE_RADIO
        MediaCommand.CUSTOM_ACTION -> 0L
    }

    private fun MediaSessionCommandTarget.supports(capability: Long): Boolean =
        capabilities and capability != 0L

    private fun executeOnSession(
        target: MediaSessionCommandTarget,
        request: MediaCommandRequest,
    ): MediaCommandResult {
        val requiredCapability = requiredCapability(request.command)
        if (target.capabilities and requiredCapability == 0L) {
            return result(MediaBridgeContract.Status.NOT_SUPPORTED, "session capability missing")
        }

        val succeeded = when (request.command) {
            MediaCommand.PLAY -> target.play()

            MediaCommand.PAUSE -> target.pause()
            MediaCommand.TOGGLE -> target.toggle()

            MediaCommand.NEXT -> target.next()
            MediaCommand.PREVIOUS -> target.previous()
            MediaCommand.SEEK_TO -> target.seekTo(request.position ?: return result(
                MediaBridgeContract.Status.INVALID_REQUEST,
            ))

            MediaCommand.SET_SOURCE -> false
            MediaCommand.TUNE_RADIO -> false
            MediaCommand.CUSTOM_ACTION -> false
        }
        if (succeeded) host.setCurrentMediaPackage(target.packageName)
        return if (succeeded) result(MediaBridgeContract.Status.OK) else {
            result(MediaBridgeContract.Status.FAILED, "session command failed")
        }
    }

    private fun result(status: Int, message: String = "") = MediaCommandResult(status, message)
}
