package de.phoenix.simulator

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import org.json.JSONObject

@Composable internal fun ProfileList(profiles:List<Profile>,onSelect:(String)->Unit,onCreate:()->Unit) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Profile",fontSize=34.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1.2).sp,color=Navy)
            Text("Lokale Testprofile",fontSize=15.sp,color=Muted)
        }
        Text("Dokumenttyp auswählen",fontSize=13.sp,color=Muted,modifier=Modifier.padding(top=12.dp,bottom=2.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            listOf("epassport","eid").forEach { kind ->
                val count=profiles.count{it.kind==kind}
                Box(Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(if(kind=="eid")listOf(Color(0xfff0f2ff),Color(0xffe6ebff))else listOf(Color(0xffe4eaff),Color(0xffd8e1ff)))).clickable{onSelect(kind)}) {
                    Art(kind,Modifier.align(Alignment.CenterEnd).offset(x=12.dp,y=8.dp).size(160.dp))
                    Column(Modifier.fillMaxSize().padding(Inset),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(if(kind=="eid")"eID" else "ePassport",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.8).sp,color=Navy)
                        Text(if(count==1)"1 Profil vorhanden" else "$count Profile vorhanden",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Navy,modifier=Modifier.fillMaxWidth(.72f))
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(if(count>0)"Profile verfügbar" else "Noch kein Profil",fontSize=10.sp,color=Muted,modifier=Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha=.35f)).border(1.dp,Color(0xffb8c8ef),RoundedCornerShape(6.dp)).padding(horizontal=9.dp,vertical=4.dp))
                        }
                    }
                    if(kind!="eid")Glyph("chevron",Navy,Modifier.align(Alignment.CenterEnd).padding(end=14.dp).size(22.dp))
                }
            }
        }
        Action("Neues Profil",false,onClick=onCreate)
    }
}

@Composable internal fun ProfileSelection(profiles:List<Profile>,kind:String,onSelect:(Profile)->Unit,onCreate:()->Unit) {
    val label=if(kind=="eid")"eID" else "ePassport"
    val matching=profiles.filter{it.kind==kind}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(Space)) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("$label-Profile",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.9).sp,color=Navy)
            Text("${matching.size} ${if(matching.size==1)"Profil" else "Profile"} · Lokal auf diesem Gerät",fontSize=13.sp,color=Muted)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=8.dp)) {
            if(matching.isEmpty())Text("Noch kein $label-Profil vorhanden.",fontSize=14.sp,color=Muted,modifier=Modifier.padding(vertical=24.dp))
            matching.forEach { profile ->
                Row(Modifier.fillMaxWidth().clickable{onSelect(profile)}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(52.dp).background(if(kind=="eid")Color(0xffe9efdf)else Color(0xffe5eaff),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){if(kind=="epassport")Art(kind,Modifier.size(46.dp).offset(x=(-4).dp))else Glyph(kind,Color(0xff4c6631),Modifier.size(27.dp))}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(profile.name,fontSize=15.sp,fontWeight=FontWeight.Bold,color=Navy,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("$label · Testprofil",fontSize=12.sp,color=Muted)
                    }
                    Glyph("chevron",Muted,Modifier.size(20.dp))
                }
                HorizontalDivider(color=Border.copy(alpha=.7f))
            }
        }
        Action("Neues $label-Profil",false,onClick=onCreate)
    }
}

@Composable internal fun About(components:JSONObject,onCopy:()->Unit) {
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
        Action("Versionsinfos kopieren",false,onClick=onCopy)
    }
    }
}

@Composable internal fun Settings(onLegal:(String)->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Verbindungen",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Navy)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pale).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Glyph("cloud",Navy,Modifier.size(30.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Cloud-Anbindung",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Navy,modifier=Modifier.weight(1f))
                Text("Geplant",fontSize=10.sp,color=Blue,modifier=Modifier.background(Color(0xffdce2ff),RoundedCornerShape(12.dp)).padding(horizontal=11.dp,vertical=4.dp))
            }
            Text("Noch nicht implementiert",fontSize=13.sp,color=Muted)
            }
        }
        Text("Simulation lokal auf dem Smartphone",fontSize=12.sp,color=Muted)
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

