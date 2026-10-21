package de.phoenix.simulator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable internal fun PassportDataEditor(initial:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    val context=LocalContext.current
    val countries=remember{CountryCodes.load(context)}
    var countryField by remember{mutableStateOf<String?>(null)}
    var fields by remember(initial){mutableStateOf(JSONObject(initial))}
    var mrz by remember{mutableStateOf("")}
    var valid by remember{mutableStateOf(false)}
    val raw=fields.toString()
    LaunchedEffect(raw) {
        valid=false
        val draft=Profile("mrz-preview","epassport","MRZ",JSONObject().put("DG1",JSONObject(raw).toString())).json()
        val result=withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft)).getString("mrz")}}
        mrz=result.getOrDefault("");valid=mrz.length==88
    }
    @Composable fun Field(key:String,label:String,width:Modifier=Modifier.fillMaxWidth(),limit:Int=120) {
        OutlinedTextField(fields.optString(key),{input->fields=JSONObject(fields.toString()).put(key,input.take(limit));valid=false},label={Text(label,fontSize=12.sp)},singleLine=true,modifier=width.semantics{contentDescription=label},shape=androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
    }
    countryField?.let{key->CountryPicker(countries,{countryField=null}){country->fields=JSONObject(fields.toString()).put(key,country.mrz);valid=false;countryField=null}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("DG1 · Personendaten")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Field("surname","Familienname")
            Field("given_names","Vornamen")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Field("document_number","Dokumentnummer",Modifier.weight(1f),9)
                CountryField("Ausstellender Staat",fields.optString("issuing_state"),countries,Modifier.weight(1f)){countryField="issuing_state"}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                CountryField("Staatsangehörigkeit",fields.optString("nationality"),countries,Modifier.weight(1f)){countryField="nationality"}
                Field("sex","Geschlecht (M/F/<)",Modifier.weight(1f),1)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Field("date_of_birth","Geburt (JJMMTT)",Modifier.weight(1f),6)
                Field("date_of_expiry","Ablauf (JJMMTT)",Modifier.weight(1f),6)
            }
            Field("optional_data","Optionale Angaben",limit=14)
            Text("MRZ · automatisch berechnet",fontSize=12.sp,color=Muted)
            if(valid)Text(mrz.chunked(44).joinToString("\n"),fontSize=8.sp,fontFamily=FontFamily.Monospace,color=Navy)
            else Text("Bitte gültige Passangaben eingeben. Datum: JJMMTT; Staaten: ICAO-Code.",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton({onSave(raw)},enabled=valid){Text("Übernehmen")}},dismissButton={TextButton(onDismiss){Text("Abbrechen")}})
}
