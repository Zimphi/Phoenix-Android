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

/** Smoke checks use an isolated store; explicit imports preserve existing drafts. */
class SmokeInstrumentation:Instrumentation() {
    private var activation=false
    private var importDraft:String?=null
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);activation=arguments?.getString("activation")=="true";importDraft=arguments?.getString("importDraft");start()}
    override fun onStart(){
        val result=Bundle()
        try{
            importDraft?.let{path->
                val source=File(path).canonicalFile
                check(source.parentFile==targetContext.cacheDir.canonicalFile)
                val imported=ProfileStore(targetContext).importDraft(source.inputStream().use{ProfileStore.read(it)})
                result.putString("stream","Imported separate synthetic test draft: ${imported.name}")
                finish(Activity.RESULT_OK,result);return
            }
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
            CardService.foreground(true)
            val stale=CardService.beginStart();CardService.deactivate()
            check(!CardService.activate(stale)){"Stopped start must remain disarmed"}
            val paused=CardService.beginStart();CardService.foreground(false)
            check(!CardService.activate(paused)){"Background start must remain disarmed"}
            if(activation) {
                TrialActivation.activate()
                val defaults=Profile.defaults()
                for(index in 0 until defaults.length()) {
                    val draft=defaults.getJSONObject(index)
                    NativeCore.call(JSONObject().put("op","mount_draft").put("draft",draft))
                    val command=if(draft.getString("kind")=="epassport")"00a4040c07a0000002471001" else "00a4040c09e80704007f00070302"
                    val response=NativeCore.call(JSONObject().put("op","apdu").put("command",command)).getString("response")
                    check(response.endsWith("9000")){"Document selection failed"}
                    NativeCore.call(JSONObject().put("op","close"))
                }
                result.putBoolean("activation_and_document_selection",true)
            }
            result.putString("stream",if(activation)"Background trial activation, ePassport/eID personalization and SELECT APDUs passed; stale and paused starts remain disarmed." else "Draft persistence, import validation, hardware key proof, native initialization, both-core inventory and start cancellation passed.")
            finish(Activity.RESULT_OK,result)
        }catch(error:Throwable){result.putString("stream","FAILED at ${error.stackTrace.firstOrNull()}: ${error.javaClass.simpleName}: ${error.message}");finish(Activity.RESULT_CANCELED,result)}
    }
}
