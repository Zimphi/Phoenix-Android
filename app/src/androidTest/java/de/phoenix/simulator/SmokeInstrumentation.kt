package de.phoenix.simulator

import android.app.Instrumentation
import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject

/** Device checks use their own draft store and never reset existing app data. */
class SmokeInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        val result=Bundle()
        try{
            val directory=File(targetContext.cacheDir,"synthetic-check-${java.util.UUID.randomUUID()}").apply{check(mkdirs())}
            val isolated=object:ContextWrapper(targetContext){override fun getFilesDir()=directory}
            val store=ProfileStore(isolated)
            check(store.profiles.size==2)
            val profile=store.profiles.first();val original=profile.name
            profile.name="Gespeichertes Testprofil";store.save()
            check(ProfileStore(isolated).profiles.first().name=="Gespeichertes Testprofil")
            val invalid=JSONObject(profile.json().toString()).put("id","../escape")
            check(runCatching{Profile.parse(invalid)}.isFailure)
            check(runCatching{store.importDraft(profile.json().toString())}.isFailure)
            check(ProfileStore(isolated).profiles.size==2)
            profile.name=original;store.save()
            val challenge=ByteArray(48).also{java.security.SecureRandom().nextBytes(it)}
            val proof=LicenseKey.prove(targetContext,challenge)
            val publicKey=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(proof.publicKey))
            check(Signature.getInstance("SHA256withECDSA").run{initVerify(publicKey);update(challenge);verify(proof.signature)})
            check(proof.publicKey.contentEquals(LicenseKey.prove(targetContext,challenge).publicKey))
            result.putBoolean("hardware_key_verified",true)
            result.putBoolean("strongbox",proof.strongBox)
            check(NativeCore.available){"Native library not bundled"}
            val binding=LicenseKey.prove(targetContext,NativeCore.bindingChallenge())
            NativeCore.initialize(targetContext,binding.publicKey,binding.signature)
            val inventory=NativeCore.call(JSONObject().put("op","inventory"))
            check(inventory.has("eid")&&inventory.has("epassport"))
            val apdu=runCatching{NativeCore.call(JSONObject().put("op","apdu").put("command","00a40400"))}.getOrNull()
            // With no card selected the host may return a card-status rejection, never fabricated success.
            check(apdu==null||apdu.optString("response")!="9000")
            result.putString("stream","Draft persistence, import validation, hardware key proof, native initialization and both-core inventory passed.")
            finish(Activity.RESULT_OK,result)
        }catch(error:Throwable){result.putString("stream","FAILED at ${error.stackTrace.firstOrNull()}: ${error.javaClass.simpleName}: ${error.message}");finish(Activity.RESULT_CANCELED,result)}
    }
}
