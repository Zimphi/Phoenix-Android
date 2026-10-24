package de.phoenix.simulator

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import org.json.JSONObject

@Composable internal fun ProfileList(profiles:List<Profile>,onSelect:(String)->Unit) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Profile",fontSize=34.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1.2).sp,color=Navy)
            Text("Lokale Testprofile",fontSize=15.sp,color=Muted)
        }
        Text("Dokumenttyp auswählen",fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=12.dp,bottom=2.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf("epassport","eid").forEach { kind ->
                DocumentTypeCard(kind,profiles.count{it.kind==kind}){onSelect(kind)}
            }
        }
    }
}

@Composable private fun DocumentTypeCard(kind:String,count:Int,onSelect:()->Unit) {
                Box(Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(if(kind=="eid")listOf(Color(0xfff0f2ff),Color(0xffe6ebff))else listOf(Color(0xffe4eaff),Color(0xffd8e1ff)))).clickable(onClick=onSelect)) {
                    Art(kind,Modifier.align(Alignment.CenterEnd).offset(x=12.dp,y=8.dp).size(160.dp))
                    Column(Modifier.fillMaxSize().padding(Inset),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(if(kind=="eid")"eID" else "ePassport",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.8).sp,color=Navy)
                        Text(if(count==1)"1 Profil vorhanden" else "$count Profile vorhanden",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Navy,modifier=Modifier.fillMaxWidth(.72f))
                        Spacer(Modifier.weight(1f))
                    }
                    if(kind!="eid")Glyph("chevron",Navy,Modifier.align(Alignment.CenterEnd).padding(end=14.dp).size(22.dp))
                }
}

@Composable internal fun ProfileSelection(profiles:List<Profile>,kind:String,onSelect:(Profile)->Unit,onCreate:()->Unit) {
    val label=if(kind=="eid")"eID" else "ePassport"
    var search by remember(kind){mutableStateOf("")}
    val matching=profiles.filter{it.kind==kind&&(search.isBlank()||it.name.contains(search,true)||it.documentNumber.contains(search,true))}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(Space)) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("$label-Profile",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.9).sp,color=Navy)
            Text("${matching.size} ${if(matching.size==1)"Profil" else "Profile"} · Lokal auf diesem Gerät",fontSize=13.sp,color=Muted)
        }
        if(kind=="eid")OutlinedTextField(search,{search=it.take(120)},label={Text("Nachname oder Seriennummer")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("profile-search"),shape=RoundedCornerShape(12.dp))
        Column(Modifier.weight(1f).testTag("profile-list").verticalScroll(rememberScrollState()).padding(top=8.dp)) {
            if(matching.isEmpty())Text("Noch kein $label-Profil vorhanden.",fontSize=14.sp,color=Muted,modifier=Modifier.padding(vertical=24.dp))
            matching.forEach { profile ->
                Row(Modifier.fillMaxWidth().testTag("profile-${profile.id}").clickable{onSelect(profile)}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(profile.kind=="epassport")Art("epassport",Modifier.size(60.dp,66.dp)) else DocumentPreview(profile,modifier=Modifier.size(78.dp,52.dp),compact=true)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(profile.name,modifier=if(profile.persosimSource.isNotEmpty())Modifier.testTag("profile-source-${profile.persosimSource}")else Modifier,fontSize=15.sp,fontWeight=FontWeight.Bold,color=Navy,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(profile.kind=="eid")Text(listOf(profile.groupText("DG4"),"Seriennummer ${profile.documentNumber}").filter{it.isNotBlank()}.joinToString(" · "),fontSize=11.sp,color=Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(if(!profile.simulatable)"$label · Nicht simulierbar" else if(profile.expired())"$label · Abgelaufen · Negativtest" else if(profile.signedFixture)"$label · Signiert · AusweisApp-Test" else "$label · Testprofil",fontSize=12.sp,color=Muted)
                    }
                    Glyph("chevron",Muted,Modifier.size(20.dp))
                }
                HorizontalDivider(color=Border.copy(alpha=.7f))
            }
        }
        Action("Neues $label-Profil",false,onClick=onCreate)
    }
}