@Composable internal fun Editor(profile:Profile,onImport:()->Unit,onSaveName:(String)->Unit,onEditGroup:(String)->Unit,onSimulate:()->Unit) {
    var groups by remember(profile.id){mutableStateOf(false)}
    var name by remember(profile.id){mutableStateOf(profile.name)}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(profile.name,fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.7).sp,color=Navy)
            Text("${profile.label()} · Testprofil",fontSize=13.sp,color=Muted)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pale).padding(3.dp)) {
            listOf("Einstellungen","Datengruppen").forEachIndexed { index,label ->
                val active=groups==(index==1)
                Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if(active)Blue else Color.Transparent).clickable{groups=index==1}.padding(vertical=12.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=12.sp,color=if(active)Color.White else Muted)}
            }
        }
        Action("Profil importieren",false,onClick=onImport)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(groups) {
                profile.groups.keys().asSequence().sortedBy{it.removePrefix("DG").toInt()}.forEach { key ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if(key=="DG1")Pale else Color.White).border(1.dp,Border,RoundedCornerShape(10.dp)).clickable{onEditGroup(key)}.padding(horizontal=15.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                        val description=when(key){"DG1"->if(profile.kind=="epassport")"Personendaten & MRZ" else "Dokumenttyp";"DG2"->if(profile.kind=="epassport")"Gesichtsbild" else "Ausstellender Staat";"DG4"->"Vornamen";"DG5"->"Familienname";"DG8"->"Geburtsdatum";"DG14"->"Sicherheitsinformationen";else->"Inhalt bearbeiten"}
                        Column(Modifier.weight(1f)) {Text(key,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=Navy);Text(description,fontSize=11.sp,color=Muted)}
                        Glyph("edit",Blue,Modifier.size(17.dp));Spacer(Modifier.width(14.dp));Glyph("chevron",Muted,Modifier.size(18.dp))
                    }
                }
                Action("Datengruppe hinzufügen",false,onClick={onEditGroup("")})
            } else {
                OutlinedTextField(name,{name=it.take(120)},label={Text("Profilname")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pale).padding(16.dp)) {Text("Dokumenttyp",fontSize=13.sp,color=Muted,modifier=Modifier.weight(1f));Text(profile.label(),fontSize=14.sp,color=Navy)}
                Text("Synthetische Daten · lokal gespeichert",fontSize=11.sp,color=Muted)
            }
        }
        if(!groups)Action("Änderungen speichern",onClick={onSaveName(name)})
        Action("Simulation starten",primary=groups,onClick=onSimulate)
    }
}

@Composable internal fun Simulation(profile:Profile?,status:String,onStart:()->Unit,onStop:()->Unit) {
    val active=status=="Bereit für Lesegerät"&&CardService.active()
    val starting=status=="Simulation wird gestartet …"
    val stopping=status=="Simulation wird beendet …"
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val artHeight=(maxHeight-470.dp).coerceIn(96.dp,160.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(Space),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("NFC-Simulation",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.8).sp,color=Navy,modifier=Modifier.fillMaxWidth())
        Art("nfc",Modifier.fillMaxWidth().height(artHeight))
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(if(active)"Bereit zum Verbinden" else "Bereit zum Starten",fontSize=23.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.6).sp,color=Navy,textAlign=TextAlign.Center)
            Text("Smartphone an das Lesegerät halten",fontSize=12.sp,color=Muted,textAlign=TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pale).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(active)"AKTIVES PROFIL" else "AUSGEWÄHLTES PROFIL",fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=Blue)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Color(0xffdfe5ff),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){if(profile?.kind=="epassport")Art("epassport",Modifier.size(40.dp).offset(x=(-3).dp))else Glyph(profile?.kind?:"Profile",Blue,Modifier.size(26.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(profile?.name?:"Kein Profil ausgewählt",fontSize=14.sp,fontWeight=FontWeight.Bold,color=Navy)
                    Text("${profile?.label()?:"Testprofil"} · Testprofil",fontSize=12.sp,color=Muted)
                }
            }
            HorizontalDivider(color=Border)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Glyph("wifi",Muted,Modifier.size(18.dp));Text(status,fontSize=11.sp,color=Muted)
            }
        }
        if(active)Action("Simulation beenden",false,onClick=onStop) else Action(if(starting)"Wird gestartet …" else if(stopping)"Wird beendet …" else "Simulation starten",enabled=!starting&&!stopping,onClick=onStart)
        Text("NFC-Kompatibilität geräteabhängig",fontSize=10.sp,color=Muted)
    }
    }
}
