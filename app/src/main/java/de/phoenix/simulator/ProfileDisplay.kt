package de.phoenix.simulator

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val key=mapOf("given_names" to "DG4","surname" to "DG5","date_of_birth" to "DG8","date_of_expiry" to "DG3","nationality" to "DG10","birth_place" to "DG9","issuing_state" to "DG2")[field]
    return if(profile.kind=="eid")key?.let{groupValue("eid",it,groups.optString(it)).takeUnless{v->v=="Keine Angabe"}}?:"—"
    else runCatching{JSONObject(groups.optString("DG1")).optString(field).ifBlank{"—"}}.getOrDefault("—")
}

/** Schematic document view; every displayed field comes from the selected profile. */
@Composable internal fun DocumentPreview(profile:Profile,groups:JSONObject=profile.groups,modifier:Modifier=Modifier,compact:Boolean=false,mrz:String="") {
    val passport=profile.kind=="epassport"
    if(passport){val display=passportDisplay(profile);PassportPreview(profile,display?.groups?:groups,modifier,compact,display?.mrz?:mrz,display?.image);return}
    val context=LocalContext.current
    val countries=remember{CountryCodes.load(context)}
    val issuer=profileField(profile,groups,"issuing_state")
    val country=if(issuer in listOf("D","D<<","DEU"))"BUNDESREPUBLIK DEUTSCHLAND" else countries.firstOrNull{it.hasCode(issuer)}?.name?:issuer
    Canvas(modifier.semantics{contentDescription="Personalausweis Vorderseite"}) {
        val factor=minOf(size.width/856f,size.height/540f)
        translate((size.width-856f*factor)/2,(size.height-540f*factor)/2){scale(factor,factor,pivot=Offset.Zero) {
            identityDocument(profileField(profile,groups,"surname"),profileField(profile,groups,"given_names"),passportDate(profileField(profile,groups,"date_of_birth")),profileField(profile,groups,"nationality"),passportDate(profileField(profile,groups,"date_of_expiry")),profileField(profile,groups,"birth_place").replace("\n"," · "),profile.settings.optString("can"),country)
        }}
    }
}

internal fun passportDate(raw:String)=if(raw.matches(Regex("[0-9]{6}")))"${raw.substring(4,6)}.${raw.substring(2,4)}.${raw.substring(0,2)}" else if(raw.matches(Regex("[0-9]{8}")))"${raw.substring(6,8)}.${raw.substring(4,6)}.${raw.substring(0,4)}" else raw

@Composable private fun PassportPreview(profile:Profile,groups:JSONObject,modifier:Modifier,compact:Boolean,mrz:String,image:ImageBitmap?) {
    val context=LocalContext.current
    val countries=remember{CountryCodes.load(context)}
    val data=runCatching{JSONObject(groups.optString("DG1"))}.getOrDefault(JSONObject())
    val code=data.optString("issuing_state")
    val country=if(code in listOf("D","D<<","DEU"))"BUNDESREPUBLIK DEUTSCHLAND" else countries.firstOrNull{it.hasCode(code)}?.name?:code
    BoxWithConstraints(modifier.semantics{contentDescription="Reisepass Innenseite"}) {
        Canvas(Modifier.fillMaxSize()) {
            val factor=minOf(size.width/720f,size.height/507f)
            translate((size.width-720f*factor)/2,(size.height-507f*factor)/2){scale(factor,factor,pivot=Offset.Zero) {
                passportDocument(country,code,profileField(profile,groups,"surname"),profileField(profile,groups,"given_names"),data.optString("document_number"),passportDate(profileField(profile,groups,"date_of_birth")),data.optString("sex"),profileField(profile,groups,"nationality"),passportDate(profileField(profile,groups,"date_of_expiry")),mrz)
            }}
        }
        val photo=Modifier.offset(maxWidth*(28f/720f),maxHeight*(118f/507f)).size(maxWidth*(185f/720f),maxHeight*(245f/507f)).clip(RoundedCornerShape(2.dp))
        if(image!=null)Image(image,contentDescription="Synthetisches Gesichtsbild",modifier=photo,contentScale=ContentScale.Crop)
        else Canvas(photo){identityPortrait()}
    }
}

private data class PassportDisplay(val groups:JSONObject,val mrz:String,val image:ImageBitmap?)
@Composable private fun passportDisplay(profile:Profile):PassportDisplay? {
    val draft=profile.json()
    var result by remember(profile.id,profile.groups.toString()){mutableStateOf<PassportDisplay?>(null)}
    LaunchedEffect(profile.id,profile.groups.toString()) {
        result=withContext(Dispatchers.IO){runCatching {
            val description=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft))
            val encoded=description.optString("portrait")
            val image=if(encoded.isEmpty()||encoded=="null")null else decodePortrait(encoded)
            PassportDisplay(description.getJSONObject("groups"),description.optString("mrz"),image)
        }.getOrDefault(PassportDisplay(profile.groups,"",null))}
    }
    return result
}
internal fun decodePortrait(encoded:String):ImageBitmap? {
    require(encoded.length<=2*1024*1024){"Gesichtsbild zu groß"}
    val bytes=Base64.decode(encoded,Base64.NO_WRAP)
    val dimensions=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,dimensions)
    require(dimensions.outWidth in 1..4096&&dimensions.outHeight in 1..4096&&dimensions.outWidth.toLong()*dimensions.outHeight<=8_000_000){"Ungültige Bildgröße"}
    return BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()
}
@Composable internal fun PassportFace(profile:Profile) {
    val display=passportDisplay(profile)
    val image=display?.image
    if(image!=null)Image(image,contentDescription="Synthetisches Gesichtsbild",modifier=Modifier.fillMaxWidth().heightIn(max=380.dp),contentScale=ContentScale.Fit)
    else Text(if(display==null)"Bild wird geladen …" else "Kein darstellbares Gesichtsbild vorhanden.",color=Muted)
}
