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
    private var pki=false
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);activation=arguments?.getString("activation")=="true";pki=arguments?.getString("pki")=="true";importDraft=arguments?.getString("importDraft");start()}
    override fun onStart(){
        val result=Bundle()
        try{
            if(pki){result.putString("stream",verifyPkiDevice(targetContext));finish(Activity.RESULT_OK,result);return}
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
            check(store.profiles.size==51)
            check(store.profiles.count{it.id=="eid-persosim03"}==1)
            check(store.profiles.count{it.signedFixture}==16)
            check(store.profiles.count{it.passportCatalog}==33)
            check(store.profiles.count{it.passportCatalog&&it.simulatable}==8)
            check(store.profiles.none{it.id.contains("gt",ignoreCase=true)})
            val settings=JSONObject(store.profiles.first{it.kind=="eid"}.json().toString())
            settings.getJSONObject("settings").put("pin_retries",2).put("puk_uses",7).put("transport_pin",true).put("pin","12345").put("eid_active",false)
            check(Profile.parse(settings).settings.getInt("pin_retries")==2)
            check(Profile.parse(settings).settings.getBoolean("transport_pin"))
            check(!Profile.parse(settings).settings.getBoolean("eid_active"))
            settings.getJSONObject("settings").put("pin","123456")
            check(runCatching{Profile.parse(settings)}.isFailure)
            settings.getJSONObject("settings").put("pin","12345").put("puk_uses",11)
            check(runCatching{Profile.parse(settings)}.isFailure)
            val profile=store.profiles.first();val original=profile.name
            profile.name="Gespeichertes Testprofil";store.save()
            check(ProfileStore(isolated).profiles.first().name=="Gespeichertes Testprofil")
            val invalid=JSONObject(profile.json().toString()).put("id","../escape")
            check(runCatching{Profile.parse(invalid)}.isFailure)
            check(runCatching{store.importDraft(profile.json().toString())}.isFailure)
            check(ProfileStore(isolated).profiles.size==51)
            profile.name=original;store.save()
            val migration=File(directory,"migration").apply{check(mkdirs())}
            val migrationContext=object:ContextWrapper(targetContext){override fun getFilesDir()=migration}
            val canonical=NativeCore.call(JSONObject().put("op","passport_catalog")).getJSONArray("profiles").getJSONObject(0)
            val conflicting=Profile.parse(canonical).apply{name="Eigener Namenskonflikt";groups=JSONObject(groups.toString()).put("DG11","synthetic-own-data")}
            File(migration,"profile-drafts.json").writeText(org.json.JSONArray().put(conflicting.json()).toString())
            val migrated=ProfileStore(migrationContext)
            val preserved=migrated.profiles.single{it.name=="Eigener Namenskonflikt"}
            check(preserved.id.startsWith("own-")&&!preserved.passportCatalog&&preserved.groups.getString("DG11")=="synthetic-own-data")
            check(migrated.profiles.count{it.passportCatalog}==33&&migrated.ownCount()==1)
            check(ProfileStore(migrationContext).profiles.single{it.name=="Eigener Namenskonflikt"}.id==preserved.id)
            val crowded=File(directory,"crowded").apply{check(mkdirs())}
            val crowdedContext=object:ContextWrapper(targetContext){override fun getFilesDir()=crowded}
            val ownDrafts=org.json.JSONArray()
            repeat(100){index->ownDrafts.put(Profile.defaults().getJSONObject(1).put("id","old-$index"))}
            File(crowded,"profile-drafts.json").writeText(ownDrafts.toString())
            val full=ProfileStore(crowdedContext)
            check(full.ownCount()==100&&full.profiles.count{it.passportCatalog}==33&&full.profiles.size==149)
            check(runCatching{full.importDraft(Profile.defaults().getJSONObject(1).put("id","over-limit").toString())}.isFailure)
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
