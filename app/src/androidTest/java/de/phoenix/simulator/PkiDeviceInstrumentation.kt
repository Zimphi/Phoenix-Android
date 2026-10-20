package de.phoenix.simulator

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import java.security.KeyStore
import java.security.Signature
import java.util.UUID

/** Hardware checks use an isolated preference namespace and disposable keys. */
class PkiDeviceInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        val result=Bundle()
        try { result.putString("stream",verifyPkiDevice(targetContext));finish(Activity.RESULT_OK,result) }
        catch(error:Throwable){result.putString("stream","FAILED: ${error.javaClass.simpleName}: ${error.message}");finish(Activity.RESULT_CANCELED,result)}
    }
}

internal fun verifyPkiDevice(context:Context):String {
        val namespace="pki-device-check-${UUID.randomUUID()}"
        val isolated=object:ContextWrapper(context){
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(namespace,mode)
        }
        val preferences=isolated.getSharedPreferences("test",Context.MODE_PRIVATE)
        val keys=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        var alias:String?=null
    try {
            val device=PkiDevice(isolated)
            val create=PkiDevice::class.java.getDeclaredMethod("createKey").apply{isAccessible=true}
            alias=create.invoke(device) as String
            val key=keys.getKey(alias,null) as java.security.PrivateKey
            check(key.encoded==null){"Private key must not be exportable"}
            val message="synthetic-device-proof".toByteArray()
            val signature=Signature.getInstance("SHA256withECDSA").run{initSign(key);update(message);sign()}
            check(Signature.getInstance("SHA256withECDSA").run{
                initVerify(keys.getCertificate(alias).publicKey);update(message);verify(signature)
            })
            check(preferences.edit().putString("id",UUID.randomUUID().toString()).putString("alias",alias).commit())
            check(device.connected())
            check(runCatching{device.enroll("invalid")}.exceptionOrNull()?.message?.contains("zuerst trennen")==true)
            check(keys.containsAlias(alias)&&device.connected())
            preferences.edit().remove("id").remove("alias").putString("pending_alias",alias)
                .putString("pending_code","different-code-hash").commit()
            check(runCatching{device.enroll("phoenix://device?server=https%3A%2F%2Fpreview.epassport-simulator.de&environment=preview#code=${"A".repeat(43)}")}
                .exceptionOrNull()?.message?.contains("Registrierung ausstehend")==true)
            check(device.hasLocalKey()&&keys.containsAlias(alias))
            check(device.forget().contains("Web-Einstellungen"))
            check(!device.hasLocalKey()&&!keys.containsAlias(alias))
        return "Hardware-backed signing without user authentication, non-exportability, replacement guards and local recovery passed."
    }finally{
        alias?.let{keys.deleteEntry(it)}
        preferences.edit().clear().commit()
    }
}