@Composable internal fun About(components:JSONObject) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val logoHeight=(maxHeight-420.dp).coerceIn(64.dp,132.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Brand(Modifier.size(logoHeight*.87f,logoHeight))
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text("Phoenix",fontSize=35.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1.3).sp,color=Navy)
            Text("eID & ePassport Simulator",fontSize=14.sp,color=Muted)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pale).padding(horizontal=17.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Versionen",fontSize=17.sp,fontWeight=FontWeight.Bold,color=Navy)
            listOf("eid" to "eID Core","epassport" to "ePassport Core").forEach { (id,label) ->
                val core=components.getJSONObject(id)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Glyph("layers",Navy,Modifier.padding(end=12.dp).size(23.dp))
                    Column(Modifier.weight(1f)) {Text(label,fontSize=14.sp,color=Navy);Text(core.getString("commit").take(12),fontSize=9.sp,color=Muted)}
                    Text(core.getString("version"),fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Navy)
                }
            }
            Text(if(NativeCore.available)"Native Buildreferenzen" else "Native Einbindung ausstehend",fontSize=10.sp,color=Muted)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Pale).padding(15.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("App-Version",fontSize=13.sp,color=Navy,modifier=Modifier.weight(1f))
            Column(horizontalAlignment=Alignment.End) {
                Text(BuildConfig.VERSION_NAME,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Navy)
                Text("Build ${BuildConfig.VERSION_CODE}",fontSize=10.sp,color=Muted)
            }
        }
    }
    }
}

