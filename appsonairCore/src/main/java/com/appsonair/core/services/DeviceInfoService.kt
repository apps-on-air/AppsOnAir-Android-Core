package com.appsonair.core.services

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class DeviceInfoService private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DeviceInfoService? = null

        fun getInstance(context: Context): DeviceInfoService {
            return instance ?: synchronized(this) {
                instance ?: DeviceInfoService(context.applicationContext).also { instance = it }
            }
        }
    }

    val deviceInfo: JSONObject
        // Overloaded method without additionalInfo
        get() = getDeviceInfo(null)

    private val pm = context.packageManager

    private val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        pm.getPackageInfo(context.packageName, 0)
    }

    private val buildVersionNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        pInfo.versionCode
    }

    private val versionName = pInfo.versionName
    private val appsOnAirCoreVersion = BuildConfig.VERSION_NAME
    private val releaseVersion = getVersionName(versionName)
    private val bundleIdentifier = context.packageName
    private val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
    private val deviceModel = Build.MODEL
    private val deviceTotalStorage = formatSize(totalStorage)
    private val deviceOsVersion = Build.VERSION.RELEASE
    private val deviceScreenSize = screenSize

    fun getDeviceInfo(additionalInfo: Map<String, Any>?): JSONObject {
        val deviceInfo = JSONObject()
        val appInfo = JSONObject()
        val systemInfo = JSONObject()
        try {
            // Adding additional info in app info
            additionalInfo?.forEach { (key, value) -> appInfo.put(key, value) }

            // App information that will remain unchanged
            appInfo.put("releaseVersionNumber", releaseVersion)
            appInfo.put("buildVersionNumber", buildVersionNumber)
            appInfo.put("appsOnAirCoreVersion", appsOnAirCoreVersion)
            appInfo.put("bundleIdentifier", bundleIdentifier)
            appInfo.put("appName", appName)

            // Device information that will remain unchanged
            deviceInfo.put("deviceTotalStorage", deviceTotalStorage)
            deviceInfo.put("deviceModel", deviceModel)
            deviceInfo.put("deviceOsVersion", deviceOsVersion)
            deviceInfo.put("deviceScreenSize", deviceScreenSize)

            // Device information that can change
            deviceInfo.put("deviceUsedStorage", formatSize(usedStorage))
            deviceInfo.put("deviceMemory", formatSize(deviceMemory))
            deviceInfo.put("appMemoryUsage", formatSize(appMemoryUsage))
            deviceInfo.put("deviceOrientation", deviceOrientation)
            deviceInfo.put("deviceRegionCode", Locale.getDefault().country)
            deviceInfo.put("deviceBatteryLevel", batteryLevel)
            deviceInfo.put("deviceRegionName", Locale.getDefault().displayCountry)
            deviceInfo.put("timezone", TimeZone.getDefault().id)
            deviceInfo.put("networkState", networkState)
            deviceInfo.put("brand",Build.BRAND)
            deviceInfo.put("manufacturer",Build.MANUFACTURER)
            deviceInfo.put("firstInstallTime",deviceFirstInstallTime)
            deviceInfo.put("batteryStatus",deviceBatteryStatus)
            deviceInfo.put("isSimulator",isRunningOnEmulator)
            deviceInfo.put("getNetworkType",getNetworkType)
            deviceInfo.put("platform","Android")

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

    private val getNetworkType: String
        get() {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = connectivityManager.activeNetwork ?: return "No Connection"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "No Connection"

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Unknown"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // Best guess based on downstream speed
                    when {
                        capabilities.linkDownstreamBandwidthKbps >= 50000 -> "5G/4G"
                        capabilities.linkDownstreamBandwidthKbps >= 10000 -> "4G"
                        capabilities.linkDownstreamBandwidthKbps >= 1000 -> "3G"
                        else -> "2G"
                    }
                }
                else -> "Unknown"
            }
        }

    private val isRunningOnEmulator: Boolean
        get() {
            val fingerprint = Build.FINGERPRINT
            val model = Build.MODEL
            val product = Build.PRODUCT
            val manufacturer = Build.MANUFACTURER
            val brand = Build.BRAND
            val device = Build.DEVICE
            val hardware = Build.HARDWARE

            return listOf(
                "google_sdk", "sdk", "sdk_gphone64_x86_64", "vbox86p", "emulator", "simulator", "goldfish",
                "ranchu", "generic", "miniSim", "genymotion"
            ).any {
                product.equals(it, ignoreCase = true) ||
                        model.equals(it, ignoreCase = true) ||
                        device.equals(it, ignoreCase = true) ||
                        brand.equals(it, ignoreCase = true) ||
                        manufacturer.equals(it, ignoreCase = true) ||
                        fingerprint.equals(it, ignoreCase = true) ||
                        hardware.equals(it, ignoreCase = true)
            }
        }

    private val deviceFirstInstallTime: String
        get() {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            try {
                val installDate = Date(packageInfo.firstInstallTime)
                return  SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.getDefault()).format(installDate)
            } catch (e: Exception) {

                return "Unavailable"
            }
        }

    private val deviceBatteryStatus: String
        get() {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val readableStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
                else -> "unknown"
            }
            return  readableStatus
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

        if (fSize >= 1024f) {
            fSize /= 1024f
            suffix = "KB"
            if (fSize >= 1024f) {
                fSize /= 1024f
                suffix = "MB"
                if (fSize >= 1024f) {
                    fSize /= 1024f
                    suffix = "GB"
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