// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun NfcUploadDialog(report:String,
    send:suspend(String,suspend(String)->Unit)->NfcUploadReceipt,
    delete:suspend(String)->Unit,onDismiss:()->Unit) {
    val preview=remember(report){runCatching{NfcUploadReport.clean(report)}.getOrNull()}
    val scope=rememberCoroutineScope()
    var job by remember{mutableStateOf<Job?>(null)}
    var busy by remember{mutableStateOf(false)}
    var progress by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    var receipt by remember{mutableStateOf<NfcUploadReceipt?>(null)}
    var deleted by remember{mutableStateOf(false)}
    fun close(){job?.cancel();onDismiss()}
    AlertDialog(onDismissRequest=::close,title={Text("Diagnoselog sicher senden")},text={
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("nfc-upload-preview"),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Empfänger: contact@epassport-simulator.de",color=Navy)
            Text("Das Log wird auf diesem Telefon mit dem öffentlichen PGP-Schlüssel verschlüsselt. Nur der Empfänger mit dem privaten Schlüssel kann es entschlüsseln.",color=Muted)
            if(preview!=null){
                Text("Telefone: ${preview.getJSONObject("master").getString("model")} und ${preview.getJSONObject("peer").getString("model")}")
                Text("Enthalten: Gerätestände, Bibliotheksversionen, Zeitstempel, Testergebnisse und NFC-Befehlsmetadaten beider Telefone.",color=Muted)
                Text("Keine Dokumentinhalte, privaten Schlüssel oder Kopplungscodes.",color=Muted)
            }else Text("Dieses Logformat kann nicht sicher übertragen werden. Der lokale Export bleibt möglich.")
            Text("Ziel: Phoenix Diagnose-Service · Aktive Speicherdauer: 14 Tage; verschlüsselte Sicherungshistorie beim Anbieter bis zu 7 weitere Tage. Für den Upload wird deine Phoenix-Geräteanmeldung verwendet.",color=Muted)
            if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text(progress,Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
            receipt?.let{value->
                Text(if(deleted)"Serverkopie gelöscht" else "Verschlüsselt gesendet",color=Navy,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
                Text("Vorgangsnummer: ${value.id}")
                if(!deleted){
                    Text("Automatische Löschung: "+DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(value.expiresAt)))
                    TextButton(enabled=!busy,onClick={job=scope.launch{
                        busy=true;error=null;progress="Serverkopie löschen …"
                        try{delete(value.id);deleted=true}catch(e:CancellationException){throw e}
                        catch(_:Exception){error="Löschung nicht bestätigt. Bitte erneut versuchen."}finally{busy=false}
                    }}){Text("Serverkopie löschen")}
                }
            }
        }
    },confirmButton={
        if(receipt==null)TextButton(enabled=!busy&&preview!=null,onClick={job=scope.launch{
            busy=true;error=null
            try{receipt=send(report){progress=it}}catch(e:CancellationException){throw e}
            catch(_:Exception){error="Senden fehlgeschlagen. Prüfe deine Phoenix-Anmeldung und versuche es erneut. Das lokale Log bleibt erhalten."}
            finally{busy=false}
        }}){Text("Verschlüsselt senden")}
        else TextButton(onClick=::close){Text("Fertig")}
    },dismissButton={if(receipt==null)TextButton(onClick=::close){Text(if(busy)"Übertragung abbrechen" else "Abbrechen")}})
}
