package com.appsonair.core.services

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.icu.util.TimeZone
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import com.appsonair.core.BuildConfig
import org.json.JSONObject
import java.util.Locale

class DeviceInfoService(private val context: Context) {
    val deviceInfo: JSONObject
        // Overloaded method without additionalInfo
        get() = getDeviceInfo(null)

    fun getDeviceInfo(additionalInfo: Map<String, Any>?): JSONObject {
        val deviceInfo = JSONObject()
        val appInfo = JSONObject()
        val systemInfo = JSONObject()
        try {
            val pm = context.packageManager
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val versionName = pInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }


            if (!additionalInfo.isNullOrEmpty()) {
                for ((key, value) in additionalInfo) {
                    appInfo.put(key, value)
                }
            }

            appInfo.put("releaseVersionNumber", getVersionName(versionName))
            appInfo.put("buildVersionNumber", versionCode)
            appInfo.put("appsOnAirCoreVersion", BuildConfig.VERSION_NAME)
            appInfo.put("bundleIdentifier", context.packageName)
            appInfo.put("appName", context.applicationInfo.loadLabel(context.packageManager).toString())

            deviceInfo.put("deviceModel", Build.MODEL)
            deviceInfo.put("deviceUsedStorage", formatSize(usedStorage))
            deviceInfo.put("deviceTotalStorage", formatSize(totalStorage))
            deviceInfo.put("deviceMemory", formatSize(deviceMemory))
            deviceInfo.put("appMemoryUsage", formatSize(appMemoryUsage))
            deviceInfo.put("deviceOrientation", deviceOrientation)
            deviceInfo.put("deviceOsVersion", Build.VERSION.RELEASE)
            deviceInfo.put("deviceRegionCode", Locale.getDefault().country)
            deviceInfo.put("deviceBatteryLevel", batteryLevel)
            deviceInfo.put("deviceScreenSize", screenSize)
            deviceInfo.put("deviceRegionName", Locale.getDefault().displayCountry)
            deviceInfo.put("timezone", TimeZone.getDefault().id)
            deviceInfo.put("networkState", networkState)

            systemInfo.put("deviceInfo", deviceInfo)
            systemInfo.put("appInfo", appInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return systemInfo
    }

    private fun getVersionName(version: String?): String? {
        if (version != null && version.contains("+")) {
            return version.split("\\+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        }
        return version
    }

    private val deviceOrientation: String
        get() {
            val orientation = context.resources.configuration.orientation
            return when (orientation) {
                Configuration.ORIENTATION_PORTRAIT -> {
                    "Portrait"
                }
                Configuration.ORIENTATION_LANDSCAPE -> {
                    "Landscape"
                }
                else -> {
                    "Undefined"
                }
            }
        }

    private val usedStorage: Long
        get() {
            val path = Environment.getDataDirectory()
            val totalSpace = path.totalSpace
            val freeSpace = path.freeSpace
            return totalSpace - freeSpace
        }

    private val totalStorage: Long
        get() {
            val path = Environment.getDataDirectory()
            return path.totalSpace
        }

    private val deviceMemory: Long
        get() {
            val mi = ActivityManager.MemoryInfo()
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getMemoryInfo(mi)
            return mi.totalMem
        }

    private val appMemoryUsage: Long
        get() {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryClass = activityManager.memoryClass
            return memoryClass * 1024L * 1024L // Converting MB to Bytes
        }


    private val batteryLevel: Int
        get() {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }

    private val screenSize: String
        get() {
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            return width.toString() + "x" + height
        }

    private val networkState: String
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val networkCapabilities = cm.getNetworkCapabilities(activeNetwork)

            return when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                else -> "No Connection"
            }
        }


    private fun formatSize(size: Long): String {
        var suffix: String? = null
        var fSize = size.toFloat()

        if (size >= 1024) {
            suffix = "KB"
            fSize /= 1024f
            if (size >= 1024) {
                suffix = "MB"
                fSize /= 1024f
                if (size >= 1024) {
                    suffix = "GB"
                    fSize /= 1024f
                }
            }
        }
        val resultBuffer = StringBuilder(fSize.toString())
        val commaOffset = resultBuffer.indexOf(".")
        if (commaOffset >= 0) {
            val endIndex = commaOffset + 3
            if (endIndex < resultBuffer.length) {
                resultBuffer.setLength(endIndex)
            }
        }
        if (suffix != null) resultBuffer.append(suffix)
        return resultBuffer.toString()
    }
}