// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

/** Hardware integration harness; pairing files exist only in the debug app cache. */
class NfcSessionInstrumentation:Instrumentation() {
    private var host=false
    private var loopback=false
    private var codePairing=false
    private var cancelAfterPair=false
    private var holdResult=false
    private var session:NfcTestSession?=null
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);loopback=arguments?.getString("loopback")=="true";codePairing=arguments?.getString("pairing")=="code";host=arguments?.getString("role")=="master";cancelAfterPair=arguments?.getString("cancelAfterPair")=="true";holdResult=arguments?.getString("holdResult")=="true";start()}
    override fun onStart(){
        val result=Bundle()
        val qrFile=File(targetContext.cacheDir,"nfc-test-pair.json")
        try {
            if(loopback){
                val proof=LicenseKey.prove(targetContext,NativeCore.bindingChallenge())
                NativeCore.initialize(targetContext,proof.publicKey,proof.signature)
                TrialActivation.activate();TrialActivation.activate(terminal=true)
                val metadata=NfcTestMetadata.collect(targetContext)
                val clean=NfcTestMetadata.clean(org.json.JSONObject(metadata.toString()).put("token","must-not-export").put("serial","must-not-export"))
                check(!clean.has("token")&&!clean.has("serial"));check(clean.getString("apk_sha256").matches(Regex("[a-f0-9]{64}")))
                check(runCatching{NfcTestMetadata.clean(org.json.JSONObject(metadata.toString()).put("model","x".repeat(513)))}.isFailure)
                val evidence=org.json.JSONArray()
                val profiles=File(targetContext.filesDir,"profile-drafts.json")
                val before=if(profiles.isFile)profiles.readBytes() else null
                for(kind in listOf("epassport","eid")){
                    try{val material=NfcTestNative.prepareCard(kind)
                        check(material.getString("password").matches(Regex("[a-f0-9]{128}")))
                        var apdus=0
                        val verified=NfcTestNative.inspectCard(material,NfcDocumentExchange{apdus++;NfcTestNative.transmit(it)});check(verified.getString("status")=="pass")
                        val reader=org.json.JSONObject().put("radio",true).put("control",true).put("select",true).put("status",0x9000).put("error","none").put("duration_ms",1).put("apdus",org.json.JSONArray()).put("full",verified)
                        val bounded=NfcTestSession.cleanReader(reader).getJSONObject("full")
                        check(bounded.getJSONArray("data_groups").toString()==verified.getJSONArray("data_groups").toString())
                        check(runCatching{NfcTestSession.cleanReader(org.json.JSONObject(reader.toString()).put("status",65536))}.isFailure)
                        evidence.put(org.json.JSONObject().put("protocol",bounded).put("apdu_count",apdus))}
                    catch(error:Exception){throw IllegalStateException("$kind: ${error.message}",error)}
                    finally{NfcTestNative.close()}
                }
                check(if(before==null)!profiles.exists() else before.contentEquals(profiles.readBytes())){"Existing profiles changed"}
                runOnMainSync{
                    NfcTestCardService.installDocument()
                    val service=NfcTestCardService()
                    service.processCommandApdu(NfcTestProtocol.SELECT,null)
                    service.onDeactivated(0)
                }
                // Drain the native worker with an invalid request; no test card is created.
                check(runCatching{NfcTestCardService.prepare("invalid")}.isFailure)
                var dropped:org.json.JSONObject?=null
                runOnMainSync{dropped=org.json.JSONObject(NfcTestCardService.snapshot()).getJSONArray("apdus").getJSONObject(0);NfcTestCardService.clear()}
                check(requireNotNull(dropped).getBoolean("discarded")&&requireNotNull(dropped).getLong("dispatch_ms")==-1L){"Response survived RF deactivation"}
                File(targetContext.cacheDir,"nfc-test-loopback-report.json").writeText(org.json.JSONObject().put("created_at",java.time.Instant.now().toString()).put("physical_nfc",false).put("hce_deactivation_guard",true).put("sessions",evidence).put("metadata",clean).toString(2))
                result.putString("stream","Licensed ePassport and eID loopback protocol sessions passed; physical NFC not claimed")
                finish(Activity.RESULT_OK,result);return
            }
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
            val proof=LicenseKey.prove(targetContext,NativeCore.bindingChallenge())
            NativeCore.initialize(targetContext,proof.publicKey,proof.signature)
            runOnMainSync{
                val current=NfcTestSession(activity);session=current
                activity.setContent{MaterialTheme(typography=PhoenixTypography,colorScheme=lightColorScheme(primary=Blue,background=Page,surface=Page,onSurface=Navy)){
                    Box(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=20.dp).padding(top=8.dp,bottom=16.dp).semantics{testTagsAsResourceId=true}){NfcTestScreen(onExit={current.disconnect()},session=current)}
                }}
            }
            val current=requireNotNull(session)
            runBlocking {
                if(host){
                    runOnMainSync{current.create()}
                    val pairing=withTimeout(30000){current.ui.first{(if(codePairing)it.pairCode.isNotEmpty() else it.qr.isNotEmpty())||it.phase=="result"}}
                    val value=if(codePairing)pairing.pairCode else pairing.qr
                    check(value.isNotEmpty()){pairing.error?:"Pairing creation failed"}
                    qrFile.writeText(value)
                }else{
                    check(qrFile.isFile&&qrFile.length() in 1..2048)
                    val code=qrFile.readText();qrFile.delete()
                    runOnMainSync{if(codePairing)current.joinCode(code)else current.join(code)}
                }
                val ready=withTimeout(45000){current.ui.first{it.phase=="ready"||it.phase=="result"}}
                qrFile.delete();check(ready.connected){ready.error?:"Pairing failed"}
                if(host) {
                    if(cancelAfterPair){runOnMainSync{current.disconnect()};check(!current.ui.value.connected);result.putString("stream","Temporary pairing and cancellation passed")}
                    else {
                        runOnMainSync{current.start()}
                        val finished=withTimeout(300000){current.ui.first{it.phase=="result"}}
                        File(targetContext.cacheDir,"nfc-test-hardware-report.json").writeText(requireNotNull(finished.report))
                        check(finished.verdict=="not_observed"){"Hardware verdict: ${finished.verdict}; report saved"}
                        val rows=org.json.JSONObject(requireNotNull(finished.report)).getJSONArray("directions")
                        check(rows.length()==6&&finished.connected)
                        for(index in 0 until 6){check(rows.getJSONObject(index).getInt("direction")==index/3);check(rows.getJSONObject(index).getString("kind")==listOf("epassport","eid","samsung")[index%3])}
                        if(holdResult)withTimeout(300000){current.ui.first{it.phase=="ended"}}
                        result.putString("stream","Both NFC transport directions passed; sanitized combined report saved")
                    }
                }else{
                    withTimeout(600000){current.ui.first{if(holdResult)it.phase=="ended" else it.phase in listOf("result","ended")}}
                    result.putString("stream","Paired peer completed the hardware session")
                }
            }
            finish(Activity.RESULT_OK,result)
        }catch(error:Exception){result.putString("stream","NFC hardware integration failed: ${error.message}");finish(Activity.RESULT_CANCELED,result)}
        finally{qrFile.delete();runOnMainSync{session?.close()}}
    }
}
