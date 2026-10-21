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
import androidx.compose.ui.platform.testTag
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
private val groupWrappers=setOf("Text","Place","GeneralPlace","Places","Freetext","Structured","Compressed","Optional")
internal fun readableValue(value:Any?):String=when(value) {
    null,JSONObject.NULL->""
    is JSONObject->when {
        value.optString("type")=="bytes"->"Binärdaten · ${value.optJSONArray("value")?.length()?:0} Bytes"
        value.length()==1&&value.has("Bytes")->"Binärdaten · ${value.optJSONArray("Bytes")?.length()?:0} Bytes"
        value.has("type")&&value.has("value")->readableValue(value.opt("value"))
        value.length()==1&&value.has("Unavailable")->"Nicht verfügbar: ${readableValue(value.opt("Unavailable"))}"
        value.length()==1&&value.keys().next() in groupWrappers->readableValue(value.opt(value.keys().next()))
        else->value.keys().asSequence().mapNotNull{key->readableValue(value.opt(key)).takeIf{it.isNotBlank()}?.let{"${fieldNames[key]?:key}: $it"}}.joinToString("\n")
    }
    is JSONArray->(0 until value.length()).map{readableValue(value.opt(it))}.filter{it.isNotBlank()}.joinToString(" · ")
    else->value.toString()
}
internal fun editableGroupText(raw:String):String {
    val parsed=runCatching{JSONObject(raw)}.getOrNull()?:return raw
    if(parsed.length()==1&&parsed.opt("Text") is String)return parsed.getString("Text")
    val place=parsed.optJSONObject("GeneralPlace")
    if(parsed.length()==1&&place?.length()==1&&place.opt("Freetext") is String)return place.getString("Freetext")
    return raw
}

internal fun groupValue(kind:String,key:String,raw:String):String {
    if(raw.isBlank())return "Keine Angabe"
    val json=runCatching{if(raw.trimStart().startsWith("{"))JSONObject(raw)else if(raw.trimStart().startsWith("["))JSONArray(raw)else null}.getOrNull()
    if(json!=null)return readableValue(json).ifBlank{"Keine Angabe"}
    if(kind=="epassport"&&raw.length%2==0&&raw.matches(Regex("[0-9a-fA-F]+")))return "${groupDescription(kind,key)} · ${raw.length/2} Bytes"
    return raw
}
internal fun profileField(profile:Profile,groups:JSONObject,field:String):String {
    val key=mapOf("given_names" to "DG4","surname" to "DG5","birth_name" to "DG13","date_of_birth" to "DG8","date_of_expiry" to "DG3","nationality" to "DG10","birth_place" to "DG9","issuing_state" to "DG2")[field]
    return if(profile.kind=="eid")key?.let{groupValue("eid",it,groups.optString(it)).takeUnless{v->v=="Keine Angabe"}}?:"—"
    else runCatching{JSONObject(groups.optString("DG1")).optString(field).ifBlank{"—"}}.getOrDefault("—")
}

internal fun portraitSex(profile:Profile,groups:JSONObject):String {
    val value=if(profile.kind=="epassport")runCatching{JSONObject(groups.optString("DG1")).optString("sex")}.getOrDefault("")
    else profile.portraitSex.ifEmpty{groupValue("eid","DG11",groups.optString("DG11"))}
    return value.trim().uppercase().takeIf{it=="M"||it=="F"}?:if(profile.kind=="eid")"M" else ""
}

internal fun identityCheckDigit(value:String):Char {
    val weights=intArrayOf(7,3,1)
    val total=value.mapIndexed{index,c->val n=when(c){in '0'..'9'->c-'0';in 'A'..'Z'->c-'A'+10;'<'->0;else->error("Ungültiges MRZ-Zeichen")};n*weights[index%3]}.sum()
    return ('0'.code+total%10).toChar()
}
internal fun td1FromTd3(td3:String,type:String):String {
    require(td3.matches(Regex("[A-Z0-9<]{88}"))&&type.matches(Regex("[A-Z<]{2}")))
    val first=type+td3.substring(2,5)+td3.substring(44,54)+"<".repeat(15)
    val second=td3.substring(57,64)+td3[64]+td3.substring(65,72)+td3.substring(54,57)+"<".repeat(11)
    val check=identityCheckDigit(first.substring(5)+second.substring(0,7)+second.substring(8,15)+second.substring(18,29))
    return first+"\n"+second+check+"\n"+td3.substring(5,44).take(30).padEnd(30,'<')
}
@Composable private fun identityMrz(profile:Profile,groups:JSONObject,countries:List<CountryCode>):String {
    var mrz by remember(profile.id){mutableStateOf("")}
    val source=groups.toString()
    LaunchedEffect(profile.id,profile.documentNumber,source) {
        mrz=""
        if(profile.documentType()=="UB")return@LaunchedEffect
        mrz=withContext(Dispatchers.IO){runCatching{
            fun code(field:String):String {val raw=profileField(profile,groups,field);return countries.firstOrNull{it.hasCode(raw)}?.mrz?:raw}
            val dg1=JSONObject().put("surname",profileField(profile,groups,"surname")).put("given_names",profileField(profile,groups,"given_names")).put("issuing_state",code("issuing_state")).put("nationality",code("nationality")).put("document_number",profile.documentNumber).put("date_of_birth",profileField(profile,groups,"date_of_birth").takeLast(6)).put("date_of_expiry",profileField(profile,groups,"date_of_expiry").takeLast(6)).put("sex",groups.optString("DG11").trim().ifBlank{"<"}).put("optional_data","")
            val draft=Profile("identity-mrz","epassport","MRZ",JSONObject().put("DG1",dg1.toString())).coreDraft()
            td1FromTd3(NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft)).getString("mrz"),profile.documentType())
        }.getOrDefault("")}
    }
    return mrz
}

