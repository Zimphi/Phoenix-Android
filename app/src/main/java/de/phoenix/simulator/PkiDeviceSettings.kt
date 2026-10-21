package de.phoenix.simulator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun PkiDeviceSettings(identity:JSONObject,onAccountChanged:()->Unit) {
    val context=LocalContext.current
    val device=remember{PkiDevice(context.applicationContext)}
    val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf(if(device.connected())"Angemeldet" else "Nicht angemeldet")}
    var busy by remember{mutableStateOf(false)}
    var connected by remember{mutableStateOf(device.connected())}
    var hasLocalKey by remember{mutableStateOf(device.hasLocalKey())}
    fun perform(action:()->String){
        busy=true
        scope.launch{
            val result=withContext(Dispatchers.IO){runCatching(action)}
            message=result.getOrElse{it.message?:"PKI-Verbindung fehlgeschlagen"};connected=device.connected();hasLocalKey=device.hasLocalKey();onAccountChanged();busy=false
        }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        if(result.contents!=null)perform{device.enroll(result.contents);device.status()}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Glyph("cloud",Blue,Modifier.size(40.dp))
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("Phoenix-Konto",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Navy)
                Text("Anmeldung und zentrale PKI",fontSize=13.sp,color=Muted)
            }
        }
        if(connected)AccountIdentity(identity,device.environment())
        Surface(color=Pale,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
            Text(message,Modifier.fillMaxWidth().padding(16.dp),fontSize=14.sp,color=Navy)
        }
        if(!connected) {
            Text("Öffne die Phoenix-Web-Einstellungen und scanne den Geräte-QR-Code, um dich anzumelden und die zentrale PKI zu verwenden.",fontSize=14.sp,color=Muted)
            Action(if(busy)"Anmeldung läuft …" else "Mit QR-Code anmelden",enabled=!busy) {
                scanner.launch(ScanOptions().setCaptureActivity(QrCaptureActivity::class.java).setOrientationLocked(true)
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
                    .setPrompt("Geräte-QR-Code aus den Phoenix-Web-Einstellungen scannen"))
            }
        } else {
            Action("Verbindung prüfen",false,enabled=!busy){perform{device.status()}}
            Action("Abmelden",false,enabled=!busy){perform{device.disconnect()}}
        }
        if(hasLocalKey&&!connected) {
            TextButton(onClick={perform{device.forget()}},enabled=!busy){Text("Lokalen Zugang zurücksetzen")}
        }
    }
}
