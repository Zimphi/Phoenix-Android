// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable private fun rememberNfcSession():NfcTestSession {
    val activity=requireNotNull(LocalActivity.current) as ComponentActivity
    return remember(activity){NfcTestSession(activity)}
}

@Composable internal fun NfcTestScreen(session:NfcTestSession=rememberNfcSession(),onExit:()->Unit) {
    val context=LocalContext.current
    val activity=requireNotNull(LocalActivity.current) as ComponentActivity
    val state by session.ui.collectAsState()
    val scope=rememberCoroutineScope()
    var export by remember{mutableStateOf<String?>(null)}
    var exportStatus by remember{mutableStateOf<String?>(null)}
    var exitConfirm by remember{mutableStateOf(false)}
    var codeDialog by remember{mutableStateOf(false)}
    var pairingCode by remember{mutableStateOf("")}
    val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        val report=export;export=null;session.exportFinished()
        if(uri!=null&&report!=null)scope.launch {
            exportStatus=withContext(Dispatchers.IO){runCatching{
                requireNotNull(context.contentResolver.openOutputStream(uri)).use{it.write(report.toByteArray(Charsets.UTF_8))}
            }.fold({"Log gespeichert"},{"Log konnte nicht gespeichert werden"})}
        }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->result.contents?.let(session::join)}
    DisposableEffect(session){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)session.background()}
        activity.lifecycle.addObserver(observer)
        val window=activity.window
        val wasKeptOn=window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON!=0
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose{activity.lifecycle.removeObserver(observer);if(!wasKeptOn)window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);session.close()}
    }
    fun exit(){if(state.connected||state.phase=="pair")exitConfirm=true else onExit()}
    fun save(){export=state.report;session.exportStarted();saver.launch("phoenix-nfc-test.json")}
    BackHandler{exit()}
    Column(Modifier.fillMaxSize().background(Page).navigationBarsPadding().testTag("nfc-test-screen")) {
        if(state.master||!state.connected)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            IconButton({exit()},Modifier.semantics{contentDescription="Zurück zu Einstellungen"}){Glyph("back",Navy)}
            Text("NFC-Test",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=Navy)
        }
        if(!state.master&&state.phase !in listOf("intro","pair")) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp).testTag("nfc-test-peer"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                Image(painterResource(R.drawable.phoenix_favicon),"Phoenix Logo",Modifier.size(100.dp))
                Spacer(Modifier.height(28.dp))
                Text(state.title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Navy)
                Spacer(Modifier.height(16.dp))
                Text(state.detail,fontSize=14.sp,color=Muted)
                Spacer(Modifier.height(24.dp))
                NfcTestLine("privacy",if(state.connected)"Verschlüsselt mit dem Master verbunden" else "Kopplungsdaten verworfen")
            }
            if(!state.connected)Action("Fertig",onClick=session::reset)
        }else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nfc-test-content"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            if(state.phase in listOf("pair","ready","running","result")) {
                val step=when(state.phase){"pair"->1;"ready"->2;"running"->3;else->4}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    repeat(4){Box(Modifier.weight(1f).height(3.dp).background(if(it<step)Blue else Border,RoundedCornerShape(3.dp)))}
                }
                Text("Schritt $step von 4 · ${listOf("Koppeln","Vorbereiten","Prüfen","Ergebnis")[step-1]}",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Muted)
            }
            Text(state.title,fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=Navy,modifier=Modifier.semantics{heading()})
            if(state.phase in listOf("intro","pair","ready","running")) {
                NfcTestNote(if(state.phase=="running")"Patchbedarf nicht bestimmbar – Test läuft. Die Bewertung folgt nach Abschluss beider Richtungen." else "Patchbedarf nicht bestimmbar – Test ausstehend.")
            }
            when(state.phase) {
                "intro"->{
                    Text("Nacheinander werden eine vollständige ePassport-Session, eine vollständige eID-Session und der Samsung-Auswahlbefehl geprüft. Danach wiederholt sich die gesamte Folge mit vertauschten NFC-Rollen.",color=Muted)
                    NfcTestLine("wifi","Beide Telefone im selben WLAN")
                    NfcTestLine("phone","Einmal per QR-Code für diesen Test koppeln")
                    NfcTestLine("contactless","Telefone entsperrt Rücken an Rücken legen")
                    NfcTestNote("Dieses Telefon führt als Master durch alle Schritte. Das zweite Telefon zeigt während des Tests nur Logo und Status.")
                    Action("Test erstellen",modifier=Modifier.testTag("nfc-test-create"),onClick=session::create)
                    Action("An Test teilnehmen",primary=false,modifier=Modifier.testTag("nfc-test-join")){
                        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("QR-Code auf dem Master scannen").setBeepEnabled(false).setOrientationLocked(true).setCaptureActivity(QrCaptureActivity::class.java))
                    }
                    TextButton({pairingCode="";codeDialog=true},Modifier.testTag("nfc-test-code-join")){Text("Stattdessen Kopplungscode eingeben",fontSize=12.sp,color=Muted)}
                    NfcTestLine("privacy","Verschlüsselt · keine dauerhafte Kopplung")
                    NfcTestNote("Verwendet werden isolierte synthetische Profile. Geprüft werden PACE, Secure Messaging, PA, CA, TA und die berechtigten Datengruppen. Deine Dokumentprofile bleiben unverändert.")
                }
                "pair"->{
                    Text(state.detail,color=Muted)
                    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Blue,trackColor=Border)
                    if(state.qr.isNotEmpty()){
                        val bitmap=remember(state.qr){
                            val matrix=MultiFormatWriter().encode(state.qr,BarcodeFormat.QR_CODE,640,640)
                            Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888).apply{
                                val pixels=IntArray(640*640){index->if(matrix[index%640,index/640])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
                                setPixels(pixels,0,640,0,0,640,640)
                            }.asImageBitmap()
                        }
                        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Image(bitmap,"QR-Code zur temporären verschlüsselten Kopplung",Modifier.sizeIn(maxWidth=260.dp,maxHeight=260.dp).aspectRatio(1f).testTag("nfc-test-qr"))}
                        if(state.pairCode.isNotEmpty())Text("Alternativ mit Code koppeln: ${state.pairCode.chunked(3).joinToString(" ")}",fontSize=12.sp,color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally).testTag("nfc-pair-code"))
                        NfcTestNote("Der QR-Code gilt nur für diese Sitzung und höchstens fünf Minuten. Zeige ihn nur dem zweiten Testtelefon.")
                    }
                    Action("Verbindung trennen",primary=false,onClick=session::disconnect)
                }
                "ready"->{
                    NfcTestNote("${android.os.Build.MODEL} mit ${state.peer} verbunden")
                    NfcTestLine("privacy","Verschlüsselte Verbindung hergestellt")
                    NfcTestLine("contactless","NFC auf beiden Telefonen aktiviert")
                    NfcTestLine("file","Keine Änderung an deinen Dokumentprofilen")
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Art("nfc",Modifier.size(160.dp))}
                    Text(state.detail,color=Muted)
                    Action("Test starten",modifier=Modifier.testTag("nfc-test-start"),onClick=session::start)
                    Action("Verbindung trennen",primary=false,onClick=session::disconnect)
                }
                "running"->{
                    Text(state.detail,color=Muted,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(state.direction==0)"Dieses Telefon als Karte" else "Zweites Telefon als Karte",color=Navy,fontWeight=FontWeight.SemiBold);Text("${state.progress} %",color=Navy)}
                    LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth().testTag("nfc-test-progress"),color=Blue,trackColor=Border)
                    NfcTestLine("contactless",if(state.step>=1)"✓ NFC-Verbindung erkannt" else "NFC-Verbindung erkennen")
                    NfcTestLine("privacy",if(state.caseName=="samsung")"Authentifizierte Kontrollantwort prüfen" else "PACE · Secure Messaging · PA · CA · TA")
                    NfcTestLine("file",if(state.caseName!="samsung")"Berechtigte DGs lesen · unberechtigte Zugriffe müssen abgewiesen werden" else if(state.step>=3)"Auswahlbefehl übertragen · wird ausgewertet" else "Geschützten Auswahlbefehl prüfen")
                    NfcTestLine("phone","Beide NFC-Richtungen auswerten")
                    NfcTestLine("wifi","Steuerung verschlüsselt · Messung über NFC")
                    Action("Test abbrechen",primary=false,modifier=Modifier.testTag("nfc-test-cancel"),onClick=session::disconnect)
                }
                "result"->{
                    val verdict=state.verdict
                    val title=when(verdict){"not_observed"->"Alle Sessions und Auswahltests bestanden";"known_signature"->"Patchprüfung für diesen Firmwarestand empfohlen";else->"Patchbedarf nicht bestimmbar"}
                    val explanation=when(verdict){
                        "not_observed"->"ePassport und eID wurden nacheinander in beiden Richtungen vollständig geprüft. Auch die zusätzlichen Auswahltests waren erfolgreich."
                        "known_signature"->"Die Messung entspricht dem bekannten Fehlerbild auf dem ursprünglichen Galaxy-S21-Bibliotheksstand. Die native Ursache muss mit dem kalibrierten Dokumenttest bestätigt werden."
                        else->"Eine vollständige, korrelierte NFC-Messung fehlt. Ein Verbindungsabbruch allein beweist keinen Samsung-Fehler."
                    }
                    Surface(color=if(verdict=="not_observed")Color(0xffedf7f1)else Pale,shape=RoundedCornerShape(14.dp)){
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Glyph(if(verdict=="not_observed")"privacy" else "info",Blue)
                            Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Navy)
                            Text(explanation,fontSize=13.sp,color=Muted)
                        }
                    }
                    state.error?.let{NfcTestNote(it)}
                    state.report?.let { report->
                        val data=remember(report){JSONObject(report)}
                        NfcTestLine("phone",data.optJSONObject("master")?.optString("model")?:"Master")
                        NfcTestLine("phone",data.optJSONObject("peer")?.optString("model")?.takeIf{it.isNotEmpty()}?:"Zweites Telefon: keine Daten")
                    }
                    NfcTestNote("Die Bewertung gilt für diese Testläufe und Firmwarestände. Ein Fehler wird nicht automatisch als Samsung-Ursache eingestuft. Die App flasht dein Gerät nicht automatisch.")
                    state.report?.let{report->
                        val rows=remember(report){JSONObject(report).optJSONArray("directions")}
                        if(rows!=null)for(index in 0 until rows.length()){
                            val row=rows.getJSONObject(index)
                            NfcTestLine(if(row.optString("verdict")=="not_observed")"privacy" else "info", "${if(row.optInt("direction")==0)"Hinrichtung" else "Gegenrichtung"} · ${row.optString("kind")} · ${if(row.optString("verdict")=="not_observed")"bestanden" else "nicht vollständig bestätigt"}")
                        }
                    }
                    if(state.report!=null)Action("Gemeinsames Log speichern",modifier=Modifier.testTag("nfc-test-save"),onClick=::save)
                    if(state.connected)Action("Test wiederholen",primary=false,onClick=session::repeat)
                    else Action("Erneut koppeln",primary=false,onClick=session::reset)
                    if(state.connected)TextButton(onClick=session::disconnect){Text("Verbindung trennen")}
                }
                "ended"->{
                    Text(state.detail,color=Muted)
                    NfcTestLine("privacy","Verbindung getrennt · Kopplungsdaten verworfen")
                    if(state.report!=null){
                        NfcTestNote("Das gemeinsame JSON-Log enthält Gerätestände, Schritte und Ergebnisse beider Telefone. Keine Dokumentinhalte, Schlüssel oder Kopplungscodes.")
                        Action("Gemeinsames Log speichern",onClick=::save)
                    }
                    Action("Neuen Test vorbereiten",primary=false,onClick=session::reset)
                    Action("Fertig",primary=false,onClick=onExit)
                }
            }
            exportStatus?.let{Text(it,color=Muted,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
            Spacer(Modifier.height(12.dp))
        }
    }
    if(codeDialog)AlertDialog(onDismissRequest={codeDialog=false;pairingCode=""},title={Text("Mit Kopplungscode verbinden")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Gib den sechsstelligen Code unter dem QR-Code auf dem Master ein. Beide Telefone müssen im selben WLAN sein.")
        OutlinedTextField(pairingCode,{pairingCode=it.filter{digit->digit in '0'..'9'}.take(6)},label={Text("Kopplungscode")},singleLine=true,
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword),modifier=Modifier.testTag("nfc-test-code-input"))
    }},confirmButton={TextButton({val value=pairingCode;pairingCode="";codeDialog=false;session.joinCode(value)},enabled=pairingCode.length==6){Text("Verbinden")}},dismissButton={TextButton({pairingCode="";codeDialog=false}){Text("Abbrechen")}})
    if(exitConfirm)AlertDialog(onDismissRequest={exitConfirm=false},title={Text("NFC-Test beenden?")},text={Text("Die Verbindung wird getrennt und die temporäre Kopplung aufgehoben.")},confirmButton={TextButton({exitConfirm=false;onExit()}){Text("Beenden")}},dismissButton={TextButton({exitConfirm=false}){Text("Weiter testen")}})
}

@Composable private fun NfcTestLine(icon:String,text:String){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Glyph(icon,Blue,Modifier.size(22.dp));Text(text,fontSize=14.sp,color=Navy,modifier=Modifier.weight(1f))
    }
}
@Composable private fun NfcTestNote(text:String){
    Surface(color=Pale,shape=RoundedCornerShape(14.dp)){Text(text,Modifier.padding(16.dp),fontSize=13.sp,color=Muted)}
}
