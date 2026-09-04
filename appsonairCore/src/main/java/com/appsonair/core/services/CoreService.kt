package com.appsonair.core.services

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.appsonair.core.R
import org.json.JSONObject

class CoreService {
    companion object {
        @JvmStatic
        fun getAppId(context: Context): String {
            return try {
                val appInfo = context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                )
                val bundle = appInfo.metaData
                if (bundle != null) {
                    val appId = bundle.getString("AppsonairAppId") ?: bundle.getString("appId") ?: ""
                    appId.ifEmpty {
                        Log.d(
                            "CoreService",
                            "AppsOnAirCore: " + context.getString(R.string.error_add_app_id)
                        )
                        ""
                    }
                } else {
                    Log.d(
                        "CoreService",
                        "AppsOnAirCore: " + context.getString(R.string.error_add_meta_data)
                    )
                    ""
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(
                    "CoreService",
                    "AppsOnAirCore: " + context.getString(R.string.error_something_wrong)
                )
                ""
            }
        }

        @JvmStatic
        fun getDeviceInfo(context: Context, additionalInfo: Map<String, Any> = emptyMap()): JSONObject {
            val deviceInfoService = DeviceInfoService.getInstance(context)

            if (additionalInfo.isNotEmpty()){
                return deviceInfoService.getDeviceInfo(additionalInfo)
            }

            return  deviceInfoService.deviceInfo
        }

        /**
         * Persistent per-install device identifier.
         *
         * Synchronous and cheap: unlike [getDeviceInfo], which builds the full payload, this
         * reads only the stored identifier, so callers assembling a payload synchronously can
         * depend on it. Backed by the same value reported as `deviceInfo.deviceId`, so both
         * routes always agree.
         *
         * Persisted in SharedPreferences, so it survives restarts but is cleared on uninstall.
         * iOS keeps the equivalent in the Keychain, where it survives uninstall — do not assume
         * the two platforms have the same lifetime.
         */
        @JvmStatic
        fun getDeviceId(context: Context): String {
            return DeviceInfoService.getInstance(context).deviceId
        }

        /**
         * Device language as an ISO 639-1 code (e.g. "en"). Java's obsolete codes ("iw", "in",
         * "ji") are normalized, so this matches what iOS Core reports for the same language.
         *
         * Synchronous and cheap, like [getDeviceId]. Same value as `deviceInfo.language`.
         */
        @JvmStatic
        fun getLanguage(context: Context): String {
            return DeviceInfoService.getInstance(context).language
        }

        /**
         * Cheap, synchronous device facts, bundled for callers that need several at once —
         * everything the Push and AppRemark SDKs need from Core: `deviceId`, `language`,
         * `locale`, `regionCode`, `apiLevel`, `osVersion`, `platform`, `timezone`,
         * `deviceModel`, `manufacturer`, `appVersion`, `buildVersionNumber`, `themeMode`,
         * `fontScale`, `isSimulator`, `firstInstallTime` and `installVendor`.
         *
         * Unlike [getDeviceInfo], nothing here touches storage, memory, battery or the
         * network — the only non-trivial reads are `firstInstallTime` and `installVendor`,
         * one PackageManager lookup each — so it is safe to call while
         * assembling a payload synchronously — which iOS Core's equivalent requires, since
         * its `getDeviceInfo` is async. Individual fields are also available via
         * [getDeviceId] and [getLanguage] for single-field reads.
         *
         * Values come from the same properties [getDeviceInfo] reports, so the two routes
         * can never disagree. Intended for the Push and AppRemark SDKs.
         */
        @JvmStatic
        fun getDeviceMetadata(context: Context): JSONObject {
            return DeviceInfoService.getInstance(context).getDeviceMetadata()
        }
    }
}