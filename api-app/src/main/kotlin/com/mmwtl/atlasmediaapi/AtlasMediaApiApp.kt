package com.mmwtl.atlasmediaapi

import android.app.Application
import com.mmwtl.atlasmediaapi.media.bridge.MediaBackendCoordinator
import com.mmwtl.atlasmediaapi.standalone.BuildConfig
import timber.log.Timber

class AtlasMediaApiApp : Application() {
    val coordinator: MediaBackendCoordinator
        get() = MediaRuntime.coordinator(this)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        MediaRuntime.coordinator(this)
    }
}
