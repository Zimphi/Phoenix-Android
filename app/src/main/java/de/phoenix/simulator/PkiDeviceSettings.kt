package de.phoenix.simulator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun PkiDeviceSettings() {
    val context=LocalContext.current
    val device=remember{PkiDevice(context.applicationContext)}
    val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf(if(device.connected())"Gerät registriert" else "Noch kein PKI-Gerätezugang")}
    var busy by remember{mutableStateOf(false)}
    var connected by remember{mutableStateOf(device.connected())}
    var hasLocalKey by remember{mutableStateOf(device.hasLocalKey())}
    fun perform(action:()->String){
        busy=true
        scope.launch{
            val result=withContext(Dispatchers.IO){runCatching(action)}
            message=result.getOrElse{it.message?:"PKI-Verbindung fehlgeschlagen"};connected=device.connected();hasLocalKey=device.hasLocalKey();busy=false
        }
    }
    val scanner=rememberLauncherForActivityResult(ScanContract()){result->
        if(result.contents!=null)perform{device.enroll(result.contents)}
    }
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Zentrale PKI",style=MaterialTheme.typography.titleMedium)
            Text(message)
            Button(modifier=Modifier.semantics{contentDescription="PKI-QR-Code scannen"},enabled=!busy&&!connected,onClick={scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false).setPrompt("PKI-QR-Code aus den Phoenix-Web-Einstellungen scannen"))}){Text("PKI-QR-Code scannen")}
            if(connected){
                OutlinedButton(modifier=Modifier.semantics{contentDescription="PKI-Verbindung prüfen"},enabled=!busy,onClick={perform{device.status()}}){Text("PKI-Verbindung prüfen")}
                TextButton(modifier=Modifier.semantics{contentDescription="Gerätezugang trennen"},enabled=!busy,onClick={perform{device.disconnect()}}){Text("Gerätezugang trennen")}
            }
            if(hasLocalKey){
                TextButton(modifier=Modifier.semantics{contentDescription="Lokalen Schlüssel entfernen"},enabled=!busy,onClick={perform{device.forget()}}){Text("Lokalen Schlüssel entfernen")}
                Text("Entfernt nur den Schlüssel auf diesem Gerät. Die serverseitige Freigabe lässt sich in den Web-Einstellungen sperren.")
            }
        }
    }
}
