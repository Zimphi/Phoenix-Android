package de.phoenix.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONArray
import org.json.JSONObject

private val eidDescriptions=listOf("Dokumenttyp","Ausstellender Staat","Ablaufdatum","Vornamen","Familienname","Ordens-/Künstlername","Doktorgrad","Geburtsdatum","Geburtsort","Staatsangehörigkeit","Geschlecht","Optionale Daten","Geburtsname","Unterschriftsbild","Ausstellungsdatum","Reserviert","Anschrift","Gemeindeschlüssel","Aufenthaltstitel I","Aufenthaltstitel II","Telefonnummer","E-Mail-Adresse")
private val passportDescriptions=listOf("Personendaten & MRZ","Gesichtsbild","Fingerabdrücke","Irisdaten","Portraitdarstellung","Reserviert","Unterschriftsbild","Zusätzliche Daten","Zusätzliche Daten","Zusätzliche Daten","Weitere Personendaten","Dokumentangaben","Optionale Angaben","Sicherheitsinformationen","Aktive Authentisierung","Benachrichtigung")
internal fun groupDescription(kind:String,key:String)= (if(kind=="eid")eidDescriptions else passportDescriptions).getOrNull(key.removePrefix("DG").toIntOrNull()?.minus(1)?:-1)?:"Datengruppe"
private val fieldNames=mapOf("surname" to "Familienname","given_names" to "Vornamen","document_number" to "Dokumentnummer","date_of_birth" to "Geburtsdatum","date_of_expiry" to "Gültig bis","issuing_state" to "Ausstellender Staat","nationality" to "Staatsangehörigkeit","sex" to "Geschlecht","optional_data" to "Optionale Daten","street" to "Straße","zipcode" to "Postleitzahl","city" to "Ort","country" to "Staat","state" to "Bundesland")
internal fun readableValue(value:Any?):String=when(value) {
    null,JSONObject.NULL->""
    is JSONObject-> if(value.optString("type")=="bytes")"Binärdaten · ${value.optJSONArray("value")?.length()?:0} Bytes" else if(value.has("type")&&value.has("value"))readableValue(value.opt("value")) else value.keys().asSequence().mapNotNull{key->readableValue(value.opt(key)).takeIf{it.isNotBlank()}?.let{"${fieldNames[key]?:key}: $it"}}.joinToString("\n")
    is JSONArray->(0 until value.length()).map{readableValue(value.opt(it))}.filter{it.isNotBlank()}.joinToString(" · ")
    else->value.toString()
}
internal fun groupValue(kind:String,key:String,raw:String):String {
    if(raw.isBlank())return "Keine Angabe"
    val json=runCatching{if(raw.trimStart().startsWith("{"))JSONObject(raw)else if(raw.trimStart().startsWith("["))JSONArray(raw)else null}.getOrNull()
    if(json!=null)return readableValue(json).ifBlank{"Keine Angabe"}
    if(kind=="epassport"&&raw.length%2==0&&raw.matches(Regex("[0-9a-fA-F]+")))return "${groupDescription(kind,key)} · ${raw.length/2} Bytes"
    return raw
}
internal fun profileField(profile:Profile,groups:JSONObject,field:String):String {
    val key=mapOf("given_names" to "DG4","surname" to "DG5","date_of_birth" to "DG8","date_of_expiry" to "DG3","nationality" to "DG10")[field]
    return if(profile.kind=="eid")key?.let{groupValue("eid",it,groups.optString(it)).takeUnless{v->v=="Keine Angabe"}}?:"—"
    else runCatching{JSONObject(groups.optString("DG1")).optString(field).ifBlank{"—"}}.getOrDefault("—")
}

/** Schematic document view; every displayed field comes from the selected profile. */
@Composable internal fun DocumentPreview(profile:Profile,groups:JSONObject=profile.groups,modifier:Modifier=Modifier,compact:Boolean=false) {
    val passport=profile.kind=="epassport"
    val shape=RoundedCornerShape(if(compact)6.dp else 12.dp)
    val content=Modifier.fillMaxSize().clip(shape).background(if(passport)Color(0xfff2eee8)else Color(0xffe8eee5)).border(1.dp,if(passport)Color(0xffd7c8b9)else Color(0xffc5d2c0),shape)
    Column(modifier.semantics{contentDescription=if(passport)"Reisepass Innenseite" else "Personalausweis Vorderseite"}.then(content).padding(if(compact)5.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(if(compact)3.dp else 6.dp)) {
        Text(if(passport)"REISEPASS · TESTDOKUMENT" else "PERSONALAUSWEIS · TESTDOKUMENT",fontSize=if(compact)5.sp else 9.sp,fontWeight=FontWeight.Bold,color=Navy,maxLines=1)
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(if(compact)5.dp else 12.dp)) {
            Column(Modifier.fillMaxHeight().width(if(compact)22.dp else 56.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha=.65f)),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Box(Modifier.size(if(compact)9.dp else 20.dp).background(Muted.copy(alpha=.4f),androidx.compose.foundation.shape.CircleShape))
                Box(Modifier.padding(top=3.dp).size(if(compact)15.dp else 36.dp,if(compact)12.dp else 25.dp).background(Muted.copy(alpha=.4f),RoundedCornerShape(topStart=15.dp,topEnd=15.dp)))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.SpaceEvenly) {
                Text(profileField(profile,groups,"surname"),fontSize=if(compact)7.sp else 15.sp,fontWeight=FontWeight.ExtraBold,color=Navy,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(profileField(profile,groups,"given_names"),fontSize=if(compact)6.sp else 12.sp,fontWeight=FontWeight.SemiBold,color=Navy,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(!compact)Text("Geboren: ${profileField(profile,groups,"date_of_birth")}   ·   ${profileField(profile,groups,"nationality")}",fontSize=9.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        if(passport)Text(runCatching{JSONObject(groups.optString("DG1")).optString("document_number")}.getOrDefault("").let{if(compact)"P< · SYNTHETISCHES TESTPROFIL" else "P<  $it  ·  SYNTHETISCHES TESTPROFIL"},fontSize=if(compact)4.sp else 8.sp,fontFamily=FontFamily.Monospace,color=Muted,maxLines=1)
        else if(!compact)Text("Gültig bis ${profileField(profile,groups,"date_of_expiry")}",fontSize=8.sp,color=Muted)
    }
}
