package com.example.appsonair_android_core

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.appsonair.core.interfaces.UpdateNetwork
import com.appsonair.core.services.CoreService
import com.appsonair.core.services.NetworkService
import com.example.appsonair_android_core.ui.theme.AppsOnAirAndroidCoreTheme

class MainActivity : ComponentActivity() {
    @Suppress("PrivatePropertyName")
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppsOnAirAndroidCoreTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.White
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Greeting(
                            context = this@MainActivity
                        )
                    }
                }
            }
        }

        val deviceInfo = CoreService.getDeviceInfo(this,)
        Log.d("deviceInfo", deviceInfo.toString())

        val appId: String = CoreService.getAppId(this)
        Log.d(TAG, "AppsonairAppId: $appId")

        val updateNetworkState = UpdateNetwork { isConnected ->
            Log.d(TAG, "hasNetworkConnection: $isConnected")
        }

        NetworkService.checkConnectivity(this, updateNetworkState)
    }
}

@Composable
fun Greeting( modifier: Modifier = Modifier, context: Context) {

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = {
            val deviceInfo = CoreService.getDeviceInfo(context)
            Log.d("deviceInfo", deviceInfo.toString())

        }) {
            Text(
                text = "Get Device Info",
                modifier = modifier
            )
        }
        Button(onClick = {
            val appId: String = CoreService.getAppId(context)
            Log.d("AppsonairAppId", appId)

        }) {
            Text(
                text = "Get App ID",
                modifier = modifier
            )
        }
    }
}