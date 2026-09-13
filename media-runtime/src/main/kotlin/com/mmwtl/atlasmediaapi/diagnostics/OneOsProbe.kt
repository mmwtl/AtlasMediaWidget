package com.mmwtl.atlasmediaapi.diagnostics

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.geely.lib.oneosapi.OneOSApiManager
import com.geely.lib.oneosapi.listener.ServiceConnectionListener
import com.geely.lib.oneosapi.mediacenter.MediaCenterManager
import com.geely.lib.oneosapi.mediacenter.bean.DeviceInfo
import com.geely.lib.oneosapi.mediacenter.bean.Frequency
import com.geely.lib.oneosapi.mediacenter.bean.MediaData
import com.geely.lib.oneosapi.mediacenter.bean.MusicFileData
import com.geely.lib.oneosapi.mediacenter.bean.OnlineUserInfo
import com.geely.lib.oneosapi.mediacenter.bean.SearchResult
import com.geely.lib.oneosapi.mediacenter.constant.MediaCenterConstant
import com.geely.lib.oneosapi.mediacenter.listener.DeviceStateListener
import com.geely.lib.oneosapi.mediacenter.listener.IRadioStateListener
import com.geely.lib.oneosapi.mediacenter.listener.MusicStateListener
import com.geely.lib.oneosapi.mediacenter.listener.SourceStateListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

data class OneOsProbeReport(
    val status: Status,
    val elapsedMillis: Long,
    val mediaCenterAlive: Boolean = false,
    val currentAudioSource: String = "UNKNOWN",
    val currentAppSource: String = "UNKNOWN",
    val musicManagers: Map<String, Boolean> = emptyMap(),
    val radioManagerAlive: Boolean = false,
    val sourceCallbackRegistered: Boolean = false,
    val sourceCallbackCount: Int = 0,
    val lastSourceCallback: String = "none",
    val callbackProbeRegistered: Boolean = false,
    val musicCallbackCount: Int = 0,
    val deviceCallbackCount: Int = 0,
    val radioCallbackCount: Int = 0,
    val lastMusicCallback: String = "none",
    val lastDeviceCallback: String = "none",
    val lastRadioCallback: String = "none",
    val callbackRegistrationError: String? = null,
    val errorCode: String? = null
) {
    enum class Status {
        IDLE,
        CONNECTING,
        CONNECTED,
        TIMEOUT,
        ERROR
    }
}

/**
 * Milestone 1 and 2 read-only OneOS verification probe.
 */
