package de.phoenix.simulator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun RiSettings(profile:Profile,onState:(JSONObject)->Unit) {
    val scope=rememberCoroutineScope()
    var state by remember(profile.id){mutableStateOf<JSONObject?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var edit by remember{mutableStateOf(false)}
    var values by remember{mutableStateOf(JSONObject())}
    fun run(request:JSONObject) {
        busy=true;error=null
        val draft=profile.coreDraft()
        scope.launch {
            withContext(Dispatchers.IO){runCatching {
                NativeCore.call(JSONObject().put("op","ifd_stop"))
                NativeCore.call(JSONObject().put("op","mount_draft").put("draft",draft))
                NativeCore.call(JSONObject().put("op","close"))
                NativeCore.call(request.put("id",profile.id))
            }}.onSuccess{state=it.optJSONObject("ri");onState(it)}.onFailure{error=it.message}
            busy=false
        }
    }
    ProfileSettingsSection("RI-Schlüssel","privacy","ri-settings") {
        Text("Eigenes Schlüsselpaar für EC und DH. Änderungen wirken sich auf das Pseudonym gegenüber einem Dienst aus.",fontSize=11.sp,color=Muted)
        if(state==null)Button({run(JSONObject().put("op","editor_state"))},enabled=!busy){Text("RI-Schlüssel laden")}
        state?.let {ri->
            Row(Modifier.fillMaxWidth()) {
                Text("RI bei Datenänderungen beibehalten",modifier=Modifier.weight(1f),fontSize=13.sp,color=Navy)
                Switch(ri.optBoolean("frozen"),{run(JSONObject().put("op","ri_update").put("frozen",it))},enabled=!busy)
            }
            Text("EC · brainpoolP256r1 · DH · 2048 Bit",fontSize=11.sp,color=Muted)
            OutlinedButton({values=JSONObject(ri.toString());edit=true},enabled=!busy){Text("Schlüssel anzeigen / ändern")}
            OutlinedButton({run(JSONObject().put("op","ri_update").put("frozen",ri.optBoolean("frozen")).put("regenerate",true))},enabled=!busy){Text("RI-Schlüssel neu erzeugen")}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.error)}
    }
    if(edit)AlertDialog(onDismissRequest={edit=false},title={Text("RI-Schlüssel bearbeiten")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Hexadezimale Testschlüssel. Private und öffentliche Werte müssen zusammenpassen.",fontSize=12.sp)
            listOf("ec_private" to "EC privat (PKCS#8)","ec_public" to "EC öffentlich (DER)","dh_private" to "DH privat", "dh_public" to "DH öffentlich").forEach{(key,label)->
                var value by remember(values,key){mutableStateOf(values.optString(key))}
                OutlinedTextField(value,{value=it;values.put(key,it)},label={Text(label)},maxLines=2,modifier=Modifier.fillMaxWidth())
            }
        }
    },confirmButton={TextButton({
        val keys=JSONObject();listOf("ec_private","ec_public","dh_private","dh_public").forEach{keys.put(it,values.getString(it))}
        edit=false;run(JSONObject().put("op","ri_update").put("frozen",state?.optBoolean("frozen")?:false).put("keys",keys))
    }){Text("Übernehmen")}},dismissButton={TextButton({edit=false}){Text("Abbrechen")}})
}
