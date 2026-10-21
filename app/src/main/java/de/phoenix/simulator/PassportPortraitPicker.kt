package de.phoenix.simulator

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable internal fun PassportPortraitPicker(profile:Profile,onDismiss:()->Unit,onSelect:(String)->Unit) {
    val options=listOf("picture-1" to "Bild 1","picture-2" to "Bild 2","picture-3" to "Bild 3","shadow-male" to "Schattenriss männlich","shadow-female" to "Schattenriss weiblich")
    var previews by remember{mutableStateOf<Map<String,ImageBitmap>>(emptyMap())}
    LaunchedEffect(Unit) {
        previews=withContext(Dispatchers.IO){options.mapNotNull{(key,_)->runCatching{
            val draft=profile.coreDraft().put("groups",JSONObject(profile.groups.toString()).put("DG2","synthetic:$key"))
            decodePortrait(NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft)).getString("portrait"))?.let{key to it}
        }.getOrNull()}.toMap()}
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Gesichtsbild auswählen")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            options.forEach{(key,label)->
                Row(Modifier.fillMaxWidth().clickable{onSelect("synthetic:$key")}.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    previews[key]?.let{Image(it,contentDescription=null,modifier=Modifier.size(48.dp,64.dp),contentScale=ContentScale.Crop)}
                    Text(label,color=Navy)
                }
            }
        }
    },confirmButton={},dismissButton={TextButton(onDismiss){Text("Abbrechen")}})
}