/** Schematic document view; every displayed field comes from the selected profile. */
@Composable internal fun DocumentPreview(profile:Profile,groups:JSONObject=profile.groups,modifier:Modifier=Modifier,compact:Boolean=false,mrz:String="",portraitOverride:String=profile.portraitSex,back:Boolean=false) {
    val passport=profile.kind=="epassport"
    if(passport){val display=passportDisplay(profile);PassportPreview(profile,display?.groups?:groups,modifier,compact,display?.mrz?:mrz,display?.image);return}
    val context=LocalContext.current
    val countries=remember{CountryCodes.load(context)}
    val sex=portraitOverride.ifEmpty{portraitSex(profile,groups)}
    val portrait=portraitBitmap(sex)
    val watermark=remember{BitmapFactory.decodeResource(context.resources,R.drawable.phoenix_wordmark).asImageBitmap()}
    val paper=remember(back,profile.documentType()){BitmapFactory.decodeResource(context.resources,if(back||profile.documentType()=="UB"||residenceDocument(profile.documentType()))R.drawable.document_back_paper else R.drawable.document_paper).asImageBitmap()}
    val identityMrz=identityMrz(profile,groups,countries)
    val issuer=profileField(profile,groups,"issuing_state")
    val country=if(issuer in listOf("D","D<<","DEU"))"BUNDESREPUBLIK DEUTSCHLAND" else countries.firstOrNull{it.hasCode(issuer)}?.name?:issuer
    Canvas(modifier.testTag("identity-portrait-${sex.ifEmpty{"auto"}}").semantics{contentDescription=(when{profile.documentType()=="UB"->"eID-Karte";residenceDocument(profile.documentType())->"Aufenthaltstitel";else->"Personalausweis"})+if(back)" Rückseite" else " Vorderseite"}) {
        val factor=minOf(size.width/856f,size.height/540f)
        translate((size.width-856f*factor)/2,(size.height-540f*factor)/2){scale(factor,factor,pivot=Offset.Zero) {
            if(profile.documentType()=="UB")unionDocument(profile,groups,paper,watermark,back)
            else if(residenceDocument(profile.documentType()))permitDocument(profile,groups,paper,portrait,identityMrz,back,countries)
            else if(back)identityBack(groups,paper,countries,identityMrz)
            else identityDocument(profileField(profile,groups,"surname"),profileField(profile,groups,"given_names"),passportDate(profileField(profile,groups,"date_of_birth")),profileField(profile,groups,"nationality"),passportDate(profileField(profile,groups,"date_of_expiry")),profileField(profile,groups,"birth_place").replace("\n"," · "),profile.settings.optString("can"),country,profileField(profile,groups,"birth_name"),portrait,watermark,paper,profile.documentNumber)
        }}
    }
}

internal fun passportDate(raw:String)=if(raw.matches(Regex("[0-9]{6}")))"${raw.substring(4,6)}.${raw.substring(2,4)}.${raw.substring(0,2)}" else if(raw.matches(Regex("[0-9]{8}")))"${raw.substring(6,8)}.${raw.substring(4,6)}.${raw.substring(0,4)}" else raw

@Composable private fun PassportPreview(profile:Profile,groups:JSONObject,modifier:Modifier,compact:Boolean,mrz:String,image:ImageBitmap?) {
    val context=LocalContext.current
    val countries=remember{CountryCodes.load(context)}
    val data=runCatching{JSONObject(groups.optString("DG1"))}.getOrDefault(JSONObject())
    val fallback=portraitBitmap(portraitSex(profile,groups))
    val watermark=remember{BitmapFactory.decodeResource(context.resources,R.drawable.phoenix_wordmark).asImageBitmap()}
    val paper=remember{BitmapFactory.decodeResource(context.resources,R.drawable.document_paper).asImageBitmap()}
    val code=data.optString("issuing_state")
    val country=if(code in listOf("D","D<<","DEU"))"BUNDESREPUBLIK DEUTSCHLAND" else countries.firstOrNull{it.hasCode(code)}?.name?:code
    BoxWithConstraints(modifier.semantics{contentDescription="Reisepass Innenseite"}) {
        Canvas(Modifier.fillMaxSize()) {
            val factor=minOf(size.width/720f,size.height/550f)
            translate((size.width-720f*factor)/2,(size.height-550f*factor)/2){scale(factor,factor,pivot=Offset.Zero) {
                passportDocument(country,code,profileField(profile,groups,"surname"),profileField(profile,groups,"given_names"),data.optString("document_number"),passportDate(profileField(profile,groups,"date_of_birth")),data.optString("sex"),profileField(profile,groups,"nationality"),passportDate(profileField(profile,groups,"date_of_expiry")),mrz,watermark,paper)
            }}
        }
        val photo=Modifier.offset(maxWidth*(25f/720f),maxHeight*(90f/550f)).size(maxWidth*(225f/720f),maxHeight*(309f/550f)).clip(RoundedCornerShape(2.dp))
        if(image!=null)Image(image,contentDescription="Synthetisches Gesichtsbild",modifier=photo,contentScale=ContentScale.Crop)
        else Canvas(photo){identityPortrait(portrait=fallback)}
    }
}

private data class PassportDisplay(val groups:JSONObject,val mrz:String,val image:ImageBitmap?)
@Composable private fun passportDisplay(profile:Profile):PassportDisplay? {
    val draft=profile.coreDraft()
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