@Composable internal fun Settings(onLegal:(String)->Unit,onReader:()->Unit,onPki:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Verbindungen",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Navy)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pale).clickable(onClick=onPki).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Glyph("cloud",Blue,Modifier.size(30.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("Cloud-Anbindung",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Navy)
                Text("Benutzerkonto und zentrale PKI",fontSize=12.sp,color=Muted)
            }
            Glyph("chevron",Muted,Modifier.size(19.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pale).clickable(onClick=onReader).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Glyph("wifi",Blue,Modifier.size(30.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("IFD-Schnittstelle",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Navy)
                Text("Smartphone als Kartenleser · Kopplung",fontSize=12.sp,color=Muted)
            }
            Glyph("chevron",Muted,Modifier.size(19.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Rechtliches",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Navy)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Pale)) {
            listOf("Impressum","Datenschutz","Lizenzen & Drittanbieter").forEachIndexed { index,label ->
                Row(Modifier.fillMaxWidth().clickable{onLegal(label)}.padding(horizontal=16.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Glyph(listOf("file","privacy","legal")[index],Navy,Modifier.size(23.dp))
                    Text(label,fontSize=15.sp,color=Navy,modifier=Modifier.weight(1f));Glyph("chevron",Muted,Modifier.size(19.dp))
                }
                if(index<2)HorizontalDivider(Modifier.padding(horizontal=17.dp),color=Border)
            }
        }
    }
}

@Composable internal fun Editor(profile:Profile,revision:Int,cloudVerified:Boolean,onImport:()->Unit,onExport:()->Unit,onSave:(String,JSONObject)->Boolean,onCopy:()->Unit,onEditGroup:(String,String)->Unit,onSimulate:()->Unit,onPortrait:(String)->Boolean,onBack:()->Unit,onDocumentNumber:(String)->Boolean,onReset:()->Unit,onPki:(String)->Unit,onCoreState:(JSONObject)->Unit) {
    var reset by remember(profile){mutableStateOf(false)}
    var groups by remember(profile){mutableStateOf(true)}
    var back by remember(profile){mutableStateOf(false)}
    val fieldScroll=key(profile){rememberScrollState()}
    var backOffset by remember(profile){mutableFloatStateOf(Float.POSITIVE_INFINITY)}
    var fieldHeight by remember{mutableIntStateOf(0)}
    LaunchedEffect(fieldScroll.value,groups,backOffset,fieldHeight){if(groups&&profile.kind=="eid")back=fieldScroll.value+fieldHeight*.5f>=backOffset}

    var face by remember(profile){mutableStateOf(false)}
    var portrait by remember(profile){mutableStateOf(profile.portraitSex)}
    var documentNumber by remember(profile){mutableStateOf(profile.documentNumber)}
    var can by remember(profile){mutableStateOf(profile.settings.getString("can"))}
    var pin by remember(profile){mutableStateOf(profile.settings.getString("pin"))}
    var puk by remember(profile){mutableStateOf(profile.settings.getString("puk"))}
    var retries by remember(profile){mutableStateOf(profile.settings.optInt("pin_retries",3).toString())}
    var uses by remember(profile){mutableStateOf(profile.settings.optInt("puk_uses",10).toString())}
    var transport by remember(profile){mutableStateOf(profile.settings.optBoolean("transport_pin",false))}
    var eidActive by remember(profile){mutableStateOf(profile.settings.optBoolean("eid_active",true))}
    LaunchedEffect(profile.settings.toString()) {
        can=profile.settings.getString("can");pin=profile.settings.getString("pin");puk=profile.settings.getString("puk")
        retries=profile.settings.optInt("pin_retries",3).toString();uses=profile.settings.optInt("puk_uses",10).toString()
        transport=profile.settings.optBoolean("transport_pin",false);eidActive=profile.settings.optBoolean("eid_active",true)
    }
    val valid=profile.passportCatalog||(can.matches(Regex("[0-9]{6}"))&&pin.matches(Regex(if(transport)"[0-9]{5}" else "[0-9]{6}"))&&puk.matches(Regex("[0-9]{10}"))&&retries.toIntOrNull() in 0..3&&uses.toIntOrNull() in 0..10)
    val save={onSave(profile.name,JSONObject().put("can",can).put("pin",pin).put("puk",puk).put("pin_retries",retries.toInt()).put("puk_uses",uses.toInt()).put("transport_pin",transport).put("eid_active",eidActive))}
    var bsiCompatible by remember(profile){mutableStateOf(profile.signedFixture)}
    var displayedGroups by remember(profile){mutableStateOf(profile.groups)}
    var displayedMrz by remember(profile){mutableStateOf("")}
    var displayedPassportDetails by remember(profile){mutableStateOf(JSONObject())}
    var displayedPortrait by remember(profile){mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)}
    LaunchedEffect(profile.id,revision,profile.groups.toString()) {
        val result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){runCatching{
            val description=NativeCore.call(JSONObject().put("op","describe_draft").put("draft",profile.coreDraft()))
            val image=description.optString("portrait").takeIf{it.isNotBlank()&&it!="null"}?.let{decodePortrait(it)}
            description to image
        }.getOrNull()}
        val description=result?.first
        bsiCompatible=description?.optString("bsi_source")?.let{it.isNotBlank()&&it!="null"}?:false
        displayedGroups=description?.optJSONObject("groups")?:profile.groups
        displayedMrz=description?.optString("mrz")?:""
        displayedPassportDetails=description?.optJSONObject("supplementary")?:JSONObject()
        displayedPortrait=result?.second
    }
    LaunchedEffect(can,pin,puk,retries,uses,transport,eidActive) {
        if(valid&&!profile.passportCatalog) {
            val settings=JSONObject().put("can",can).put("pin",pin).put("puk",puk).put("pin_retries",retries.toInt()).put("puk_uses",uses.toInt()).put("transport_pin",transport).put("eid_active",eidActive)
            if(settings.length()!=profile.settings.length()||settings.keys().asSequence().any{key->settings.opt(key)!=profile.settings.opt(key)})onSave(profile.name,settings)
        }
    }
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Personalisierungsdaten zurücksetzen?")},text={Text("Datengruppen, Zugangsdaten und Dokumentdarstellung werden auf den gesicherten Ausgangsstand dieses Profils zurückgesetzt. Änderungen gehen verloren.")},confirmButton={TextButton({reset=false;onReset()}){Text("Zurücksetzen")}},dismissButton={TextButton({reset=false}){Text("Abbrechen")}})
    if(face)AlertDialog(onDismissRequest={face=false},title={Text("DG2 · Gesichtsbild")},text={PassportFace(profile)},confirmButton={TextButton({face=false}){Text("Schließen")}},dismissButton={TextButton({face=false;onEditGroup("DG2",profile.groups.optString("DG2"))}){Text("Bild auswählen")}})
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onBack,Modifier.semantics{contentDescription="Zurück"}){Glyph("back",Navy)}
            Text(profile.name,fontSize=16.sp,fontWeight=FontWeight.ExtraBold,color=Navy,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center,modifier=Modifier.weight(1f))
            IconButton({if(profile.passportCatalog||save())onSimulate()},enabled=valid,modifier=Modifier.semantics{contentDescription="Profil in Simulation übernehmen"}){Box(Modifier.size(28.dp).background(Color(0xffe7eef6),androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center){Glyph("play",Navy,Modifier.size(15.dp))}}
        }
        Box(Modifier.fillMaxWidth().testTag("document-preview-frame").aspectRatio(if(profile.kind=="epassport")720f/550f else 856f/540f)) {
            DocumentPreview(profile,displayedGroups,Modifier.fillMaxSize().clickable(role=Role.Button,onClickLabel=if(back)"Vorderseite anzeigen" else if(profile.kind=="epassport")"Zweite Passseite anzeigen" else "Rückseite anzeigen"){back=!back},mrz=displayedMrz,portraitOverride=portrait,back=back,passportImage=displayedPortrait,passportDetails=displayedPassportDetails)
        }
        if(profile.kind=="eid") {
            val warnings=buildList {
                if(profile.expired())add("Abgelaufenes Testdokument – Negativtest")
                if(!eidActive)add("eID deaktiviert")
                if(transport)add("Transport-PIN aktiv – persönliche PIN setzen")
                if(retries.toIntOrNull()==0)add("PIN gesperrt")
                else if(retries.toIntOrNull() in 1..2)add("Noch $retries PIN-Versuche")
                if(uses.toIntOrNull()==0)add("PUK verbraucht – Entsperren nicht mehr möglich")
            }
            if(warnings.isNotEmpty())Row(Modifier.fillMaxWidth().testTag("credential-warning").padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("⚠",color=Color(0xffb58100),fontSize=20.sp)
                Text(warnings.joinToString(" · "),color=Color(0xff8a6300),fontSize=12.sp,modifier=Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Datengruppen","Einstellungen").forEachIndexed { index,label ->
                val active=groups==(index==0)
                Column(Modifier.weight(1f).clickable{groups=index==0}) {
                    Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        Glyph(if(index==0)"layers" else "gear",if(active)Navy else Muted,Modifier.size(21.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label,fontSize=13.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium,color=if(active)Navy else Muted)
                    }
                    HorizontalDivider(thickness=if(active)2.dp else 1.dp,color=if(active)Navy else Border)
                }
            }
        }
        if(!profile.simulatable)Text("Nicht simulierbar: ${profile.simulationError}",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
        Column(Modifier.weight(1f).testTag("editor-fields").onSizeChanged{fieldHeight=it.height}.verticalScroll(fieldScroll),verticalArrangement=Arrangement.spacedBy(if(groups)0.dp else 10.dp)) {
            if(groups) {
                (if(profile.kind=="eid")eidGroupOrder(profile.documentType()) else profile.groups.keys().asSequence().sortedBy{it.removePrefix("DG").toInt()}.toList()).forEach { key ->
                    val disabled=profile.kind=="eid"&&key in listOf("DG21","DG22")
                    Row(Modifier.fillMaxWidth().testTag("group-$key").then(if(profile.kind=="eid"&&key==eidBackStart(profile.documentType()))Modifier.onGloballyPositioned{backOffset=it.positionInParent().y}else Modifier).heightIn(min=68.dp).clickable(enabled=!disabled){if(profile.kind=="epassport"&&key=="DG2")face=true else onEditGroup(key,displayedGroups.optString(key))}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(profile.kind=="epassport"&&key=="DG2"&&displayedPortrait!=null)Image(displayedPortrait!!,contentDescription=null,modifier=Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                        else Box(Modifier.size(40.dp).background(Color(0xffeef2f6),androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center){Glyph(if(profile.kind=="eid")when(key){"DG4","DG5"->"person";"DG8"->"calendar";"DG9"->"place";else->"file"}else "file",Navy,Modifier.size(21.dp))}
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(if(profile.kind=="epassport"&&key=="DG1")listOf(profileField(profile,displayedGroups,"given_names"),profileField(profile,displayedGroups,"surname")).joinToString(" ") else if(profile.kind=="epassport"&&key=="DG2")"Gesichtsbild ansehen" else if(profile.kind=="eid"&&key in listOf("DG3","DG8","DG15"))passportDate(groupValue(profile.kind,key,displayedGroups.optString(key))) else groupValue(profile.kind,key,displayedGroups.optString(key)),fontSize=14.sp,fontWeight=FontWeight.Bold,color=if(disabled)Muted.copy(alpha=.45f)else Navy,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                                Text(key+" ·",fontSize=11.sp,color=Muted)
                                Text(groupDescription(profile.kind,key),fontSize=11.sp,color=Muted)
                            }
                        }
                        Spacer(Modifier.width(10.dp));Glyph("chevron",Muted,Modifier.size(18.dp))
                    }
                    HorizontalDivider(color=Border)
                }
            } else if(profile.passportCatalog) {
                ProfileSettingsSection("Zugangsdaten","privacy","credential-settings") {
                Text("Originales Simulatorprofil. Zugangsdaten und signierte Datengruppen bleiben unverändert.",fontSize=13.sp,color=Muted)
                Text("CAN: $can\nPIN: ${pin.ifEmpty{"Nicht hinterlegt"}}\nPUK: ${puk.ifEmpty{"Nicht hinterlegt"}}",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Navy)
                }
            } else {
                ProfileSettingsSection("Zugangsdaten","privacy","credential-settings") {
                if(profile.kind=="eid")OutlinedTextField(documentNumber,{input->
                    documentNumber=input.uppercase(java.util.Locale.ROOT).filter{it in 'A'..'Z'||it in '0'..'9'}.take(9)
                    if(documentNumber.isNotEmpty()&&!onDocumentNumber(documentNumber))documentNumber=profile.documentNumber
                },label={Text("Fiktive Seriennummer")},supportingText={Text("Eindeutige Zuordnung dieses Profils; wird auch in der MRZ verwendet.")},isError=documentNumber.isEmpty(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("profile-serial"))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("CAN",can,{v:String->can=v}),Triple(if(transport)"Transport-PIN (5 Ziffern)" else "PIN",pin,{v:String->pin=v})).forEach { (label,value,change) ->
                        OutlinedTextField(value,{change(it.filter(Char::isDigit).take(if(label.startsWith("Transport-PIN"))5 else 6))},label={Text(label)},singleLine=true,modifier=Modifier.weight(1f).semantics{contentDescription=label},shape=RoundedCornerShape(12.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    }
                }
                OutlinedTextField(puk,{puk=it.filter(Char::isDigit).take(10)},label={Text("PUK")},singleLine=true,modifier=Modifier.fillMaxWidth().semantics{contentDescription="PUK"},shape=RoundedCornerShape(12.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                if(profile.kind=="eid") {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(retries,{retries=it.filter(Char::isDigit).take(1)},label={Text("PIN-Versuche (0–3)")},singleLine=true,modifier=Modifier.weight(1f).semantics{contentDescription="PIN-Versuche (0–3)"},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                        OutlinedTextField(uses,{uses=it.filter(Char::isDigit).take(2)},label={Text("PUK-Nutzungen (0–10)")},singleLine=true,modifier=Modifier.weight(1f).semantics{contentDescription="PUK-Nutzungen (0–10)"},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Transport-PIN-Modus",fontSize=14.sp,color=Navy,modifier=Modifier.weight(1f))
                        Switch(transport,{transport=it},modifier=Modifier.semantics{contentDescription="Transport-PIN-Modus"})
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("eID deaktivieren",fontSize=14.sp,color=Navy,modifier=Modifier.weight(1f))
                        Switch(!eidActive,{eidActive=!it},modifier=Modifier.semantics{contentDescription="eID deaktivieren"})
                    }
                    Text("Transport-PIN: 5 Ziffern, erst nach PIN-Änderung online nutzbar. Deaktivierte eID: PIN-Zugriff gesperrt; CAN-Verwaltung bleibt möglich.",fontSize=11.sp,color=Muted)
                }
                }
            }
            if(!groups) {
                if(profile.kind=="eid")RiSettings(profile,onCoreState)
                ProfilePkiOptions(profile.kind,profile.pkiInfrastructure,bsiCompatible,cloudVerified,onPki)
                ProfileSettingsSection("Personalisierung","gear","personalization-settings") {
                    Action("Personalisierungsdaten zurücksetzen",false,onClick={reset=true})
                    if(profile.signedFixture||profile.passportCatalog)Action("Bearbeitbare Kopie erstellen",false,onClick=onCopy)
                }
                if(profile.kind=="eid") {
                    ProfileSettingsSection("Schattenriss","person","portrait-settings") {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf("" to "Automatisch","M" to "Männlich","F" to "Weiblich").forEach{(value,label)->
                                FilterChip(selected=portrait==value,onClick={if(onPortrait(value))portrait=value},label={Text(label,fontSize=11.sp)},modifier=Modifier.testTag("portrait-${value.ifEmpty{"auto"}}"))
                            }
                        }
                        Text("Nur Dokumentdarstellung.",fontSize=11.sp,color=Muted)
                    }
                }
                ProfileSettingsSection("Profil übertragen","import","profile-transfer-settings") {
                    Action("Profil importieren",false,onClick=onImport)
                    Action("Profil exportieren",false,enabled=valid,onClick=onExport)
                }
            }
        }
        if(!groups&&!valid)Text("Bitte vollständige Zugangsdaten eingeben. Die letzte gültige Einstellung bleibt gespeichert.",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
    }
}

@Composable internal fun Simulation(profile:Profile?,status:String,onStart:()->Unit,onStop:()->Unit,wlan:Boolean,onMode:(Boolean)->Unit,remote:JSONObject,onSelectProfile:()->Unit) {
    val active=status=="Bereit für Lesegerät"&&(if(wlan)remote.optBoolean("running") else CardService.active())
    val starting=status=="Simulation wird gestartet …"
    val stopping=status=="Simulation wird beendet …"
    val loading=status=="Profil wird geladen …"
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val artHeight=(maxHeight-470.dp).coerceIn(96.dp,160.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(Space),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("Simulation",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.8).sp,color=Navy,modifier=Modifier.fillMaxWidth())
        Art("nfc",Modifier.fillMaxWidth().height(artHeight))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf(false to "NFC",true to "IFD-Schnittstelle").forEach{(remoteMode,label)->
                val enabled=!active&&!starting&&!stopping&&(!remoteMode||profile?.kind=="eid")
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if(wlan==remoteMode)Pale else Page).border(1.dp,if(wlan==remoteMode)Blue else Border,RoundedCornerShape(14.dp))
                    .selectable(selected=wlan==remoteMode,enabled=enabled,role=Role.RadioButton,onClick={onMode(remoteMode)}).semantics{contentDescription=label}.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Glyph(if(remoteMode)"ifd" else "contactless",if(enabled)Blue else Muted,Modifier.size(26.dp))
                    Text(label,fontSize=12.sp,color=if(enabled)Navy else Muted,fontWeight=FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.fillMaxWidth().height(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(if(wlan)"Smartphone als Kartenleser" else "Smartphone an das Lesegerät halten",fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pale).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(active)"AKTIVES PROFIL" else "AUSGEWÄHLTES PROFIL",fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=Blue)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled=!active&&!starting&&!stopping,onClick=onSelectProfile).semantics{contentDescription="Simulationsprofil auswählen"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Color(0xffdfe5ff),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){if(profile?.kind=="epassport")Art("epassport",Modifier.size(40.dp).offset(x=(-3).dp))else Glyph(profile?.kind?:"Profile",Blue,Modifier.size(26.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(profile?.name?:"Profil auswählen",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Navy,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${profile?.label()?:"ePassport oder eID"} · Testprofil",fontSize=12.sp,color=Muted)
                }
                Glyph("chevron",Muted,Modifier.size(20.dp))
            }
            HorizontalDivider(color=Border)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Glyph("wifi",Muted,Modifier.size(18.dp));Text(if(wlan&&active) {if(remote.optBoolean("pairing"))"Kopplung mit AusweisApp" else "IFD-Schnittstelle aktiv"}else status,fontSize=11.sp,color=Muted)
            }
        }
        if(profile?.simulatable==false)Text("Nicht simulierbar: ${profile.simulationError}",fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        if(active)Action("Simulation beenden",false,onClick=onStop) else Action(if(starting)"Wird gestartet …" else if(stopping)"Wird beendet …" else "Simulation starten",enabled=!starting&&!stopping&&!loading&&profile?.simulatable==true,onClick=onStart)
    }
    }
}

@Composable internal fun ReaderSettings(state:JSONObject,busy:Boolean,onPair:()->Unit,onStop:()->Unit,onForget:()->Unit) {
    val running=state.optBoolean("running")
    val pairing=state.optBoolean("pairing")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Glyph("wifi",Blue,Modifier.size(44.dp))
        Text("Smartphone als Kartenleser",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Navy)
        Text("AusweisApp über das lokale WLAN verbinden",fontSize=13.sp,color=Muted,textAlign=TextAlign.Center)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pale).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(pairing)"Kopplungscode" else if(state.optInt("paired")>0)"AusweisApp gekoppelt" else "Noch nicht gekoppelt",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=Navy)
            if(pairing)Text(state.optString("pairing_code"),fontSize=34.sp,fontWeight=FontWeight.Bold,color=Blue,letterSpacing=7.sp)
            Text(if(pairing)"In der AusweisApp unter Einstellungen → Smartphone als Kartenleser dieses Gerät auswählen und den Code eingeben. Gültig für 5 Minuten." else "${state.optInt("paired")} gekoppelte Geräte",fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
            if(running)Text(RemoteReader.addresses(),fontSize=12.sp,color=Muted)
            if(state.optString("last_error").isNotEmpty())Text(state.optString("last_error"),fontSize=12.sp,color=MaterialTheme.colorScheme.error)
        }
        Text("In der Simulation anschließend das eID-Profil und die IFD-Schnittstelle auswählen und starten.",fontSize=13.sp,color=Muted,textAlign=TextAlign.Center)
        if(running)Action("Kopplung beenden",false,onClick=onStop)
        else Action(if(busy)"Kopplung wird gestartet …" else "Kopplung starten",enabled=!busy,onClick=onPair)
        if(state.optInt("paired")>0&&!busy)Action("Kopplungen entfernen",false,onClick=onForget)
    }
}


@Composable internal fun SimulationProfilePicker(profiles:List<Profile>,onDismiss:()->Unit,onSelect:(Profile)->Unit) {
    var kind by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(kind==null)"Dokumenttyp auswählen" else if(kind=="eid")"eID-Profil auswählen" else "ePassport-Profil auswählen")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(kind==null) {
                listOf("epassport","eid").forEach{value->DocumentTypeCard(value,profiles.count{it.kind==value}){kind=value}}
            } else TextButton({kind=null}){Glyph("back",Blue);Spacer(Modifier.width(8.dp));Text("Dokumenttyp ändern")}
            kind?.let{selectedKind->
                val matching=profiles.filter{it.kind==selectedKind}
                Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())) {
                    if(matching.isEmpty())Text("Noch kein Profil vorhanden.",color=Muted)
                    matching.forEach{profile->
                        Row(Modifier.fillMaxWidth().clickable{onSelect(profile)}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(profile.kind=="epassport")Art("epassport",Modifier.size(60.dp,66.dp)) else DocumentPreview(profile,modifier=Modifier.size(78.dp,52.dp),compact=true)
                            Column(Modifier.weight(1f)){Text(profile.name,color=Navy,fontWeight=FontWeight.SemiBold);if(!profile.simulatable)Text("Nicht simulierbar",fontSize=11.sp,color=Muted)}
                            Glyph("chevron",Muted,Modifier.size(18.dp))
                        }
                        HorizontalDivider(color=Border)
                    }
                }
            }
        }
    },confirmButton={TextButton(onDismiss){Text("Abbrechen")}})
}


