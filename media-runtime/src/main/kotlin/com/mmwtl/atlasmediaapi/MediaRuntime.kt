package com.mmwtl.atlasmediaapi

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.mmwtl.atlasmediaapi.media.bridge.MediaBackendCoordinator
import timber.log.Timber

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
            coordinatorInstance ?: run {
                if (Timber.treeCount == 0) Timber.plant(RuntimeLogTree())
                MediaBackendCoordinator(context.applicationContext).also {
                    coordinatorInstance = it
                }
            }
        }
    }

    /**
     * Sends runtime Timber messages to logcat, tagged `Atlas.<class>`. Release builds keep INFO
     * and above.
     */
    private class RuntimeLogTree : Timber.DebugTree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean =
            BuildConfig.DEBUG || priority >= Log.INFO

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, tag?.let { "Atlas.$it" } ?: "Atlas.MediaRuntime", message, t)
        }
    }
}
