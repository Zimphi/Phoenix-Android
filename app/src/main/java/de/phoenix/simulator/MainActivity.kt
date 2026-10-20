package de.phoenix.simulator

import android.content.*
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(typography=PhoenixTypography,colorScheme=lightColorScheme(primary=Blue,onPrimary=androidx.compose.ui.graphics.Color.White,background=Page,surface=Page,onSurface=Navy)) { ProvideTextStyle(TextStyle(fontFamily=PhoenixFont,fontSize=14.sp,lineHeight=TextUnit.Unspecified,letterSpacing=0.sp)) { Phoenix() } } }
    }
    override fun onResume(){super.onResume();CardService.foreground(true);val adapter=NfcAdapter.getDefaultAdapter(this);if(adapter!=null&&packageManager.hasSystemFeature("android.hardware.nfc.hce"))CardEmulation.getInstance(adapter).setPreferredService(this,ComponentName(this,CardService::class.java))}
    override fun onPause(){CardService.foreground(false);NfcAdapter.getDefaultAdapter(this)?.let{if(packageManager.hasSystemFeature("android.hardware.nfc.hce"))CardEmulation.getInstance(it).unsetPreferredService(this)};super.onPause()}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun Phoenix() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val storeResult=remember{runCatching{ProfileStore(context)}}
    val store=storeResult.getOrNull()
    var page by remember{mutableStateOf("Profile")}
    var selected by remember{mutableStateOf<Profile?>(null)}
    var selectedKind by remember{mutableStateOf("eid")}
    var revision by remember{mutableIntStateOf(0)}
    var message by remember{mutableStateOf<String?>(null)}
    var legal by remember{mutableStateOf<String?>(null)}
    var creating by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<String?>(null)}
    var choosingGroup by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("Simulation nicht aktiv")}
    val coreReady=remember{CompletableDeferred<Unit>()}
    LaunchedEffect(page,selected?.id){if(page!="Simulation"){CardService.deactivate();if(status=="Bereit für Lesegerät")status="Simulation beendet"}}
    LaunchedEffect(Unit) {
        val initialization=withContext(Dispatchers.IO){
            runCatching {
                check(NativeCore.available){"Native Core-Bibliothek fehlt"}
                val proof=LicenseKey.prove(context.applicationContext,NativeCore.bindingChallenge())
                NativeCore.initialize(context.applicationContext,proof.publicKey,proof.signature)
            }
        }
        initialization.fold({coreReady.complete(Unit)},{coreReady.completeExceptionally(it)})
        if(initialization.isSuccess)withContext(Dispatchers.IO){runCatching{TrialActivation.activate()}.onFailure{android.util.Log.w("Phoenix","Hintergrundinitialisierung noch nicht verfügbar")}}
    }
    val clipboard=LocalClipboardManager.current
    val components=remember{JSONObject(context.assets.open("components.lock.json").use{ProfileStore.read(it)})}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null&&store!=null)scope.launch{
            val result=withContext(Dispatchers.IO){runCatching{context.contentResolver.openInputStream(uri).use{ProfileStore.read(it)}}}
            result.onSuccess{raw->runCatching{store.importDraft(raw)}.onSuccess{selected=it;selectedKind=it.kind;revision++;page="Editor"}.onFailure{message=it.message}}.onFailure{message="Import fehlgeschlagen: ${it.message}"}
        }
    }
    if(store==null){Box(Modifier.fillMaxSize().background(Page).padding(24.dp)){Text("Profilspeicher nicht lesbar. Vorhandene Daten bleiben erhalten.",color=Navy)};return}
    fun createProfile(kind:String) {
        if(store.profiles.size>=100){message="Maximal 100 Profile";return}
        val draft=Profile.defaults().getJSONObject(if(kind=="eid")1 else 0)
            .put("id",java.util.UUID.randomUUID().toString()).put("name","Neues ${if(kind=="eid")"eID" else "ePassport"}-Profil")
        val profile=Profile.parse(draft)
        store.profiles.add(profile)
        runCatching{store.save()}.onSuccess{selected=profile;selectedKind=kind;revision++;page="Editor"}.onFailure{store.profiles.remove(profile);message=it.message}
        creating=false
    }
    fun startSimulation() {
        page="Simulation"
        val profile=selected
        val adapter=NfcAdapter.getDefaultAdapter(context)
        when {
            status in listOf("Simulation wird gestartet …","Simulation wird beendet …")->Unit
            profile==null->message="Zuerst ein Profil auswählen"
            adapter==null||!adapter.isEnabled->message="NFC ist nicht eingeschaltet"
            !context.packageManager.hasSystemFeature("android.hardware.nfc.hce")->message="Keine NFC-Kartenemulation verfügbar"
            else->{
                val session=CardService.beginStart()
                val draft=profile.json()
                status="Simulation wird gestartet …"
                scope.launch {
                    val result=withContext(Dispatchers.IO){runCatching{
                        coreReady.await()
                        TrialActivation.activate()
                        NativeCore.call(JSONObject().put("op","mount_draft").put("draft",draft))
                    }}
                    result.onSuccess {
                        status=if(page=="Simulation"&&selected?.id==profile.id&&CardService.activate(session))"Bereit für Lesegerät" else "Simulation beendet"
                    }.onFailure {CardService.deactivate();status="Simulation nicht verfügbar";message=it.message}
                }
            }
        }
    }
    val active=if(page in listOf("Editor","Profile","Profilliste"))"Profile" else if(page=="Simulation")"Simulation" else "Über"
    Column(Modifier.fillMaxSize().background(Page).statusBarsPadding().semantics{testTagsAsResourceId=true}) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=Inset),verticalAlignment=Alignment.CenterVertically) {
            if(page=="Profile"||page=="Simulation") {
                Brand(Modifier.size(34.dp,42.dp));Spacer(Modifier.width(8.dp));Text("Phoenix",fontSize=20.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.7).sp,color=Navy,modifier=Modifier.weight(1f))
            }else{
                if(page in listOf("Editor","Einstellungen","Profilliste"))IconButton({page=when(page){"Editor"->"Profilliste";"Profilliste"->"Profile";else->"Über"}},Modifier.semantics{contentDescription="Zurück"}){Glyph("back",Navy)}
                if(page=="Editor"||page=="Profilliste") {Spacer(Modifier.weight(1f));Brand(Modifier.size(30.dp,38.dp))}
                else Text(page,fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.6).sp,color=Navy,modifier=Modifier.weight(1f))
            }
            if(page !in listOf("Einstellungen","Editor","Profilliste"))IconButton({page="Einstellungen"},Modifier.semantics{contentDescription="Einstellungen"}){Glyph("gear",Navy)}
        }
        Box(Modifier.weight(1f).padding(horizontal=Inset).padding(top=8.dp,bottom=16.dp)) {
            val visibleProfiles=remember(revision){store.profiles.toList()}
            key(Unit) {
                when(page) {
                    "Profile"->ProfileList(visibleProfiles,{selectedKind=it;selected=null;page="Profilliste"},{creating=true})
                    "Profilliste"->ProfileSelection(visibleProfiles,selectedKind,{selected=it;page="Editor"},{createProfile(selectedKind)})
                    "Editor"->selected?.let{profile->Editor(profile,{importer.launch(arrayOf("application/json"))},{name->
                        val value=name.trim()
                        if(value.isEmpty()||value.length>120)message="Profilname muss 1 bis 120 Zeichen enthalten"
                        else {val old=profile.name;profile.name=value;runCatching{store.save()}.onSuccess{revision++;Toast.makeText(context,"Profilentwurf gespeichert",Toast.LENGTH_SHORT).show()}.onFailure{profile.name=old;message=it.message}}
                    },{group->if(profile.id=="eid-persosim03")message="Signiertes PersoSim-Testprofil: Datengruppen sind unveränderlich. Eigene Daten bitte in einem separaten Profil bearbeiten." else if(group.isEmpty())choosingGroup=true else editing=group})}
                    "Über"->About(components){clipboard.setText(androidx.compose.ui.text.AnnotatedString("Phoenix Android ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n$components"));Toast.makeText(context,"Versionsinfos kopiert",Toast.LENGTH_SHORT).show()}
                    "Einstellungen"->Settings{legal=it}
                    "Simulation"->Simulation(selected,status,::startSimulation,{
                        CardService.deactivate();status="Simulation wird beendet …"
                        scope.launch {
                            withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","close"))}}
                            status="Simulation beendet"
                        }
                    })
                }
            }
        }
        Navigation(active){page=it}
    }
    if(message!=null)AlertDialog(onDismissRequest={message=null},title={Text("Phoenix")},text={Text(message!!)},confirmButton={TextButton({message=null}){Text("OK")}})
    if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("Neues Testprofil")},text={Column{
        listOf("epassport" to "ePassport","eid" to "eID").forEach{(kind,label)->Action(label,false){createProfile(kind)}}
    }},confirmButton={},dismissButton={TextButton({creating=false}){Text("Abbrechen")}})
    if(choosingGroup)AlertDialog(onDismissRequest={choosingGroup=false},title={Text("Datengruppe auswählen")},text={Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())){
        (1..if(selected?.kind=="eid")22 else 16).forEach{n->TextButton({editing="DG$n";choosingGroup=false},Modifier.fillMaxWidth()){Text("DG$n")}}
    }},confirmButton={TextButton({choosingGroup=false}){Text("Abbrechen")}})
    editing?.let{group->
        var value by remember(group){mutableStateOf(selected!!.groups.optString(group,""))}
        AlertDialog(onDismissRequest={editing=null},title={Text("$group bearbeiten")},text={OutlinedTextField(value,{value=it.take(65536)},modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=260.dp),label={Text("$group Inhalt")})},confirmButton={TextButton({
            val profile=selected!!;val old=profile.groups
            profile.groups=JSONObject(old.toString()).put(group,value)
            runCatching{store.save()}.onSuccess{revision++;editing=null}.onFailure{profile.groups=old;message=it.message}
        }){Text("Speichern")}},dismissButton={TextButton({editing=null},Modifier.semantics{testTagsAsResourceId=true}.testTag("cancel-group")){Text("Abbrechen")}})
    }
    legal?.let{title->
        val text=remember(title){when(title){
            "Impressum"->"Phoenix Android\nhttps://github.com/Zimphi/Phoenix-Android\n\nVerantwortlichen- und Kontaktangaben sind vor Veröffentlichung einer App-Distribution zu ergänzen."
            "Datenschutz"->"Diese Entwicklungsversion speichert synthetische Profilentwürfe lokal auf dem Gerät. Keine Analyse- oder Werbedienste.\n\nDie Hintergrundaktivierung verwendet den Phoenix Lizenz-Testrelay über HTTPS. Übermittelt werden pseudonyme Geräte- und Anfragehashes sowie die ausgewählten Core-Produkte. Der private Geräteschlüssel bleibt im Android Keystore. Die native Lizenzprüfung benötigt zusätzlich authentifizierte Zeit von Netnod bzw. Cloudflare. Profile und NFC-APDUs werden dafür nicht übertragen. Die Cloud-Simulation ist nicht umgesetzt."
            else->context.assets.open("THIRD_PARTY_NOTICES.txt").use{ProfileStore.read(it)}+"\n\n"+context.assets.open("LICENSE.txt").use{ProfileStore.read(it)}
        }}
        AlertDialog(onDismissRequest={legal=null},title={Text(title)},text={Text(text,Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()))},confirmButton={TextButton({legal=null},Modifier.semantics{testTagsAsResourceId=true}.testTag("close-legal")){Text("Schließen")}})
    }
}
