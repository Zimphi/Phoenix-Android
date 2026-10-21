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
    private var auditCommunity=false
    private var performance=false
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);activation=arguments?.getString("activation")=="true";pki=arguments?.getString("pki")=="true";auditCommunity=arguments?.getString("auditCommunity")=="true";importDraft=arguments?.getString("importDraft");performance=arguments?.getString("performance")=="true";start()}
    override fun onStart(){
        val result=Bundle()
        try{
            if(performance) {
                DocumentImages.warm(targetContext)
                check(DocumentImages.load(targetContext,R.drawable.document_paper)===DocumentImages.load(targetContext,R.drawable.document_paper))
                check(CountryCodes.load(targetContext)===CountryCodes.load(targetContext))
                val draftProfile=Profile.parse(Profile.defaults().getJSONObject(1))
                val draft=draftProfile.coreDraft();draftProfile.groups.put("DG4","CHANGED")
                check(draft.getJSONObject("groups").getString("DG4")=="MAX")
                val device=PkiDevice(targetContext)
                val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
                val worker=Thread{synchronized(device){entered.countDown();release.await(3,java.util.concurrent.TimeUnit.SECONDS)}}
                val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
                worker.start();check(entered.await(1,java.util.concurrent.TimeUnit.SECONDS))
                try {
                    pool.submit(java.util.concurrent.Callable{device.identity();device.userId();device.environment();device.connected();device.hasLocalKey();device.verifiedConnection()})
                        .get(300,java.util.concurrent.TimeUnit.MILLISECONDS)
                } finally {release.countDown();worker.join();pool.shutdownNow()}
                result.putString("stream","Shared artwork/country data, isolated draft snapshots and nonblocking account snapshots passed.")
                finish(Activity.RESULT_OK,result);return
            }
            if(auditCommunity) {
                val store=ProfileStore(targetContext)
                val profiles=store.profiles.filter{it.kind=="eid"}
                check(profiles.map{it.documentNumber}.toSet().size==profiles.size)
                val checks=org.json.JSONArray()
                for(profile in profiles) {
                    check(profile.name==profile.groupText("DG5").trim())
                    check(store.bySerial(profile.documentNumber)===profile)
                    val check=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",profile.coreDraft())).getJSONObject("community")
                    checks.put(JSONObject().put("id",profile.id).put("serial",profile.documentNumber).put("surname",profile.name).put("community",check))
                }
                result.putString("stream",checks.toString(2));finish(Activity.RESULT_OK,result);return
            }
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
            check(store.profiles.size==26)
            val eidProfiles=store.profiles.filter{it.kind=="eid"}
            check(eidProfiles.map{it.documentNumber}.toSet().size==eidProfiles.size)
            for(profile in eidProfiles) {
                check(profile.name==profile.groupText("DG5").trim())
                check(store.bySerial(profile.documentNumber)===profile)
                if(profile.signedFixture)check(profile.pkiInfrastructure=="bsi")
            }
            val serialSource=eidProfiles.first()
            val duplicate=serialSource.editableCopy().put("document_number",serialSource.documentNumber)
            check(runCatching{store.importDraft(duplicate.toString())}.isFailure)
            val uniqueCopy=store.importDraft(serialSource.editableCopy().toString())
            check(uniqueCopy.documentNumber!=serialSource.documentNumber)
            val serial=uniqueCopy.documentNumber
            check(store.resetPersonalization(uniqueCopy).documentNumber==serial)
            store.profiles.removeIf{it.id==uniqueCopy.id};store.save()

            check(store.profiles.count{it.id=="eid-persosim03"}==1)
            check(store.profiles.count{it.signedFixture}==16)
            check(store.profiles.count{it.passportCatalog}==8)
            check(store.profiles.count{it.passportCatalog&&it.simulatable}==8)
            check(store.profiles.none{it.id.contains("gt",ignoreCase=true)})
            store.profiles.filter{it.passportCatalog}.forEach{passport->
                val description=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",passport.json()))
                check(description.getString("mrz").length==88)
                check(decodePortrait(description.getString("portrait"))!=null)
            }
            store.profiles.filter{it.signedFixture}.forEach{original->
                val before=original.json().toString()
                val edited=store.importDraft(original.editableCopy().toString())
                check(edited.persosimSource==original.id)
                edited.groups.put("DG4","GEAENDERT")
                store.save()
                val reloaded=ProfileStore(isolated).profiles.single{it.id==edited.id}
                val description=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",reloaded.coreDraft()))
                check(description.getJSONObject("groups").getString("DG4")=="GEAENDERT")
                check(original.json().toString()==before&&!reloaded.signedFixture)
                store.profiles.remove(edited);store.save()
            }
            for(kind in listOf("eid","epassport")) {
                val source=store.profiles.first{it.kind==kind}
                val own=store.importDraft(source.editableCopy().toString())
                val original=own.json().toString()
                own.groups.put(if(kind=="eid")"DG4" else "DG1","changed")
                own.settings.put("can","654321");own.portraitSex="M";own.documentNumber="TEST2";own.pkiInfrastructure="phoenix"
                store.save()
                val reopened=ProfileStore(isolated)
                val restored=reopened.resetPersonalization(reopened.profiles.single{it.id==own.id})
                val data=restored.json();val generation=data.getString("personalization_generation")
                data.remove("personalization_generation")
                val expected=JSONObject(original)
                if(kind=="eid")expected.put("document_number","TEST2")
                check(data.toString()==expected.toString())
                check(generation.isNotEmpty()&&restored.coreDraft().getString("personalization_generation")==generation)
                check(ProfileStore(isolated).profiles.single{it.id==own.id}.personalizationGeneration==generation)
                store.profiles.remove(own);store.save()
            }
            val invalidPki=Profile.defaults().getJSONObject(0).put("pki_infrastructure","bsi")
            check(runCatching{Profile.parse(invalidPki)}.isFailure)
            invalidPki.put("pki_infrastructure","own")
            check(runCatching{Profile.parse(invalidPki)}.isFailure)
            check(!PkiDevice(targetContext).verifiedConnection())
            val catalogue=store.profiles.first{it.passportCatalog}
            val catalogueBefore=catalogue.json().toString()
            val copy=Profile.parse(catalogue.editableCopy())
            check(copy.id!=catalogue.id&&copy.settings.getString("pin")=="123456")
            copy.groups.put("DG1","changed copy")
            check(catalogue.json().toString()==catalogueBefore)
            val editable=Profile.defaults().getJSONObject(0)
            val person=JSONObject(editable.getJSONObject("groups").getString("DG1")).put("surname","Müller").put("given_names","Lena")
            editable.getJSONObject("groups").put("DG1",person.toString())
            val mrz=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",editable)).getString("mrz")
            check(mrz.length==88&&mrz.contains("MUELLER<<LENA"))
            person.put("date_of_birth","invalid")
            editable.getJSONObject("groups").put("DG1",person.toString())
            check(runCatching{NativeCore.call(JSONObject().put("op","describe_draft").put("draft",editable))}.isFailure)
            check(runCatching{decodePortrait("not-an-image")}.isFailure)
            check(runCatching{decodePortrait("A".repeat(2*1024*1024+1))}.isFailure)

            val td3="P<UTO"+"ERIKSSON<<ANNA<MARIA".padEnd(39,'<')+"D231458907UTO7408122F1204159".padEnd(44,'<')
            val td1=td1FromTd3(td3,"I<")
            check(td1=="I<UTOD231458907<<<<<<<<<<<<<<<\n7408122F1204159UTO<<<<<<<<<<<6\nERIKSSON<<ANNA<MARIA<<<<<<<<<<")
            check(td1.lines().all{it.length==30})
            check(runCatching{td1FromTd3("invalid","ID")}.isFailure)
            val numbered=Profile.parse(Profile.defaults().getJSONObject(1))
            check(numbered.documentNumber.matches(Regex("[A-Z0-9]{9}")))
            numbered.documentNumber="T00000001"
            check(Profile.parse(numbered.json()).documentNumber=="T00000001")
            check(!numbered.coreDraft().has("document_number"))
            check(eidBackStart("UB")=="DG9"&&eidBackStart("AR")=="DG17")
            check(dateValue("29","2","2024",false)=="20240229")
            check(dateValue("29","2","2023",false)==null)
            check(dateValue("31","4","2026",false)==null)
            check(dateValue("12","8","1983",true)=="830812")
            check(compactDate("830812",true)?.year==1983)
            check(compactDate("20310801",false)?.dayOfMonth==1)
            check(residenceDocument("AR")&&!residenceDocument("ID"))
            val typeDraft=Profile.parse(Profile.defaults().getJSONObject(1))
            typeDraft.groups.put("DG19","retained residence data")
            check(!typeDraft.coreDraft().getJSONObject("groups").has("DG19"))
            check(typeDraft.json().getJSONObject("groups").getString("DG19")=="retained residence data")
            typeDraft.groups.put("DG1","AR")
            check(typeDraft.coreDraft().getJSONObject("groups").has("DG19"))
            val addressRoot=JSONObject("""{"Places":[{"Structured":{"city":"BERLIN","country":"D"}},{"Freetext":"Zweite Anschrift"}]}""")
            structuredPlaces(addressRoot).single().put("city","KÖLN")
            check(addressRoot.getJSONArray("Places").getJSONObject(1).getString("Freetext")=="Zweite Anschrift")
            val freePlace="""{"GeneralPlace":{"Freetext":"BERLIN"}}"""
            check(groupValue("eid","DG9",freePlace)=="BERLIN")
            check(editableGroupText(freePlace)=="BERLIN")
            check(editableGroupText("""{"Text":"ERIKA"}""")=="ERIKA")
            val structuredPlace="""{"GeneralPlace":{"Structured":{"city":"BERLIN","country":"D"}}}"""
            check(editableGroupText(structuredPlace)==structuredPlace)
            check(!groupValue("eid","DG9",structuredPlace).contains("GeneralPlace"))
            check(readableValue(JSONObject("""{"Bytes":[1,2,3]}"""))=="Binärdaten · 3 Bytes")
            val settings=JSONObject(store.profiles.first{it.kind=="eid"}.json().toString())
            val appearance=Profile.parse(settings)
            check(portraitSex(appearance,JSONObject().put("DG11","F"))=="F")
            check(portraitSex(appearance,JSONObject())=="M")
            appearance.groups=JSONObject(appearance.groups.toString()).put("DG11","M")
            check(portraitSex(appearance,appearance.groups)=="M")
            appearance.portraitSex="F"
            check(portraitSex(appearance,appearance.groups)=="F")
            check(Profile.parse(appearance.json()).portraitSex=="F")
            check(!appearance.coreDraft().has("portrait_sex"))
            check(runCatching{Profile.parse(appearance.json().put("portrait_sex","invalid"))}.isFailure)
            val female=Profile.parse(Profile.defaults().getJSONObject(0))
            check(portraitSex(female,female.groups)=="F")
            val maleGroups=JSONObject().put("DG1",JSONObject(female.groups.getString("DG1")).put("sex","M").toString())
            check(portraitSex(female,maleGroups)=="M")
            check(portraitSex(female,JSONObject().put("DG1","{}"))=="")
            for(preset in listOf("picture-1","picture-2","picture-3","shadow-male","shadow-female")) {
                val draft=Profile.defaults().getJSONObject(0)
                draft.getJSONObject("groups").put("DG2","synthetic:$preset")
                check(decodePortrait(NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft)).getString("portrait"))!=null)
            }
            val eid=store.profiles.first{it.kind=="eid"};eid.portraitSex="F";store.save()
            check(ProfileStore(isolated).profiles.single{it.id==eid.id}.portraitSex=="F")
            check(Profile.parse(JSONObject(ProfileStore.exportDraft(eid).toString(Charsets.UTF_8))).portraitSex=="F")
            check(NativeCore.call(JSONObject().put("op","describe_draft").put("draft",eid.coreDraft())).has("groups"))
            for(type in listOf("ID","AR","AS","AF","UB")) {
                val draft=eid.coreDraft()
                draft.getJSONObject("groups").put("DG1",type).put("DG4","GEAENDERT").put("DG11","M").put("DG15","20210101")
                val result=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft))
                check(result.getJSONObject("groups").getString("DG4")=="GEAENDERT")
                check(result.getJSONObject("groups").getString("DG15")=="20210101")
                check(result.getJSONObject("groups").has("DG17"))
                val active=result.getJSONArray("active")
                check((0 until active.length()).none{active.getInt(it) in listOf(11,15,21,22)})
                if(type in listOf("AR","AS","AF"))check((0 until active.length()).none{active.getInt(it)==6})
            }
            for(resource in listOf(R.drawable.portrait_male,R.drawable.portrait_female)) {
                val bitmap=android.graphics.BitmapFactory.decodeResource(targetContext.resources,resource)
                check(bitmap.hasAlpha()&&bitmap.width>0&&bitmap.height>bitmap.width);bitmap.recycle()
            }
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
            val exported=ProfileStore.exportDraft(profile).toString(Charsets.UTF_8)
            val roundtrip=Profile.parse(JSONObject(exported))
            check(roundtrip.json().toString()==profile.json().toString())
            check(ProfileStore.read(exported.byteInputStream())==exported)
            val oversized=Profile.parse(profile.json())
            oversized.groups=JSONObject().apply{repeat(22){put("DG${it+1}","x".repeat(65536))}}
            check(runCatching{ProfileStore.exportDraft(oversized)}.isFailure)
            val invalid=JSONObject(profile.json().toString()).put("id","../escape")
            check(runCatching{Profile.parse(invalid)}.isFailure)
            check(runCatching{store.importDraft(profile.json().toString())}.isFailure)
            check(ProfileStore(isolated).profiles.size==26)
            profile.name=original;store.save()
            val migration=File(directory,"migration").apply{check(mkdirs())}
            val migrationContext=object:ContextWrapper(targetContext){override fun getFilesDir()=migration}
            val canonical=NativeCore.call(JSONObject().put("op","passport_catalog")).getJSONArray("profiles").getJSONObject(0)
            val conflicting=Profile.parse(canonical).apply{name="Eigener Namenskonflikt";groups=JSONObject(groups.toString()).put("DG11","synthetic-own-data")}
            File(migration,"profile-drafts.json").writeText(org.json.JSONArray().put(conflicting.json()).toString())
            val migrated=ProfileStore(migrationContext)
            val preserved=migrated.profiles.single{it.name=="Eigener Namenskonflikt"}
            check(preserved.id.startsWith("own-")&&!preserved.passportCatalog&&preserved.groups.getString("DG11")=="synthetic-own-data")
            check(migrated.profiles.count{it.passportCatalog}==8&&migrated.ownCount()==1)
            check(ProfileStore(migrationContext).profiles.single{it.name=="Eigener Namenskonflikt"}.id==preserved.id)
            val retired=NativeCore.call(JSONObject().put("op","passport_catalog")).getJSONArray("retired_ids").getString(0)
            val legacy=File(directory,"legacy").apply{check(mkdirs())}
            val legacyContext=object:ContextWrapper(targetContext){override fun getFilesDir()=legacy}
            val old=Profile.defaults().getJSONObject(0).put("id",retired).put("name","Retired synthetic fixture")
            val own=Profile.defaults().getJSONObject(0).put("id","own-retained").put("name","My passport")
            File(legacy,"profile-drafts.json").writeText(org.json.JSONArray().put(old).put(own).toString())
            val upgraded=ProfileStore(legacyContext)
            check(upgraded.profiles.none{it.id==retired})
            check(upgraded.profiles.any{it.id=="own-retained"&&it.name=="My passport"})
            val archived=org.json.JSONArray(File(legacy,"retired-passport-drafts.json").readText())
            check(archived.length()==1&&archived.getJSONObject(0).getString("id")==retired)
            check(ProfileStore(legacyContext).profiles.size==upgraded.profiles.size)
            val crowded=File(directory,"crowded").apply{check(mkdirs())}
            val crowdedContext=object:ContextWrapper(targetContext){override fun getFilesDir()=crowded}
            val ownDrafts=org.json.JSONArray()
            repeat(100){index->ownDrafts.put(Profile.defaults().getJSONObject(1).put("id","old-$index"))}
            File(crowded,"profile-drafts.json").writeText(ownDrafts.toString())
            val full=ProfileStore(crowdedContext)
            check(full.ownCount()==100&&full.profiles.count{it.passportCatalog}==8&&full.profiles.size==124)
            check(runCatching{full.importDraft(Profile.defaults().getJSONObject(1).put("id","over-limit").toString())}.isFailure)
            val countries=CountryCodes.load(targetContext)
            check(countries.single{it.alpha3=="DEU"}.mrz=="D<<")
            check(countries.single{it.icao=="RKS"}.matches("Kosovo"))
            check(countries.single{it.alpha3=="FRA"}.hasCode("FRA"))
            check(countries.none{it.hasCode("")})
            check(passportDate("860315")=="15.03.86")
            check(passportDate("19900101")=="01.01.1990")
            val owner=java.util.UUID.randomUUID().toString()
            val status=JSONObject().put("authenticated",true).put("user_id",owner)
            check(PkiDevice.identityFromStatus(status).getString("user_id")==owner)
            status.put("account",JSONObject().put("user_id",owner).put("display_name","Synthetischer Benutzer").put("username","synthetic-user").put("email","test@example.invalid"))
            check(PkiDevice.identityFromStatus(status).getString("username")=="synthetic-user")
            status.getJSONObject("account").put("user_id",java.util.UUID.randomUUID().toString())
            check(runCatching{PkiDevice.identityFromStatus(status)}.isFailure)
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
                    val draft=Profile.parse(defaults.getJSONObject(index)).coreDraft()
                    NativeCore.call(JSONObject().put("op","mount_draft").put("draft",draft))
                    val command=if(draft.getString("kind")=="epassport")"00a4040c07a0000002471001" else "00a4040c09e80704007f00070302"
                    val response=NativeCore.call(JSONObject().put("op","apdu").put("command",command)).getString("response")
                    check(response.endsWith("9000")){"Document selection failed"}
                    NativeCore.call(JSONObject().put("op","close"))
                }
                val riProfile=Profile.parse(Profile.defaults().getJSONObject(1).put("id","ri-test-"+java.util.UUID.randomUUID()))
                fun riState():JSONObject {
                    NativeCore.call(JSONObject().put("op","mount_draft").put("draft",riProfile.coreDraft()))
                    NativeCore.call(JSONObject().put("op","close"))
                    return NativeCore.call(JSONObject().put("op","editor_state").put("id",riProfile.id))
                }
                val initialRi=riState().getJSONObject("ri")
                riProfile.groups.put("DG18","123456789012")
                check(riState().getJSONObject("ri").getString("ec_public")==initialRi.getString("ec_public"))
                riProfile.groups.put("DG5","ANDERS")
                val changedRi=riState().getJSONObject("ri")
                check(changedRi.getString("ec_public")!=initialRi.getString("ec_public"))
                check(changedRi.getString("dh_public")!=initialRi.getString("dh_public"))
                riProfile.groups=Profile.defaults().getJSONObject(1).getJSONObject("groups")
                check(riState().getJSONObject("ri").getString("ec_public")==initialRi.getString("ec_public"))
                val frozen=NativeCore.call(JSONObject().put("op","ri_update").put("id",riProfile.id).put("frozen",true))
                check(frozen.getJSONObject("ri").getBoolean("frozen"))
                riProfile.groups.put("DG5","ANDERS")
                check(riState().getJSONObject("ri").getString("ec_public")==initialRi.getString("ec_public"))
                val regenerated=NativeCore.call(JSONObject().put("op","ri_update").put("id",riProfile.id).put("frozen",true).put("regenerate",true)).getJSONObject("ri")
                check(regenerated.getString("ec_public")!=initialRi.getString("ec_public"))
                check(riState().getJSONObject("ri").toString()==regenerated.toString())
                val exported=Profile.parse(riProfile.json().put("id","ri-import-"+java.util.UUID.randomUUID()).put("ri",regenerated))
                NativeCore.call(JSONObject().put("op","mount_draft").put("draft",exported.coreDraft()))
                NativeCore.call(JSONObject().put("op","close"))
                val importedRi=NativeCore.call(JSONObject().put("op","editor_state").put("id",exported.id)).getJSONObject("ri")
                check(importedRi.toString()==regenerated.toString())
                result.putBoolean("ri_policy_persistence_and_import",true)
                val fixtures=targetContext.assets.open("persosim-profiles.json").use{org.json.JSONArray(ProfileStore.read(it))}
                val original=Profile.parse(fixtures.getJSONObject(0));original.signedFixture=true
                val bsi=Profile.parse(original.editableCopy());bsi.pkiInfrastructure="profile"
                bsi.settings.put("pin","54321").put("transport_pin",true).put("pin_retries",1).put("puk_uses",0)
                val local=NativeCore.call(JSONObject().put("op","mount_draft").put("draft",bsi.coreDraft()))
                NativeCore.call(JSONObject().put("op","close"))
                val beforePki=NativeCore.call(JSONObject().put("op","editor_state").put("id",bsi.id))
                bsi.pkiInfrastructure="bsi"
                val migrated=NativeCore.call(JSONObject().put("op","mount_draft").put("draft",bsi.coreDraft()))
                check(migrated.getString("profile")!=local.getString("profile"))
                NativeCore.call(JSONObject().put("op","close"))
                val afterPki=NativeCore.call(JSONObject().put("op","editor_state").put("id",bsi.id))
                check(beforePki.getJSONObject("settings").toString()==afterPki.getJSONObject("settings").toString())
                check(beforePki.getJSONObject("ri").toString()==afterPki.getJSONObject("ri").toString())
                val cvca=File(targetContext.filesDir,"native/profiles/eid/"+migrated.getString("profile")+"/eID/EF.CVCA").readBytes()
                check(String(cvca,Charsets.ISO_8859_1).contains("DETESTeID"))
                result.putBoolean("bsi_switch_preserves_pin_counters_and_ri",true)
                NativeCore.call(JSONObject().put("op","mount_draft").put("draft",bsi.coreDraft()))
                check(NativeCore.call(JSONObject().put("op","apdu").put("command","00a4040c09e80704007f00070302")).getString("response").endsWith("9000"))
                bsi.groups.put("DG4","GEAENDERT")
                check(runCatching{NativeCore.call(JSONObject().put("op","mount_draft").put("draft",bsi.coreDraft()))}.isFailure)
                bsi.pkiInfrastructure="phoenix"
                check(runCatching{NativeCore.call(JSONObject().put("op","mount_draft").put("draft",bsi.coreDraft()))}.isFailure)
                NativeCore.call(JSONObject().put("op","close"))
                result.putBoolean("activation_and_document_selection",true)
            }
            result.putString("stream",if(activation)"Background trial activation, ePassport/eID personalization and SELECT APDUs passed; stale and paused starts remain disarmed." else "Draft persistence, import validation, hardware key proof, native initialization, both-core inventory and start cancellation passed.")
            finish(Activity.RESULT_OK,result)
        }catch(error:Throwable){result.putString("stream","FAILED at ${error.stackTrace.firstOrNull{it.className.endsWith("SmokeInstrumentation")}}: ${error.javaClass.simpleName}: ${error.message}");finish(Activity.RESULT_CANCELED,result)}
    }
}
