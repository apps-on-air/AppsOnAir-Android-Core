package com.example.appsonair_android_core

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
//        val additionalInfo: MutableMap<String, Any> = HashMap()
//        additionalInfo["remark"] = "0.0.1"
//        additionalInfo["Sync"] = "0.0.1"
        val deviceInfo = CoreService.getDeviceInfo(this,)
        Log.d("deviceInfo", deviceInfo.toString())

        val appId: String = CoreService.getAppId(this)
        Log.d(TAG, "appId: $appId")

        val updateNetworkState = UpdateNetwork { isConnected ->
            Log.d(TAG, "hasNetworkConnection: $isConnected")
        }

        NetworkService.checkConnectivity(this, updateNetworkState)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppsOnAirAndroidCoreTheme {
        Greeting("Android")
    }
}