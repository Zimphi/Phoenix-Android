// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.nfc.NfcAdapter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Diagnostic fields only: never device identifiers, document data, SSID or credentials. */
internal object NfcTestMetadata {
    private val textFields=listOf("manufacturer","brand","model","device","product","board","hardware","firmware","android",
        "security_patch","base_os","build_id","build_display","build_fingerprint","build_type","build_tags","bootloader",
        "kernel","architecture","app_version","apk_sha256","locale","timezone","collected_at","library_sha256")
    private val numericFields=listOf("sdk","app_version_code","target_sdk","display_width_px","display_height_px","display_density_dpi","battery_percent")
    private val booleanFields=listOf("nfc_available","nfc_enabled","hce_supported","hcef_supported","charging","power_save","device_secure")
    fun collect(context:Context):JSONObject {
        val info=context.packageManager.getPackageInfo(context.packageName,0)
        val display=context.resources.displayMetrics
        val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val nfc=NfcAdapter.getDefaultAdapter(context)
        val value=JSONObject().put("manufacturer",Build.MANUFACTURER).put("brand",Build.BRAND).put("model",Build.MODEL)
            .put("device",Build.DEVICE).put("product",Build.PRODUCT).put("board",Build.BOARD).put("hardware",Build.HARDWARE)
            .put("firmware",Build.VERSION.INCREMENTAL).put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT)
            .put("security_patch",Build.VERSION.SECURITY_PATCH).put("base_os",Build.VERSION.BASE_OS)
            .put("build_id",Build.ID).put("build_display",Build.DISPLAY).put("build_fingerprint",Build.FINGERPRINT)
            .put("build_type",Build.TYPE).put("build_tags",Build.TAGS).put("bootloader",Build.BOOTLOADER)
            .put("kernel",System.getProperty("os.version","unavailable")).put("architecture",System.getProperty("os.arch","unavailable"))
            .put("app_version",info.versionName?:"unavailable").put("app_version_code",if(Build.VERSION.SDK_INT>=28)info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong())
            .put("target_sdk",context.applicationInfo.targetSdkVersion).put("display_width_px",display.widthPixels)
            .put("display_height_px",display.heightPixels).put("display_density_dpi",display.densityDpi)
            .put("nfc_available",nfc!=null).put("nfc_enabled",nfc?.isEnabled==true)
            .put("hce_supported",context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION))
            .put("hcef_supported",context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF))
            .put("battery_percent",battery?.let{val level=it.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val scale=it.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(level>=0&&scale>0)level*100/scale else -1}?:-1)
            .put("charging",(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?:0)!=0)
            .put("power_save",(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode)
            .put("device_secure",(context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure)
            .put("locale",Locale.getDefault().toLanguageTag()).put("timezone",ZoneId.systemDefault().id).put("collected_at",Instant.now().toString())
            .put("abis",JSONArray(Build.SUPPORTED_ABIS.toList()))
        val libraries=JSONArray()
        for(path in listOf("/system/lib64/libnfc_nxpsn_jni.so","/system/lib64/libnfc-nci.so","/system/lib64/libnfc_nci_jni.so")){
            val file=File(path)
            val hash=runCatching{require(file.length() in 1..16777216);val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}};digest.digest().joinToString(""){"%02x".format(it)}}.getOrDefault("unavailable")
            libraries.put(JSONObject().put("path",path).put("sha256",hash).put("version","not exposed by Android").put("bytes",if(hash=="unavailable")0 else file.length()))
            if(file.name=="libnfc_nxpsn_jni.so")value.put("library_sha256",hash)
        }
        value.put("system_libraries",libraries)
        value.put("apk_sha256",sha256(File(context.applicationInfo.sourceDir)))
        value.put("app_libraries",JSONArray(context.assets.open("nfc-test-libraries.json").bufferedReader().use{it.readText()}))
        value.put("components",JSONObject(context.assets.open("components.lock.json").bufferedReader().use{it.readText()}))
        value.put("protocol","TLSv1.2 · QR certificate pin or authenticated code")
        return value
    }
    private fun sha256(file:File):String=runCatching{
        require(file.length() in 1..268435456)
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use{input->val bytes=ByteArray(8192);while(true){val size=input.read(bytes);if(size<0)break;digest.update(bytes,0,size)}}
        digest.digest().joinToString(""){"%02x".format(it)}
    }.getOrDefault("unavailable")
    fun clean(input:JSONObject):JSONObject=JSONObject().apply {
        for(key in textFields){val text=input.getString(key);require(text.length<=512&&text.none{it.isISOControl()});put(key,text)}
        for(key in numericFields){val number=input.getLong(key);require(number in -1..10000000000L);put(key,number)}
        for(key in booleanFields)put(key,input.getBoolean(key))
        val abis=input.getJSONArray("abis");require(abis.length()<=8)
        put("abis",JSONArray().apply{for(i in 0 until abis.length()){val abi=abis.getString(i);require(abi.matches(Regex("[a-zA-Z0-9_-]{1,40}")));put(abi)}})
        val libraries=input.getJSONArray("system_libraries");require(libraries.length()<=8)
        put("system_libraries",JSONArray().apply{for(i in 0 until libraries.length()){
            val row=libraries.getJSONObject(i);val path=row.getString("path");require(path.matches(Regex("/system/lib64/[a-zA-Z0-9_.-]{1,80}")))
            val hash=row.getString("sha256");require(hash=="unavailable"||hash.matches(Regex("[0-9a-f]{64}")))
            val bytes=row.getLong("bytes");require(bytes in 0..16777216)
            put(JSONObject().put("path",path).put("sha256",hash).put("bytes",bytes).put("version","not exposed by Android"))
        }})
        val components=input.getJSONObject("components");require(components.length()<=16)
        put("components",JSONObject().apply{for(key in components.keys()){
            require(key.matches(Regex("[a-zA-Z0-9_-]{1,40}")))
            val item=components.getJSONObject(key);val clean=JSONObject()
            for(field in listOf("version","commit")){if(item.has(field)){val text=item.getString(field);require(text.length<=80&&text.none{it.isISOControl()});clean.put(field,text)}}
            put(key,clean)
        }})
        val appLibraries=input.getJSONArray("app_libraries");require(appLibraries.length()<=300)
        put("app_libraries",JSONArray().apply{for(i in 0 until appLibraries.length()){
            val row=appLibraries.getJSONObject(i);val clean=JSONObject()
            for(field in listOf("group","name","version")){val text=row.getString(field);require(text.length<=160&&text.none{it.isISOControl()});clean.put(field,text)}
            put(clean)
        }})
        put("protocol","TLSv1.2 · authenticated local session")
    }
}
