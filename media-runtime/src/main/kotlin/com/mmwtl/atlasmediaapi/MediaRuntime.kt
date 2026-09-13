package com.mmwtl.atlasmediaapi

import android.annotation.SuppressLint
import android.content.Context
import com.mmwtl.atlasmediaapi.media.bridge.MediaBackendCoordinator

/**
 * Process-local owner for the media runtime.
 *
 * The service and diagnostics activity run in the `:media` process, so both
 * resolve the same coordinator without requiring a particular Application
 * subclass in the host application.
 */
@SuppressLint("StaticFieldLeak")
object MediaRuntime {
    @Volatile
    private var coordinatorInstance: MediaBackendCoordinator? = null

    fun coordinator(context: Context): MediaBackendCoordinator {
        coordinatorInstance?.let { return it }
        return synchronized(this) {
            coordinatorInstance ?: MediaBackendCoordinator(context.applicationContext).also {
                coordinatorInstance = it
            }
        }
    }
}
