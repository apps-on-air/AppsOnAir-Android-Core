# AppsOnAir-Android-Core

## How it works? 

It uses to check internet connectivity. It provides feature to set AppsOnAir Application ID (AppId).

It is a core SDK of AppsOnAir. 

This SDK must be used with other AppsOnAir SDKs. This allows us to set and retrieve the AppsOnAir Application ID.


## How to use?

Add meta-data to the app's AndroidManifest.xml file under the application tag.

>Make sure meta-data name is “appId”.

>Provide your application id in meta-data value.


```sh
</application>
    ...
    <meta-data
        android:name="appId"
        android:value="********-****-****-****-************" />
</application>
```

Add AppsOnAir Core dependency to your gradle.

```sh
dependencies {
   implementation 'com.github.apps-on-air:AppsOnAir-Android-Core:TAG'
}
```

Add below code to setting.gradle.

```sh
dependencyResolutionManagement {
   repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
   repositories {
       google()
       mavenCentral()
       maven {
           url = uri("https://jitpack.io")
       }
   }
}
```

## Example :

To fetch your appId from manifest,

```sh
val appId: String = CoreService.getAppId(this)
```

To get the device information details without additional data.

```sh
Kotlin:

val deviceInfo = CoreService.getDeviceInfo(this) //Without any additional details
```

```sh
Java:

JSONObject deviceInfos = CoreService.getDeviceInfo(this, Collections.emptyMap());
```

To get the device information details with additional data like passing additional version details.
```sh
Kotlin:

val additionalInfo: MutableMap<String, Any> = HashMap()
additionalInfo["remark"] = "0.0.1"
additionalInfo["Sync"] = "0.0.1"
val deviceInfo = CoreService.getDeviceInfo(this,additionalInfo)
```

```sh
Java: 

Map<String, Object> additionalInfo = Collections.singletonMap("appRemarkVersion", BuildConfig.VERSION_NAME);
JSONObject deviceInfoWithAdditionalInfo = CoreService.getDeviceInfo(this, additionalInfo);
```

To check internet connectivity,

```sh
val updateNetworkState = UpdateNetwork { isConnected ->
    Log.d(TAG, "hasNetworkConnection: $isConnected")
}

NetworkService.checkConnectivity(this, updateNetworkState)
```