class OneOsProbe(
    context: Context,
    private val onReportChanged: (OneOsProbeReport) -> Unit
) {
    companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val TAG = "AtlasOneOsProbe"
    }

    private val appContext = context.applicationContext
    private val apiManager = OneOSApiManager.getInstance(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectionListener: ServiceConnectionListener? = null
    private var mediaCenterManager: MediaCenterManager? = null
    private var currentReport = OneOsProbeReport(
        status = OneOsProbeReport.Status.IDLE,
        elapsedMillis = 0L
    )
    private var callbackCount = 0
    private var lastCallback = "none"
    private var callbackProbeRegistered = false
    private var musicCallbackCount = 0
    private var deviceCallbackCount = 0
    private var radioCallbackCount = 0
    private var lastMusicCallback = "none"
    private var lastDeviceCallback = "none"
    private var lastRadioCallback = "none"
    private var callbackRegistrationError: String? = null

    private var radioListener: IRadioStateListener? = null
    private val deviceListeners = mutableMapOf<MediaCenterConstant.AudioSource, DeviceStateListener>()

    private val sourceStateListener = object : SourceStateListener {
        override fun onSourceChanged(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource
        ) {
            callbackCount += 1
            lastCallback = "source=${source.name}, app=${appSource.name}"
            currentReport = currentReport.copy(
                currentAudioSource = source.name,
                currentAppSource = appSource.name,
                sourceCallbackCount = callbackCount,
                lastSourceCallback = lastCallback,
                status = OneOsProbeReport.Status.CONNECTED
            )
            onReportChanged(currentReport)
            Timber.tag(TAG).i("source callback source=%s app=%s", source.name, appSource.name)
        }

        override fun onPsdBtStateChanged(connected: Boolean) {
            lastCallback = "psdBtConnected=$connected"
            currentReport = currentReport.copy(
                sourceCallbackCount = callbackCount,
                lastSourceCallback = lastCallback
            )
            onReportChanged(currentReport)
            Timber.tag(TAG).i("psd BT callback connected=%s", connected)
        }

        override fun onWeCarFlowTabChanged(
            source: MediaCenterConstant.AudioSource,
            appSource: MediaCenterConstant.AppSource
        ) {
            lastCallback = "weCarFlowTab source=${source.name}, app=${appSource.name}"
            currentReport = currentReport.copy(
                currentAudioSource = source.name,
                currentAppSource = appSource.name,
                lastSourceCallback = lastCallback
            )
            onReportChanged(currentReport)
            Timber.tag(TAG).i("WeCarFlow callback source=%s app=%s", source.name, appSource.name)
        }
    }

    private val musicStateListener = object : MusicStateListener {
        override fun onMediaDataChanged(
            source: MediaCenterConstant.AudioSource,
            mediaData: MediaData?
        ) {
            musicCallbackCount += 1
            lastMusicCallback = "source=${source.name}, event=mediaDataChanged"
            publishCallbackReport()
        }

        override fun onPlayPositionChanged(
            source: MediaCenterConstant.AudioSource,
            current: Long,
            total: Long
        ) {
            musicCallbackCount += 1
            lastMusicCallback = "source=${source.name}, event=playPositionChanged($current/$total)"
            publishCallbackReport()
        }

        override fun onPlayStateChanged(
            source: MediaCenterConstant.AudioSource,
            state: MediaCenterConstant.PlayState
        ) {
            musicCallbackCount += 1
            lastMusicCallback = "source=${source.name}, event=playStateChanged(${state.name})"
            publishCallbackReport()
        }

        override fun onPlayListChanged(
            source: MediaCenterConstant.AudioSource,
            list: MutableList<MediaData>?
        ) {
            musicCallbackCount += 1
            lastMusicCallback = "source=${source.name}, event=playListChanged"
            publishCallbackReport()
        }

        override fun onFavorStateChanged(
            source: MediaCenterConstant.AudioSource,
            mediaData: MediaData?
        ) = Unit

        override fun onLrcLoad(
            source: MediaCenterConstant.AudioSource,
            lrc: String?,
            time: Long
        ) = Unit

        override fun onPlayModeChange(
            source: MediaCenterConstant.AudioSource,
            mode: MediaCenterConstant.PlayMode
        ) = Unit
    }

    suspend fun run(): OneOsProbeReport {
        val startedAt = System.currentTimeMillis()
        closeConnectionListener()
        closeCallbackProbe()
        callbackCount = 0
        lastCallback = "none"
        musicCallbackCount = 0
        deviceCallbackCount = 0
        radioCallbackCount = 0
        lastMusicCallback = "none"
        lastDeviceCallback = "none"
        lastRadioCallback = "none"
        callbackRegistrationError = null
        publish(
            OneOsProbeReport(
                status = OneOsProbeReport.Status.CONNECTING,
                elapsedMillis = 0L
            )
        )

        val connected = CompletableDeferred<Boolean>()
        val listener = object : ServiceConnectionListener {
            override fun onServiceBinderUpdated(binderType: Int) {}

            override fun onServiceConnectionChanged(connectionState: Boolean) {
                connected.complete(connectionState)
                Timber.tag(TAG).i("OneOS connection state=%s", connectionState)
            }
        }
        connectionListener = listener

        withContext(Dispatchers.Main.immediate) {
            apiManager.registerServiceConnectionListener(listener)
            apiManager.init()
        }

        val didConnect = withContext(Dispatchers.IO) {
            withTimeoutOrNull(CONNECTION_TIMEOUT_MS) { connected.await() } == true
        }
        if (!didConnect) {
            return finish(
                startedAt,
                OneOsProbeReport.Status.TIMEOUT,
                errorCode = "ONE_OS_BIND_TIMEOUT"
            )
        }

        return try {
            val report = withContext(Dispatchers.IO) {
                val manager = apiManager.getMediaCenterManager()
                    ?: return@withContext null
                mediaCenterManager = manager
                manager.addSourceStateListener(sourceStateListener)

                val managers = manager.musicManagerMap
                    .toSortedMap(compareBy { it.name })
                    .mapKeys { it.key.name }
                    .mapValues { it.value.isAlive }

                OneOsProbeReport(
                    status = OneOsProbeReport.Status.CONNECTED,
                    elapsedMillis = System.currentTimeMillis() - startedAt,
                    mediaCenterAlive = manager.isAlive,
                    currentAudioSource = manager.currentAudioSource.name,
                    currentAppSource = manager.currentAppSource.name,
                    musicManagers = managers,
                    radioManagerAlive = manager.radioManager.isAlive,
                    sourceCallbackRegistered = true,
                    sourceCallbackCount = callbackCount,
                    lastSourceCallback = lastCallback,
                    callbackProbeRegistered = callbackProbeRegistered,
                    musicCallbackCount = musicCallbackCount,
                    deviceCallbackCount = deviceCallbackCount,
                    radioCallbackCount = radioCallbackCount,
                    lastMusicCallback = lastMusicCallback,
                    lastDeviceCallback = lastDeviceCallback,
                    lastRadioCallback = lastRadioCallback,
                    callbackRegistrationError = callbackRegistrationError
                )
            }
            if (report == null) {
                finish(
                    startedAt,
                    OneOsProbeReport.Status.ERROR,
                    errorCode = "MEDIA_CENTER_UNAVAILABLE"
                )
            } else {
                report.also(::publish)
            }
        } catch (throwable: Throwable) {
            val errorCode = "READ_${throwable.javaClass.simpleName.take(64)}"
            Timber.tag(TAG).w("probe read failed errorClass=%s", throwable.javaClass.simpleName)
            finish(startedAt, OneOsProbeReport.Status.ERROR, errorCode = errorCode)
        }
    }

    suspend fun registerCoexistenceCallbacks(): OneOsProbeReport {
        val manager = mediaCenterManager
            ?: return currentReport.copy(callbackRegistrationError = "MEDIA_CENTER_UNAVAILABLE")
                .also(::publish)

        var musicOk = false
        var deviceOk = false
        var radioOk = false
        var error: String? = null

        withContext(Dispatchers.IO) {
            try {
                manager.musicAdapterManager.addMusicStateListener(musicStateListener)
                musicOk = true
            } catch (e: Exception) {
                error = "MUSIC_REGISTRATION_FAILED"
            }

            try {
                manager.musicManagerMap.forEach { (source, musicManager) ->
                    val dListener = object : DeviceStateListener {
                        override fun onDeviceStateChanged(
                            source: MediaCenterConstant.AudioSource,
                            state: MediaCenterConstant.DeviceState,
                            info: DeviceInfo?
                        ) {
                            deviceCallbackCount += 1
                            lastDeviceCallback = "source=${source.name}, state=${state.name}"
                            publishCallbackReport()
                        }

                        override fun onDeviceError(
                            source: MediaCenterConstant.AudioSource,
                            error: Int,
                            errorMsg: String?
                        ) {
                            deviceCallbackCount += 1
                            lastDeviceCallback = "source=${source.name}, error=$error"
                            publishCallbackReport()
                        }

                        override fun onBluetoothDeviceChange(
                            source: MediaCenterConstant.AudioSource,
                            deviceInfoList: MutableList<DeviceInfo>?
                        ) = Unit

                        override fun onAppExistStateChanged(
                            source: MediaCenterConstant.AudioSource,
                            appSource: MediaCenterConstant.AppSource,
                            existed: Boolean
                        ) = Unit

                        override fun onAppDied(appSource: MediaCenterConstant.AppSource) = Unit

                        override fun onScanPathFinish(
                            source: MediaCenterConstant.AudioSource,
                            musicFileDataList: MutableList<MusicFileData>?
                        ) = Unit

                        @Suppress("DEPRECATION")
                        override fun onSearchSongResult(
                            source: MediaCenterConstant.AudioSource,
                            appSource: MediaCenterConstant.AppSource,
                            searchResults: MutableList<SearchResult>?
                        ) = Unit

                        @Suppress("DEPRECATION")
                        override fun onUserInfoResult(
                            source: MediaCenterConstant.AudioSource,
                            appSource: MediaCenterConstant.AppSource,
                            userInfo: OnlineUserInfo?
                        ) = Unit
                    }
                    deviceListeners[source] = dListener
                    musicManager.addDeviceStateListener(dListener)
                }
                deviceOk = true
            } catch (e: Exception) {
                if (error == null) error = "DEVICE_REGISTRATION_FAILED"
            }

            try {
                val rListener = object : IRadioStateListener.Default() {
                    override fun onCurrentFrequency(frequency: Frequency?) {
                        radioCallbackCount += 1
                        lastRadioCallback = "freq=${frequency?.frequency}"
                        publishCallbackReport()
                    }

                    override fun onRadioStatusChanged(status: Int) {
                        radioCallbackCount += 1
                        lastRadioCallback = "status=$status"
                        publishCallbackReport()
                    }
                }
                radioListener = rListener
                radioOk = manager.radioManager.openRadioAsync(rListener)
                if (!radioOk && error == null) error = "RADIO_REGISTRATION_FAILED"
            } catch (e: Exception) {
                if (error == null) error = "RADIO_EXCEPTION"
            }
        }

        callbackProbeRegistered = musicOk && deviceOk && radioOk
        callbackRegistrationError = error
        val report = currentReport.copy(
            callbackProbeRegistered = callbackProbeRegistered,
            callbackRegistrationError = callbackRegistrationError
        )
        publish(report)
        return report
    }

    fun unregisterCoexistenceCallbacks() {
        val manager = mediaCenterManager
        if (manager != null) {
            runCatching { manager.musicAdapterManager.removeMusicStateListener(musicStateListener) }
            radioListener?.let { runCatching { manager.radioManager.closeRadio(it) } }
            radioListener = null
            deviceListeners.forEach { (source, listener) ->
                runCatching { manager.musicManagerMap[source]?.removeDeviceStateListener(listener) }
            }
            deviceListeners.clear()
        }
        callbackProbeRegistered = false
        publish(currentReport.copy(callbackProbeRegistered = false))
    }

    fun close() {
        closeCallbackProbe()
        mediaCenterManager?.removeSourceStateListener(sourceStateListener)
        mediaCenterManager = null
        closeConnectionListener()
    }

    private fun closeConnectionListener() {
        connectionListener?.let(apiManager::unregisterServiceConnectionListener)
        connectionListener = null
    }

    private fun closeCallbackProbe() {
        unregisterCoexistenceCallbacks()
    }

    private fun publishCallbackReport() {
        val report = currentReport.copy(
            callbackProbeRegistered = callbackProbeRegistered,
            musicCallbackCount = musicCallbackCount,
            deviceCallbackCount = deviceCallbackCount,
            radioCallbackCount = radioCallbackCount,
            lastMusicCallback = lastMusicCallback,
            lastDeviceCallback = lastDeviceCallback,
            lastRadioCallback = lastRadioCallback,
            callbackRegistrationError = callbackRegistrationError
        )
        mainHandler.post {
            currentReport = report
            onReportChanged(report)
        }
    }

    private fun finish(
        startedAt: Long,
        status: OneOsProbeReport.Status,
        errorCode: String
    ): OneOsProbeReport {
        return OneOsProbeReport(
            status = status,
            elapsedMillis = System.currentTimeMillis() - startedAt,
            sourceCallbackCount = callbackCount,
            lastSourceCallback = lastCallback,
            errorCode = errorCode
        ).also(::publish)
    }

    private fun publish(report: OneOsProbeReport) {
        mainHandler.post {
            currentReport = report
            onReportChanged(report)
        }
    }
}
