package com.appsonair.core.services

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import java.util.TimeZone
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import com.appsonair.core.BuildConfig
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal class DeviceInfoService private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DeviceInfoService? = null

        private const val PREFS_NAME = "appsonair_core"
        private const val KEY_DEVICE_ID = "device_id"

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
    private val storage = getDeviceStorageDetails()
    private val totalStorage = storage.total
    private val usedStorage = storage.used
    private val deviceTotalStorage = totalStorage
    private val deviceOsVersion = Build.VERSION.RELEASE
    private val deviceScreenSize = screenSize

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Stable per-install identifier, generated once and then persisted.
    // Lifetime is not symmetric with iOS Core: this value lives in SharedPreferences and is
    // cleared on uninstall, while iOS keeps the equivalent in the Keychain, where it survives
    // uninstall. Consumers must not assume the two platforms behave the same way.
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            // commit() rather than apply(): the first write has to be durable before the value
            // is handed out, so a crash straight after generation cannot yield a different ID
            // on the next launch.
            prefs.edit().putString(KEY_DEVICE_ID, it).commit()
        }
    }

    // Android API level (e.g. 34). Android-only - iOS has no equivalent.
    val apiLevel: Int get() = Build.VERSION.SDK_INT

    // ISO 639-1 language code. Java reports the obsolete codes for three languages for
    // backward compatibility; they are normalized so Core reports the same value as iOS
    // for the same device language.
    val language: String
        get() = when (val code = Locale.getDefault().language) {
            "iw" -> "he"  // Hebrew
            "in" -> "id"  // Indonesian
            "ji" -> "yi"  // Yiddish
            else -> code
        }

    // IANA timezone identifier (e.g. "America/New_York").
    val timezone: String get() = TimeZone.getDefault().id

    // ISO 3166-1 alpha-2 region code (e.g. "US").
    val regionCode: String get() = Locale.getDefault().country

    // Device manufacturer, title-cased (e.g. "Samsung"). Same value getDeviceInfo() reports.
    val manufacturer: String
        get() = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

    // Primary locale as language_REGION (e.g. "en_IN"). Ported from AppRemark Android's
    // getLocale(). Overlaps `language` + `regionCode`, which are the same facts split apart.
    val locale: String
        get() {
            val primary = context.resources.configuration.locales[0]
            return "${primary.language}_${primary.country}"
        }

    // "light" / "dark" / "undefined". Ported from AppRemark Android's getThemeMode().
    val themeMode: String
        get() = when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO -> "light"
            Configuration.UI_MODE_NIGHT_YES -> "dark"
            else -> "undefined"
        }

    // System font scale, 1.0 being the default. Ported from AppRemark Android's getFontScale().
    val fontScale: Float
        get() = context.resources.configuration.fontScale

    // Store or sideload the app was installed from. Ported verbatim from AppRemark Android's
    // getInstallVendor() so the two report identical values. Note the vocabulary is NOT the
    // same as iOS Core's - Android reports store display names, iOS reports appStore /
    // testFlight / other.
    val installVendor: String
        get() = try {
            @Suppress("DEPRECATION") val installer =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    pm.getInstallerPackageName(context.packageName)
                }

            when (installer) {
                "com.android.vending" -> "Google Play Store"
                "com.amazon.venezia" -> "Amazon Appstore"
                null -> "Unknown"
                else -> installer
            }
        } catch (e: Exception) {
            "Unknown"
        }

    fun getDeviceInfo(additionalInfo: Map<String, Any>?): JSONObject {
        val deviceInfo = JSONObject()
        val appInfo = JSONObject()
        val systemInfo = JSONObject()
        val brand = Build.BRAND.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

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
            deviceInfo.put("deviceId", deviceId)
            deviceInfo.put("apiLevel", apiLevel)

            // Device information that can change
            deviceInfo.put("deviceUsedStorage", usedStorage)
            deviceInfo.put("deviceMemory", formatStandardStorageSize(deviceMemory))
            deviceInfo.put("appMemoryUsage", formatStandardStorageSize(appMemoryUsage))
            deviceInfo.put("deviceOrientation", deviceOrientation)
            deviceInfo.put("deviceRegionCode", regionCode)
            deviceInfo.put("language", language)
            deviceInfo.put("deviceBatteryLevel", batteryLevel)
            deviceInfo.put("deviceRegionName", Locale.getDefault().displayCountry)
            deviceInfo.put("timezone", timezone)
            deviceInfo.put("networkState", networkState)
            deviceInfo.put("brand",brand)
            deviceInfo.put("manufacturer",manufacturer)
            deviceInfo.put("firstInstallTime",deviceFirstInstallTime)
            deviceInfo.put("batteryStatus",deviceBatteryStatus)
            deviceInfo.put("isSimulator",isRunningOnEmulator)
            deviceInfo.put("networkType",networkType)
            deviceInfo.put("platform","Android")

            systemInfo.put("deviceInfo", deviceInfo)
            systemInfo.put("appInfo", appInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return systemInfo
    }

    // Cheap, synchronous device facts, bundled for callers that need several at once.
    // Deliberately excludes everything expensive or volatile that getDeviceInfo() reports -
    // no storage, memory, battery or network work - so this stays safe to call from a
    // synchronous payload builder on both platforms. The non-trivial reads are
    // firstInstallTime and installVendor, one PackageManager lookup each.
    //
    // No try/catch: with literal keys and String/Int/Boolean values these puts cannot throw,
    // and swallowing a failure would resurface as a confusing JSONException in the caller.
    fun getDeviceMetadata(): JSONObject = JSONObject().apply {
        put("deviceId", deviceId)
        put("language", language)
        put("apiLevel", apiLevel)
        put("osVersion", deviceOsVersion)
        put("timezone", timezone)
        put("regionCode", regionCode)
        put("appVersion", releaseVersion ?: "")
        put("buildVersionNumber", buildVersionNumber)
        put("platform", "Android")
        put("locale", locale)
        put("themeMode", themeMode)
        put("fontScale", fontScale.toDouble())
        put("deviceModel", deviceModel)
        // Raw model code, the counterpart of iOS Core's rawDeviceModel. On Android
        // Build.MODEL already IS the unmapped code ("SM-G996B"), so this is the same value
        // as deviceModel - it exists so the Push payload reads one key name on both
        // platforms, and so a later change to deviceModel cannot silently alter the wire
        // value the console maps to a marketing name.
        put("rawDeviceModel", deviceModel)
        put("manufacturer", manufacturer)
        put("installVendor", installVendor)
        put("isSimulator", isRunningOnEmulator)
        put("firstInstallTime", deviceFirstInstallTime)
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

    private val networkType: String
        get() {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = connectivityManager.activeNetwork ?: return "No Connection"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "No Connection"

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // Best guess based on downstream speed
                    when {
                        capabilities.linkDownstreamBandwidthKbps >= 50000 -> "5G"
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
        get() = try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            SimpleDateFormat("dd-MMM-yyyy hh:mm:ss a", Locale.US).apply {
                timeZone = java.util.TimeZone.getDefault()
            }.format(Date(pkgInfo.firstInstallTime))
        } catch (e: Exception) {
            "Unavailable"
        }

    private val deviceBatteryStatus: String
        get() {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val readableStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Not Charging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "Battery state is unknown"
                else -> "Unknown power state"
            }
            return  readableStatus
        }

    private data class StorageInfo(val total: String, val used: String, val available: String)
    @SuppressLint("ServiceCast")
    private fun getDeviceStorageDetails(): StorageInfo =
        if (isSamsungDevice()) getSamsungDeviceStorageDetails() else getStandardDeviceStorageDetails()

    private fun isSamsungDevice() =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    @SuppressLint("ServiceCast")
    private fun getSamsungDeviceStorageDetails(): StorageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = getSamsungStorageUuid(sm)
                val total = ssm.getTotalBytes(uuid)
                val free = ssm.getFreeBytes(uuid)
                StorageInfo(
                    formatSamsungStorageSize(total),
                    formatSamsungStorageSize(total - free),
                    formatSamsungStorageSize(free)
                )
            } catch (_: Exception) { getSamsungStorageDetailsUsingStatFs() }
        } else getSamsungStorageDetailsUsingStatFs()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getSamsungStorageUuid(sm: StorageManager): UUID = try {
        sm.primaryStorageVolume.uuid?.let(UUID::fromString) ?: StorageManager.UUID_DEFAULT
    } catch (_: Exception) {
        try {
            sm.storageVolumes.find { it.isPrimary && !it.isRemovable }?.uuid?.let(UUID::fromString)
                ?: StorageManager.UUID_DEFAULT
        } catch (_: Exception) { StorageManager.UUID_DEFAULT }
    }

    private fun getSamsungStorageDetailsUsingStatFs(): StorageInfo = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        StorageInfo(
            formatSamsungStorageSize(total),
            formatSamsungStorageSize(total - free),
            formatSamsungStorageSize(free)
        )
    } catch (_: Exception) { StorageInfo("0 GB", "0 GB", "0 GB") }

    private fun formatSamsungStorageSize(bytes: Long): String {
        val df = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
        return when {
            bytes >= 1_073_741_824 -> df.format(bytes / 1_073_741_824.0) + " GB"
            bytes >= 1_048_576 -> df.format(bytes / 1_048_576.0) + " MB"
            bytes >= 1_024 -> df.format(bytes / 1_024.0) + " KB"
            else -> "$bytes B"
        }
    }

    private fun getStandardDeviceStorageDetails(): StorageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid = sm.primaryStorageVolume.uuid?.let(UUID::fromString)
                ?: StorageManager.UUID_DEFAULT
            val total = ssm.getTotalBytes(uuid)
            val free = ssm.getFreeBytes(uuid)
            StorageInfo(
                formatStandardStorageSize(total),
                formatStandardStorageSize(total - free),
                formatStandardStorageSize(free)
            )
        } else {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            StorageInfo(
                formatStandardStorageSize(total),
                formatStandardStorageSize(total - free),
                formatStandardStorageSize(free)
            )
        }

    private fun formatStandardStorageSize(bytes: Long): String {
        val df = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
        return when {
            bytes >= 1_000_000_000 -> df.format(bytes / 1_000_000_000.0) + " GB"
            bytes >= 1_000_000 -> df.format(bytes / 1_000_000.0) + " MB"
            bytes >= 1_000 -> df.format(bytes / 1_000.0) + " KB"
            else -> "$bytes B"
        }
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
            val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            val memoryInfo = memoryInfoArray[0]
            val memoryKB = memoryInfo.totalPrivateDirty + memoryInfo.totalPrivateClean
            return memoryKB * 1024L  // Convert KB to Bytes
        }


    private val batteryLevel: String
        get() {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            return if (level >= 0) "$level%" else "Unavailable"
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
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile Data"
                else -> "No Connection"
            }
        }
}