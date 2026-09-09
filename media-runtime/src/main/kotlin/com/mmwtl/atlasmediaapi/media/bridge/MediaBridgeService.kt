package com.mmwtl.atlasmediaapi.media.bridge

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.net.toUri
import com.mmwtl.atlasmediaapi.MediaRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class MediaBridgeService : Service() {
    companion object {
        private const val MAX_GRANTED_URIS_PER_CLIENT = 512
    }

    private data class Client(
        val packageNames: MutableSet<String>,
        val messenger: Messenger,
        val deathRecipient: IBinder.DeathRecipient,
        val grantedArtworkUris: LinkedHashSet<String> = LinkedHashSet(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clients = mutableMapOf<IBinder, Client>()
    private lateinit var incomingMessenger: Messenger
    private var snapshotJob: Job? = null

    private val coordinator: MediaBackendCoordinator
        get() = MediaRuntime.coordinator(this)

    override fun onCreate() {
        super.onCreate()
        incomingMessenger = Messenger(IncomingHandler())
        snapshotJob = scope.launch {
            coordinator.stateRepository.snapshots.collectLatest { snapshot ->
                clients.values.toList().forEach { client -> sendSnapshot(client, snapshot) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    override fun onDestroy() {
        snapshotJob?.cancel()
        clients.keys.toList().forEach(::removeClient)
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("HandlerLeak")
    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val version = message.data?.getInt(MediaBridgeContract.Key.PROTOCOL_VERSION, -1) ?: -1
            if (MediaBridgeContract.negotiate(version) != MediaBridgeContract.Status.OK) {
                sendProtocolError(message.replyTo, message.data, version)
                return
            }

            when (message.what) {
                MediaBridgeContract.ClientMessage.REGISTER ->
                    registerClient(message)

                MediaBridgeContract.ClientMessage.UNREGISTER ->
                    unregisterClient(message)

                MediaBridgeContract.ClientMessage.GET_SNAPSHOT ->
                    handleGetSnapshot(message)

                MediaBridgeContract.ClientMessage.COMMAND ->
                    handleCommand(message)

                MediaBridgeContract.ClientMessage.GET_RADIO_STATIONS ->
                    handleGetRadioStations(message)

                else -> sendError(
                    message.replyTo,
                    message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
                    MediaBridgeContract.Status.INVALID_REQUEST,
                    "unknown message type",
                )
            }
        }
    }

    private fun registerClient(message: Message) {
        val replyTo = message.replyTo ?: return
        val binder = replyTo.binder
        val packageNames = packageManager.getPackagesForUid(message.sendingUid)
            .orEmpty()
            .toSet()

        if (!coordinator.callerAccessPolicy.isAllowed(message.sendingUid, packageNames)) {
            sendError(
                replyTo,
                message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
                MediaBridgeContract.Status.UNAUTHORIZED,
                "caller not authorized",
            )
            return
        }

        val existing = clients[binder]
        if (existing == null) {
            val deathRecipient = IBinder.DeathRecipient {
                scope.launch { removeClient(binder) }
            }
            try {
                binder.linkToDeath(deathRecipient, 0)
            } catch (_: RemoteException) {
                return
            }
            clients[binder] = Client(packageNames.toMutableSet(), replyTo, deathRecipient)
            coordinator.clientRegistered()
        } else {
            existing.packageNames += packageNames
        }

        send(
            replyTo,
            MediaBridgeContract.ServerMessage.REGISTERED,
            Bundle().apply {
                putInt(MediaBridgeContract.Key.PROTOCOL_VERSION, MediaBridgeContract.PROTOCOL_VERSION)
                putInt(
                    MediaBridgeContract.Key.MIN_PROTOCOL_VERSION,
                    MediaBridgeContract.MIN_PROTOCOL_VERSION,
                )
                putInt(
                    MediaBridgeContract.Key.MAX_PROTOCOL_VERSION,
                    MediaBridgeContract.MAX_PROTOCOL_VERSION,
                )
                putString(
                    MediaBridgeContract.Key.REQUEST_ID,
                    message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
                )
                putInt(MediaBridgeContract.Key.STATUS, MediaBridgeContract.Status.OK)
            },
        )

        val snapshot = coordinator.stateRepository.snapshot()
        clients[binder]?.let { sendSnapshot(it, snapshot) }
    }

    private fun unregisterClient(message: Message) {
        val binder = message.replyTo?.binder ?: return
        if (clients[binder] == null) return
        removeClient(binder)
    }

    private fun handleGetSnapshot(message: Message) {
        val client = registeredClient(message) ?: return
        sendSnapshot(
            client,
            coordinator.stateRepository.snapshot(),
            message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
        )
    }

    private fun handleCommand(message: Message) {
        val client = registeredClient(message) ?: return
        val request = message.data?.toMediaCommandRequest()
        if (request == null) {
            val rawCommand = message.data?.getString(MediaBridgeContract.Key.COMMAND).orEmpty()
            val knownCommand = runCatching { MediaCommand.valueOf(rawCommand) }.isSuccess
            sendError(
                client.messenger,
                message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
                if (rawCommand.isBlank() || knownCommand) {
                    MediaBridgeContract.Status.INVALID_REQUEST
                } else {
                    MediaBridgeContract.Status.UNKNOWN_COMMAND
                },
                if (rawCommand.isBlank() || knownCommand) {
                    "invalid command payload"
                } else {
                    "unknown command: $rawCommand"
                },
            )
            return
        }

        scope.launch(Dispatchers.IO) {
            val result = coordinator.commandMutex.withLock {
                runCatching {
                    if (coordinator.isDemoMode()) coordinator.demoBackend.execute(request)
                    else coordinator.commandRouter.execute(request)
                }
                    .onFailure(Timber::e)
                    .getOrElse {
                        MediaCommandResult(MediaBridgeContract.Status.FAILED, it.message.orEmpty())
                    }
            }
            scope.launch {
                clients[client.messenger.binder]?.let {
                    sendCommandResult(it, request.requestId, result)
                }
            }
        }
    }

    private fun handleGetRadioStations(message: Message) {
        val client = registeredClient(message) ?: return
        val requestId = message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty()
        scope.launch(Dispatchers.IO) {
            val lists = runCatching {
                if (coordinator.isDemoMode()) coordinator.demoBackend.radioStationLists()
                else coordinator.commandHost.radioStationLists()
            }
                .onFailure(Timber::e)
                .getOrNull()
            scope.launch {
                val currentClient = clients[client.messenger.binder] ?: return@launch
                if (lists == null) {
                    sendError(
                        currentClient.messenger,
                        requestId,
                        MediaBridgeContract.Status.BACKEND_UNAVAILABLE,
                        "OneOS radio backend unavailable",
                    )
                } else {
                    sendRadioStations(currentClient, requestId, lists)
                }
            }
        }
    }

    private fun registeredClient(message: Message): Client? {
        val replyTo = message.replyTo
        val client = replyTo?.binder?.let(clients::get)
        if (client == null) {
            sendError(
                replyTo,
                message.data?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
                MediaBridgeContract.Status.NOT_REGISTERED,
                "register this Messenger before requesting state or commands",
            )
            return null
        }
        return client
    }

    private fun removeClient(binder: IBinder) {
        val removed = clients.remove(binder) ?: return
        runCatching { binder.unlinkToDeath(removed.deathRecipient, 0) }
        removed.grantedArtworkUris.forEach { artworkUri ->
            removed.packageNames.forEach { packageName ->
                runCatching {
                    revokeUriPermission(
                        packageName,
                        artworkUri.toUri(),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onFailure(Timber::e)
            }
        }
        coordinator.clientUnregistered()
    }

    private fun sendSnapshot(client: Client, snapshot: MediaSnapshot, requestId: String = "") {
        grantArtworkUri(client, snapshot.artworkUri)
        val data = snapshot.toBundle().apply {
            if (requestId.isNotBlank()) putString(MediaBridgeContract.Key.REQUEST_ID, requestId)
        }
        if (!send(client.messenger, MediaBridgeContract.ServerMessage.SNAPSHOT, data)) {
            removeClient(client.messenger.binder)
        }
    }

    private fun sendRadioStations(
        client: Client,
        requestId: String,
        lists: RadioStationLists,
    ) {
        (lists.saved + lists.favorites).forEach { station ->
            grantArtworkUri(client, station.artworkUri)
        }
        val data = lists.toBundle().apply {
            putString(MediaBridgeContract.Key.REQUEST_ID, requestId)
            putInt(MediaBridgeContract.Key.STATUS, MediaBridgeContract.Status.OK)
            putLong(
                MediaBridgeContract.Key.GENERATION,
                coordinator.stateRepository.snapshot().generation,
            )
        }
        if (!send(client.messenger, MediaBridgeContract.ServerMessage.RADIO_STATIONS, data)) {
            removeClient(client.messenger.binder)
        }
    }

    private fun grantArtworkUri(client: Client, artworkUri: String) {
        if (artworkUri.isBlank() || !client.grantedArtworkUris.add(artworkUri)) return
        val uri = artworkUri.toUri()
        client.packageNames.forEach { packageName ->
            runCatching {
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure(Timber::e)
        }
        while (client.grantedArtworkUris.size > MAX_GRANTED_URIS_PER_CLIENT) {
            val oldestUri = client.grantedArtworkUris.first()
            client.grantedArtworkUris.remove(oldestUri)
            val oldestUriObj = oldestUri.toUri()
            client.packageNames.forEach { packageName ->
                runCatching {
                    revokeUriPermission(
                        packageName,
                        oldestUriObj,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onFailure(Timber::e)
            }
        }
    }

    private fun sendCommandResult(
        client: Client,
        requestId: String,
        result: MediaCommandResult,
    ) {
        send(
            client.messenger,
            MediaBridgeContract.ServerMessage.COMMAND_RESULT,
            Bundle().apply {
                putInt(MediaBridgeContract.Key.PROTOCOL_VERSION, MediaBridgeContract.PROTOCOL_VERSION)
                putString(MediaBridgeContract.Key.REQUEST_ID, requestId)
                putInt(MediaBridgeContract.Key.STATUS, result.status)
                putString(MediaBridgeContract.Key.MESSAGE, result.message)
                putLong(
                    MediaBridgeContract.Key.GENERATION,
                    coordinator.stateRepository.snapshot().generation,
                )
            },
        )
    }

    private fun sendProtocolError(replyTo: Messenger?, input: Bundle?, version: Int) {
        send(
            replyTo,
            MediaBridgeContract.ServerMessage.ERROR,
            Bundle().apply {
                putInt(MediaBridgeContract.Key.PROTOCOL_VERSION, MediaBridgeContract.PROTOCOL_VERSION)
                putInt(
                    MediaBridgeContract.Key.MIN_PROTOCOL_VERSION,
                    MediaBridgeContract.MIN_PROTOCOL_VERSION,
                )
                putInt(
                    MediaBridgeContract.Key.MAX_PROTOCOL_VERSION,
                    MediaBridgeContract.MAX_PROTOCOL_VERSION,
                )
                putString(
                    MediaBridgeContract.Key.REQUEST_ID,
                    input?.getString(MediaBridgeContract.Key.REQUEST_ID).orEmpty(),
                )
                putInt(MediaBridgeContract.Key.STATUS, MediaBridgeContract.Status.UNSUPPORTED_VERSION)
                putString(MediaBridgeContract.Key.MESSAGE, "unsupported protocolVersion=$version")
            },
        )
    }

    private fun sendError(replyTo: Messenger?, requestId: String, status: Int, message: String) {
        send(
            replyTo,
            MediaBridgeContract.ServerMessage.ERROR,
            Bundle().apply {
                putInt(MediaBridgeContract.Key.PROTOCOL_VERSION, MediaBridgeContract.PROTOCOL_VERSION)
                putString(MediaBridgeContract.Key.REQUEST_ID, requestId)
                putInt(MediaBridgeContract.Key.STATUS, status)
                putString(MediaBridgeContract.Key.MESSAGE, message)
            },
        )
    }

    private fun send(target: Messenger?, what: Int, data: Bundle): Boolean {
        if (target == null) return false
        return try {
            target.send(Message.obtain(null, what).apply { this.data = data })
            true
        } catch (_: RemoteException) {
            false
        }
    }
}
