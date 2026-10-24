package de.phoenix.simulator

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Test APK only: exercises filtering/UI; explicit live mode sends only a synthetic fixture. */
class NfcUploadInstrumentation:Instrumentation(){
    private var failure=false
    private var live=false
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);failure=arguments?.getString("failure")=="true";live=arguments?.getString("live")=="true";start()}
    override fun onStart(){
        val result=Bundle();val done=CompletableDeferred<Unit>();var stage="filtering"
        val file=File(targetContext.cacheDir,"nfc-upload-test-report.json")
        try{
            val report=file.readText();file.delete()
            val valid=NfcUploadReport.clean(report)
            check(valid.getJSONArray("directions").length()==6)
            val secret="synthetic-private-value-must-never-escape"
            val contaminated=JSONObject(report)
            contaminated.getJSONObject("master").put("token",secret)
            contaminated.getJSONArray("directions").getJSONObject(0).getJSONObject("reader").getJSONArray("apdus").getJSONObject(0).put("data",secret)
            check(!NfcUploadReport.clean(contaminated.toString()).toString().contains(secret))
            check(runCatching{NfcUploadReport.clean(JSONObject(report).put("password",secret).toString())}.isFailure)
            check(runCatching{NfcUploadReport.clean(JSONObject(report).put("schema","unknown").toString())}.isFailure)
            if(live){
                // Live acceptance is opt-in and restricted to the pinned synthetic fixture.
                check(NfcUploadCrypto.sha256(report.toByteArray())=="aa283972c885e4860ea49230e98797e40293356a335adb7f89fac3600761f77a")
                runBlocking{withTimeout(90000){
                    val client=NfcUploadClient(targetContext)
                    val receipt=client.send(report){stage=it}
                    stage="owner deletion";client.delete(receipt.id)
                }}
                result.putString("stream","Synthetic live upload and owner deletion passed")
                finish(Activity.RESULT_OK,result);return
            }
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
            runOnMainSync{activity.setContent{
                MaterialTheme(typography=PhoenixTypography,colorScheme=lightColorScheme(primary=Blue,background=Page,surface=Page,onSurface=Navy)){
                    var open by remember{mutableStateOf(false)};var calls by remember{mutableStateOf(0)}
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp).semantics{testTagsAsResourceId=true}){
                        Text("Synthetischer Diagnose-Uploadtest")
                        Text("Bestätigte Sendungen: $calls")
                        Button(onClick={open=true}){Text("Log sicher senden")}
                        Button(onClick={done.complete(Unit)}){Text("Test beenden")}
                    }
                    if(open)NfcUploadDialog(report,send={_,progress->
                        calls++;progress("Testübertragung läuft …");delay(2500)
                        if(failure)error("synthetic secret must not be shown")
                        NfcUploadReceipt("00000000-0000-0000-0000-000000000001",java.time.Instant.now().epochSecond+14*86400,"0".repeat(64))
                    },delete={id->check(id=="00000000-0000-0000-0000-000000000001");delay(300)},onDismiss={open=false})
                }
            }}
            runBlocking{withTimeout(240000){done.await()}}
            result.putString("stream","Upload report filtering and synthetic consent UI passed; no live upload claimed")
            finish(Activity.RESULT_OK,result)
        }catch(error:Exception){result.putString("stream","Upload test failed: $stage (${error.javaClass.simpleName})");finish(Activity.RESULT_CANCELED,result)}
        finally{file.delete()}
    }
}