@Composable internal fun AccountIdentity(identity:JSONObject,environment:String?) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        AccountAvatar(Modifier.size(96.dp))
        val name=identity.optString("display_name").takeIf{it.isNotBlank()&&it!="null"}
        val username=identity.optString("username").takeIf{it.isNotBlank()&&it!="null"}
        val email=identity.optString("email").takeIf{it.isNotBlank()&&it!="null"}
        Text(name?:username?:"Phoenix-Konto",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Navy,textAlign=TextAlign.Center)
        if(username!=null&&username!=name)Text(username,fontSize=15.sp,color=Navy,textAlign=TextAlign.Center)
        if(email!=null)Text(email,fontSize=14.sp,color=Muted,textAlign=TextAlign.Center)
        Text("Benutzerkennung",fontSize=12.sp,color=Muted)
        androidx.compose.foundation.text.selection.SelectionContainer{Text(identity.optString("account_id").ifBlank{"Wird geladen …"},fontSize=12.sp,color=Navy,textAlign=TextAlign.Center)}
        if(environment!=null)Text(if(environment=="preview")"Preview" else "Produktion",fontSize=12.sp,color=Muted)
    }
}

@Composable internal fun UserAccount(identity:JSONObject,environment:String?,busy:Boolean,onLogout:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text("Benutzerübersicht",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Navy)
        AccountIdentity(identity,environment)
        Action(if(busy)"Wird abgemeldet …" else "Abmelden",false,enabled=!busy&&identity.optString("user_id").isNotBlank(),onClick=onLogout)
    }
}
