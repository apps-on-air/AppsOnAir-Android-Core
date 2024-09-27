package com.appsonair.core.services

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.appsonair.core.interfaces.UpdateNetwork

class NetworkService {
    companion object {
        @JvmStatic
        @SuppressLint("ObsoleteSdkInt")
        fun checkConnectivity(context: Context, updateNetworkState: UpdateNetwork) {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    updateNetworkState.onUpdate(true)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    updateNetworkState.onUpdate(false)
                }
            }

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        }
    }
}