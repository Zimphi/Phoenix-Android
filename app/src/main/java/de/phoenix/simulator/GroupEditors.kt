package de.phoenix.simulator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.json.JSONObject
import org.json.JSONArray

internal fun compactDate(raw:String,birth:Boolean):LocalDate?=runCatching {
    require(raw.matches(Regex("[0-9]{6}|[0-9]{8}")))
    val short=raw.length==6
    var year=raw.take(if(short)2 else 4).toInt()
    if(short){year+=2000;if(birth&&year>LocalDate.now().year)year-=100}
    LocalDate.of(year,raw.takeLast(4).take(2).toInt(),raw.takeLast(2).toInt())
}.getOrNull()
internal fun dateValue(day:String,month:String,year:String,short:Boolean):String?=runCatching {
    require(year.length==4)
    val date=LocalDate.of(year.toInt(),month.toInt(),day.toInt())
    require(date.year in 1..9999)
    val full="%04d%02d%02d".format(java.util.Locale.ROOT,date.year,date.monthValue,date.dayOfMonth)
    if(short)full.takeLast(6)else full
}.getOrNull()

@Composable internal fun DateFields(label:String,initial:String,short:Boolean=false,birth:Boolean=false,onChange:(String?)->Unit) {
    val date=remember{compactDate(initial,birth)}
    var day by remember{mutableStateOf(date?.dayOfMonth?.toString()?:"")}
    var month by remember{mutableStateOf(date?.monthValue?.toString()?:"")}
    var year by remember{mutableStateOf(date?.year?.toString()?:"")}
    val result=dateValue(day,month,year,short)
    LaunchedEffect(day,month,year){onChange(result)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,color=Navy)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Tag",day,{v:String->day=v}),Triple("Monat",month,{v:String->month=v}),Triple("Jahr",year,{v:String->year=v})).forEachIndexed{index,(name,value,change)->
                OutlinedTextField(value,{change(it.filter(Char::isDigit).take(if(index==2)4 else 2))},label={Text(name)},singleLine=true,modifier=Modifier.weight(if(index==2)1.4f else 1f).testTag("${label}-${name}"),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }
        }
        if(result==null)Text("Bitte ein gültiges Datum mit Tag, Monat und vierstelligem Jahr eingeben.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun ChoiceField(label:String,value:String,choices:List<Pair<String,String>>,onChange:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Box {
        OutlinedButton({expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
            Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelSmall,color=Muted);Text(choices.firstOrNull{it.first==value}?.second?:value,color=Navy)}
            Glyph("chevron")
        }
        DropdownMenu(expanded,{expanded=false}){choices.forEach{(key,name)->DropdownMenuItem(text={Text(name)},onClick={expanded=false;onChange(key)})}}
    }
}

internal fun structuredPlaces(value:Any?):List<JSONObject> = when(value) {
    is JSONArray->(0 until value.length()).flatMap{structuredPlaces(value.opt(it))}
    is JSONObject->if(value.has("city")&&value.has("country"))listOf(value)else listOf("Place","GeneralPlace","Structured","Places").flatMap{key->structuredPlaces(value.opt(key))}
    else->emptyList()
}
internal fun residenceDocument(type:String)=type in listOf("AR","AS","AF")

