package com.mmwtl.atlasmediaapi.media.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Internet as Android reports it: the default network has INTERNET and passed validation. A
 * Wi-Fi or modem link without a working connection does not count.
 */
object InternetAvailability {
    fun isValidated(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
}