@Composable internal fun EidGroupEditor(group:String,initial:String,onDismiss:()->Unit,onSave:(String)->Unit) {
    val context=LocalContext.current
    val countries=remember{CountryCodes.load(context)}
    var value by remember{mutableStateOf(editableGroupText(initial))}
    var valid by remember{mutableStateOf(group !in listOf("DG3","DG8","DG15")||compactDate(value,group=="DG8")!=null)}
    var country by remember{mutableStateOf(false)}
    val parsed=remember{runCatching{JSONObject(initial)}.getOrNull()}
    val places=remember{structuredPlaces(parsed).ifEmpty{listOf(JSONObject().put("city","").put("country","D").put("street","").put("zipcode","").put("state",""))}}
    var address by remember{mutableStateOf(0)}
    var revision by remember{mutableIntStateOf(0)}
    val place=places[address]
    fun update(key:String,text:String){place.put(key,text);revision++}
    val addressValid=revision.let{place.optString("city").isNotBlank()&&place.optString("country").matches(Regex("[A-Z]{1}|[A-Z]{3}"))}
    val title=groupDescription("eid",group)
    if(country)CountryPicker(countries,{country=false}){selected->
        if(group=="DG17")update("country",selected.icao) else value=selected.icao
        country=false
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("$group · $title")},text={
        Column(Modifier.fillMaxWidth().semantics{testTagsAsResourceId=true}.testTag("group-editor-fields").heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(group) {
                "DG1"->ChoiceField("Dokumenttyp",value,listOf("ID" to "ID · Personalausweis","AR" to "AR · Aufenthaltstitel","AS" to "AS · Aufenthaltserlaubnis Schweiz","AF" to "AF · Aufenthaltskarte für EU-Familienangehörige","OA" to "OA · Smart-eID","UB" to "UB · eID-Karte für Unionsbürger")){value=it}
                "DG7"->ChoiceField("Doktorgrad",if(value.isBlank())"" else "DR.",listOf("" to "Nein","DR." to "Ja")){value=if(it.isNotEmpty()&&value.isNotBlank())value else it}
                "DG3","DG8","DG15"->DateFields(title,value,birth=group=="DG8"){valid=it!=null;if(it!=null)value=it}
                "DG2","DG10"->CountryField(title,value,countries){country=true}
                "DG11"->ChoiceField("Geschlecht",value,listOf("F" to "Weiblich","M" to "Männlich"," " to "Keine Angabe")){value=it}
                "DG17"->{
                    if(places.size>1)ChoiceField("Anschrift",address.toString(),places.indices.map{it.toString() to "Anschrift ${it+1}"}){address=it.toInt()}
                    if(structuredPlaces(parsed).isEmpty()&&initial.isNotBlank())Text("Bisherige Angabe: ${groupValue("eid",group,initial)}",style=MaterialTheme.typography.bodySmall)
                    listOf("street" to "Straße und Hausnummer","zipcode" to "Postleitzahl","city" to "Ort","state" to "Bundesland / Region").forEach{(key,label)->
                        OutlinedTextField(if(place.isNull(key))"" else place.optString(key),{update(key,it.take(120))},label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth())
                    }
                    CountryField("Staat",place.optString("country"),countries){country=true}
                    if(!addressValid)Text("Ort und Staat sind erforderlich.",color=MaterialTheme.colorScheme.error)
                }
                else->OutlinedTextField(value,{value=it.take(65536)},label={Text(title)},minLines=if(group in listOf("DG4","DG5","DG6","DG9","DG13"))1 else 3,maxLines=8,modifier=Modifier.fillMaxWidth())
            }
        }
    },confirmButton={TextButton({
        val result=if(group=="DG17") {
            if(structuredPlaces(parsed).isNotEmpty())parsed!!.toString()
            else JSONObject().put("GeneralPlace",JSONObject().put("Structured",place)).toString()
        }else value
        onSave(result)
    },enabled=valid&&(group!="DG17"||addressValid)){Text("Übernehmen")}},dismissButton={TextButton(onDismiss){Text("Abbrechen")}})
}

internal fun eidBackStart(type:String)=if(type=="UB")"DG9" else "DG17"
internal fun eidGroupOrder(type:String):List<String> {
    val front=if(type=="UB")listOf(5,4,8,3,7,13,1,11,10,14) else listOf(5,4,8,9,10,3,7,13,1,11,14)
    val back=if(type=="UB")listOf(9,15,2,17,18,6) else listOf(17,15,2,6,18)
    return (front+back+(if(residenceDocument(type))listOf(19,20)else emptyList())+listOf(12,21,22)).map{"DG$it"}
}
