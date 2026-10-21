package de.phoenix.simulator

import android.content.*
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        if(android.os.Build.VERSION.SDK_INT>=31)splashScreen.setOnExitAnimationListener{it.remove()}
        setContent { MaterialTheme(typography=PhoenixTypography,colorScheme=lightColorScheme(primary=Blue,onPrimary=androidx.compose.ui.graphics.Color.White,background=Page,surface=Page,onSurface=Navy)) { ProvideTextStyle(TextStyle(fontFamily=PhoenixFont,fontSize=14.sp,lineHeight=TextUnit.Unspecified,letterSpacing=0.sp)) { Phoenix() } } }
    }
    override fun onResume(){super.onResume();CardService.foreground(true);val adapter=NfcAdapter.getDefaultAdapter(this);if(adapter!=null&&packageManager.hasSystemFeature("android.hardware.nfc.hce"))CardEmulation.getInstance(adapter).setPreferredService(this,ComponentName(this,CardService::class.java))}
    override fun onPause(){CardService.foreground(false);RemoteReader.stop();NfcAdapter.getDefaultAdapter(this)?.let{if(packageManager.hasSystemFeature("android.hardware.nfc.hce"))CardEmulation.getInstance(it).unsetPreferredService(this)};super.onPause()}
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun Phoenix() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val account=remember{PkiDevice(context.applicationContext)}
    var identity by remember{mutableStateOf(account.identity())}
    val userId=identity.optString("user_id").takeIf{it.isNotBlank()}
    var cloudVerified by remember{mutableStateOf(false)}
    var accountBusy by remember{mutableStateOf(false)}
    var storeResult by remember{mutableStateOf<Result<ProfileStore>?>(null)}
    val store=storeResult?.getOrNull()
    var page by remember{mutableStateOf("Profile")}
    var selected by remember{mutableStateOf<Profile?>(null)}
    var selectedKind by remember{mutableStateOf("eid")}
    var revision by remember{mutableIntStateOf(0)}
    var message by remember{mutableStateOf<String?>(null)}
    var legal by remember{mutableStateOf<String?>(null)}
    var editing by remember{mutableStateOf<String?>(null)}
    var editingValue by remember{mutableStateOf("")}
    var copyGroup by remember{mutableStateOf<Pair<String,String>?>(null)}
    var status by remember{mutableStateOf("Simulation nicht aktiv")}
    var wlan by remember{mutableStateOf(false)}
    var remoteState by remember{mutableStateOf(JSONObject())}
    var pairBusy by remember{mutableStateOf(false)}
    var forgetPairing by remember{mutableStateOf(false)}
    var simulationPicker by remember{mutableStateOf(false)}
    var startup by remember{mutableStateOf(true)}
    val coreReady=remember{CompletableDeferred<Unit>()}
    LaunchedEffect(page,selected?.id){if(page!="Simulation"){
        CardService.deactivate()
        if(page!="IFD-Schnittstelle")withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","ifd_stop"));NativeCore.call(JSONObject().put("op","close"))}}
        remoteState=JSONObject();if(status=="Bereit für Lesegerät")status="Simulation beendet"
        val profile=selected
        if(page=="Editor"&&profile?.kind=="eid") {
            val state=withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","editor_state").put("id",profile.id))}.getOrNull()}
            state?.optJSONObject("settings")?.takeIf{
                val applied=state.optJSONObject("draft_settings")
                applied!=null&&applied.keys().asSequence().all{key->profile.settings.opt(key)==applied.opt(key)}
            }?.let{settings->
                val ri=state.optJSONObject("ri")
                val unchanged=settings.length()==profile.settings.length()&&settings.keys().asSequence().all{key->settings.opt(key)==profile.settings.opt(key)}&&ri?.toString()==profile.ri?.toString()
                if(!unchanged) {
                    val old=profile.settings;profile.settings=settings;profile.ri=ri
                    runCatching{Profile.parse(profile.json());store?.save()}.onSuccess{revision++}.onFailure{profile.settings=old;message=it.message}
                }
            }
        }
    }}
    LaunchedEffect(Unit) {
        val began=android.os.SystemClock.uptimeMillis()
        storeResult=withContext(Dispatchers.IO){runCatching{
            DocumentImages.warm(context);CountryCodes.load(context);ProfileStore(context)
        }}
        val initialization=withContext(Dispatchers.IO){
            runCatching {
                check(NativeCore.available){"Native Core-Bibliothek fehlt"}
                val proof=LicenseKey.prove(context.applicationContext,NativeCore.bindingChallenge())
                NativeCore.initialize(context.applicationContext,proof.publicKey,proof.signature)
            }
        }
        initialization.fold({coreReady.complete(Unit)},{coreReady.completeExceptionally(it)})
        delay((1000L-(android.os.SystemClock.uptimeMillis()-began)).coerceAtLeast(0L))
        startup=false
        if(initialization.isSuccess)withContext(Dispatchers.IO){runCatching{TrialActivation.activate()}.onFailure{android.util.Log.w("Phoenix","Hintergrundinitialisierung noch nicht verfügbar")}}
    }
    LaunchedEffect(page,wlan,status) {
        if(page=="IFD-Schnittstelle"||(page=="Simulation"&&wlan&&status=="Bereit für Lesegerät"))while(isActive){
            val state=withContext(Dispatchers.IO){runCatching{coreReady.await();NativeCore.call(JSONObject().put("op","ifd_status"))}}
            state.onSuccess{remoteState=it;if(page=="Simulation"&&!it.optBoolean("running")){status="Simulation beendet";if(it.optString("last_error").isNotEmpty())message=it.optString("last_error")}}
            delay(1000)
        }
    }
    val components=remember{JSONObject(context.assets.open("components.lock.json").use{ProfileStore.read(it)})}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null&&store!=null)scope.launch{
            val result=withContext(Dispatchers.IO){runCatching{context.contentResolver.openInputStream(uri).use{ProfileStore.read(it)}}}
            result.onSuccess{raw->runCatching{store.importDraft(raw)}.onSuccess{selected=it;selectedKind=it.kind;revision++;page="Editor"}.onFailure{message=it.message}}.onFailure{message="Import fehlgeschlagen: ${it.message}"}
        }
    }
    var exportId by rememberSaveable{mutableStateOf<String?>(null)}
    var pendingExport by remember{mutableStateOf<ByteArray?>(null)}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        val id=exportId;exportId=null
        val prepared=pendingExport;pendingExport=null
        if(uri!=null&&id!=null&&store!=null)scope.launch {
            val result=runCatching{prepared?:error("Export bitte erneut starten") }
            result.onSuccess{bytes->
                withContext(Dispatchers.IO){runCatching{
                    val stream=context.contentResolver.openOutputStream(uri,"wt")?:error("Datei nicht beschreibbar")
                    stream.use{it.write(bytes)}
                }}.onSuccess{message="Profil exportiert"}.onFailure{message="Export fehlgeschlagen: ${it.message}"}
            }.onFailure{message="Export fehlgeschlagen: ${it.message}"}
        }
    }
    if(store==null){
        if(startup)Startup() else Box(Modifier.fillMaxSize().background(Page).padding(24.dp)){Text("Profilspeicher nicht lesbar. Vorhandene Daten bleiben erhalten.",color=Navy)}
        return
    }
    fun exportProfile(profile:Profile) {
        val snapshot=Profile.parse(profile.json()).apply{baselineGroups=profile.baselineGroups}
        scope.launch {
            withContext(Dispatchers.IO){runCatching{
                if(snapshot.kind=="eid") {
                    CardService.deactivate()
                    NativeCore.call(JSONObject().put("op","ifd_stop"))
                    NativeCore.call(JSONObject().put("op","mount_draft").put("draft",snapshot.coreDraft()))
                    NativeCore.call(JSONObject().put("op","close"))
                    val state=NativeCore.call(JSONObject().put("op","editor_state").put("id",snapshot.id))
                    snapshot.ri=state.getJSONObject("ri");snapshot.settings=state.getJSONObject("settings")
                }
                ProfileStore.exportDraft(snapshot)
            }}.onSuccess{bytes->
                pendingExport=bytes;exportId=snapshot.id;exporter.launch(snapshot.id+".json")
            }.onFailure{message=it.message}
        }
    }
    fun createProfile(kind:String) {
        if(store.ownCount()>=100){message="Maximal 100 eigene Profile";return}
        val draft=Profile.defaults().getJSONObject(if(kind=="eid")1 else 0)
            .put("pki_infrastructure",if(kind=="eid")"bsi" else "profile").put("id",java.util.UUID.randomUUID().toString()).put("name","Neues ${if(kind=="eid")"eID" else "ePassport"}-Profil")
        val profile=Profile.parse(draft)
        store.profiles.add(profile)
        runCatching{store.save()}.onSuccess{selected=profile;selectedKind=kind;revision++;page="Editor"}.onFailure{store.profiles.remove(profile);message=it.message}
    }
    fun startSimulation() {
        page="Simulation"
        val profile=selected
        val adapter=NfcAdapter.getDefaultAdapter(context)
        when {
            status in listOf("Simulation wird gestartet …","Simulation wird beendet …")->Unit
            profile==null->message="Zuerst ein Profil auswählen"
            !profile.simulatable->message="Nicht simulierbar: ${profile.simulationError}"
            wlan&&profile.kind!="eid"->message="WLAN-Kartenleser unterstützt eID-Profile"
            !wlan&&(adapter==null||!adapter.isEnabled)->message="NFC ist nicht eingeschaltet"
            !wlan&&!context.packageManager.hasSystemFeature("android.hardware.nfc.hce")->message="Keine NFC-Kartenemulation verfügbar"
            else->{
                val session=CardService.beginStart()
                val draft=profile.coreDraft()
                status="Simulation wird gestartet …"
                scope.launch {
                    val result=withContext(Dispatchers.IO){runCatching{
                        coreReady.await()
                        TrialActivation.activate()
                        NativeCore.call(JSONObject().put("op","ifd_stop"))
                        NativeCore.call(if(wlan)JSONObject().put("op","ifd_start").put("draft",draft).put("pair",false) else JSONObject().put("op","mount_draft").put("draft",draft))
                    }}
                    result.onSuccess { state->
                        val valid=page=="Simulation"&&selected?.id==profile.id&&CardService.completeStart(session,!wlan)
                        if(valid&&wlan)remoteState=state
                        if(!valid)withContext(Dispatchers.IO){NativeCore.call(JSONObject().put("op","ifd_stop"))}
                        status=if(valid)"Bereit für Lesegerät" else "Simulation beendet"
                    }.onFailure {CardService.deactivate();status="Simulation nicht verfügbar";message=it.message}
                }
            }
        }
    }
    fun selectSimulation(profile:Profile) {
        CardService.deactivate()
        selected=profile;selectedKind=profile.kind;if(profile.kind=="epassport")wlan=false
        page="Simulation";simulationPicker=false
        if(!profile.simulatable){status="Profil nicht simulierbar";return}
        status="Profil wird geladen …"
        scope.launch {
            val result=withContext(Dispatchers.IO){runCatching{
                coreReady.await();TrialActivation.activate()
                NativeCore.call(JSONObject().put("op","ifd_stop"))
                NativeCore.call(JSONObject().put("op","mount_draft").put("draft",profile.coreDraft()))
            }}
            if(selected?.id==profile.id&&status=="Profil wird geladen …")result.onSuccess{status="Profil geladen"}.onFailure{status="Profil konnte nicht geladen werden";message=it.message}
        }
    }
    fun pairReader() {
        if(pairBusy)return
        pairBusy=true
        val session=CardService.beginStart()
        scope.launch {
            val result=withContext(Dispatchers.IO){runCatching{coreReady.await();TrialActivation.activate();NativeCore.call(JSONObject().put("op","ifd_pair"))}}
            result.onSuccess{
                if(page=="IFD-Schnittstelle"&&CardService.completeStart(session,false))remoteState=it
                else withContext(Dispatchers.IO){NativeCore.call(JSONObject().put("op","ifd_stop"))}
            }.onFailure{message=it.message}
            pairBusy=false
        }
    }
    fun saveProfile(profile:Profile,name:String,settings:JSONObject):Boolean {
        val value=name.trim()
        if(value.isEmpty()||value.length>120){message="Profilname muss 1 bis 120 Zeichen enthalten";return false}
        val old=profile.name;val previous=profile.settings;profile.name=value;profile.settings=settings
        return runCatching{Profile.parse(profile.json());store?.save()}.onSuccess{revision++}.onFailure{profile.name=old;profile.settings=previous;message=it.message}.isSuccess
    }
    LaunchedEffect(page){
        identity=account.identity();cloudVerified=account.verifiedConnection()
        if(account.connected()&&!cloudVerified)withContext(Dispatchers.IO){runCatching{account.status()}}.onSuccess{identity=account.identity();cloudVerified=account.verifiedConnection()}
    }
    val active=if(page in listOf("Editor","Profile","Profilliste"))"Profile" else if(page=="Simulation")"Simulation" else "Über"
    if(startup){Startup();return}
    Column(Modifier.fillMaxSize().background(Page).statusBarsPadding().semantics{testTagsAsResourceId=true}) {
        if(page!="Editor")Row(Modifier.fillMaxWidth().height(if(page=="Profile")96.dp else 56.dp).padding(horizontal=Inset),verticalAlignment=Alignment.CenterVertically) {
            if(page=="Profile"||page=="Simulation") {
                Brand(Modifier.size(if(page=="Profile")72.dp else 34.dp,if(page=="Profile")86.dp else 42.dp));Spacer(Modifier.width(12.dp));Text("Phoenix",fontSize=if(page=="Profile")30.sp else 20.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.7).sp,color=Navy,modifier=Modifier.weight(1f))
            }else{
                if(page in listOf("Editor","Einstellungen","Profilliste","IFD-Schnittstelle","Cloud-Anbindung","Benutzer"))IconButton({page=when(page){"Editor"->"Profilliste";"Profilliste"->"Profile";"IFD-Schnittstelle","Cloud-Anbindung","Benutzer"->"Einstellungen";else->"Über"}},Modifier.semantics{contentDescription="Zurück"}){Glyph("back",Navy)}
                if(page=="Editor"||page=="Profilliste") {Spacer(Modifier.weight(1f));Brand(Modifier.size(30.dp,38.dp))}
                else Text(page,fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.6).sp,color=Navy,modifier=Modifier.weight(1f))
            }
            if(userId!=null&&page!="Benutzer")IconButton({page="Benutzer"},Modifier.semantics{contentDescription="Benutzerkonto öffnen"}.testTag("open-account")){AccountAvatar(Modifier.size(30.dp))}
            if(page !in listOf("Einstellungen","Editor","Profilliste","IFD-Schnittstelle","Cloud-Anbindung","Benutzer"))IconButton({page="Einstellungen"},Modifier.semantics{contentDescription="Einstellungen"}){Glyph("gear",Navy)}
        }
        Box(Modifier.weight(1f).padding(horizontal=if(page=="Editor")10.dp else Inset).padding(top=if(page=="Editor")0.dp else 8.dp,bottom=16.dp)) {
            val visibleProfiles=remember(revision){store.profiles.toList()}
            key(Unit) {
                when(page) {
                    "Profile"->ProfileList(visibleProfiles,{selectedKind=it;selected=null;page="Profilliste"})
                    "Profilliste"->ProfileSelection(visibleProfiles,selectedKind,{selected=it;page="Editor"},{createProfile(selectedKind)})
                    "Editor"->selected?.let{profile->Editor(profile,revision,cloudVerified,{importer.launch(arrayOf("application/json"))},{
                        exportProfile(profile)
                    },{name,settings->saveProfile(profile,name,settings)},{
                        runCatching{store.importDraft(profile.editableCopy().toString())}.onSuccess{selected=it;revision++}.onFailure{message=it.message}
                    },{group,value->if(profile.signedFixture||profile.passportCatalog)copyGroup=group to value else {editingValue=value;editing=group}},{selectSimulation(profile)},{value->
                        val old=profile.portraitSex;profile.portraitSex=value
                        runCatching{Profile.parse(profile.json());store.save()}.onSuccess{revision++}.onFailure{profile.portraitSex=old;message=it.message}.isSuccess
                    },{page="Profilliste"},{number->
                        val old=profile.documentNumber;profile.documentNumber=number
                        runCatching{Profile.parse(profile.json());store.save()}.onSuccess{revision++}.onFailure{profile.documentNumber=old;message=it.message}.isSuccess
                    },{
                        CardService.deactivate()
                        runCatching{store.resetPersonalization(profile)}.onSuccess{selected=it;revision++}.onFailure{message=it.message}
                    },{infrastructure->
                        val old=profile.pkiInfrastructure
                        if(infrastructure=="phoenix"&&!account.verifiedConnection())message="Zuerst erfolgreich mit der Cloud verbinden" else {
                            profile.pkiInfrastructure=infrastructure
                            runCatching{Profile.parse(profile.json());store.save()}.onSuccess{revision++}.onFailure{profile.pkiInfrastructure=old;message=it.message}
                        }
                    },{state->
                        state.optJSONObject("settings")?.let{settings->
                            val old=profile.settings;profile.settings=settings;profile.ri=state.optJSONObject("ri")
                            runCatching{Profile.parse(profile.json());store.save()}.onSuccess{revision++}.onFailure{profile.settings=old;message=it.message}
                        }
                    })}
                    "Benutzer"->UserAccount(identity,account.environment(),accountBusy){
                        accountBusy=true
                        scope.launch{withContext(Dispatchers.IO){runCatching{account.disconnect()}}.onSuccess{identity=JSONObject();page="Einstellungen"}.onFailure{message=it.message};accountBusy=false}
                    }
                    "Über"->About(components)
                    "Einstellungen"->Settings({legal=it},{page="IFD-Schnittstelle"},{page="Cloud-Anbindung"})
                    "Cloud-Anbindung"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){PkiDeviceSettings(identity){identity=account.identity()}}
                    "IFD-Schnittstelle"->ReaderSettings(remoteState,pairBusy,::pairReader,{
                        scope.launch{withContext(Dispatchers.IO){NativeCore.call(JSONObject().put("op","ifd_stop"))};remoteState=JSONObject()}
                    },{forgetPairing=true})
                    "Simulation"->Simulation(selected,status,{startSimulation()},{
                        CardService.deactivate();status="Simulation wird beendet …"
                        scope.launch {
                            withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","ifd_stop"));NativeCore.call(JSONObject().put("op","close"))}}
                            remoteState=JSONObject();status="Simulation beendet"
                        }
                    },wlan,{wlan=it},remoteState,{simulationPicker=true})
                }
            }
        }
        Navigation(active){page=it}
    }
    if(simulationPicker)SimulationProfilePicker(store?.profiles?:emptyList(),{simulationPicker=false}){profile->
        selectSimulation(profile)
    }
    if(forgetPairing)AlertDialog(onDismissRequest={forgetPairing=false},title={Text("Kopplungen entfernen?")},text={Text("Die AusweisApp muss anschließend erneut gekoppelt werden.")},confirmButton={TextButton({
        forgetPairing=false;scope.launch{withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","ifd_forget"))}.onFailure{message=it.message}};remoteState=JSONObject()}
    }){Text("Entfernen")}},dismissButton={TextButton({forgetPairing=false}){Text("Abbrechen")}})
    if(message!=null)AlertDialog(onDismissRequest={message=null},title={Text("Phoenix")},text={Text(message!!)},confirmButton={TextButton({message=null}){Text("OK")}})
    copyGroup?.let{(group,value)->
        AlertDialog(onDismissRequest={copyGroup=null},title={Text("Originalprofil bearbeiten")},text={Text("Dieses signierte Original bleibt erhalten. Zum Ändern der Datengruppen wird eine eigene bearbeitbare Kopie angelegt.")},confirmButton={TextButton({
            val original=selected
            if(original!=null)runCatching{store.importDraft(original.editableCopy().toString())}.onSuccess{
                selected=it;revision++;copyGroup=null;editingValue=value;editing=group
            }.onFailure{message=it.message}
        }){Text("Kopie bearbeiten")}},dismissButton={TextButton({copyGroup=null}){Text("Abbrechen")}})
    }
    editing?.let{group->
        val profile=selected!!
        fun saveGroup(value:String) {
            val old=profile.groups
            profile.groups=JSONObject(old.toString()).put(group,value)
            if(profile.kind=="eid"&&group=="DG17") {
                val draft=profile.coreDraft()
                scope.launch {
                    val description=withContext(Dispatchers.IO){runCatching{NativeCore.call(JSONObject().put("op","describe_draft").put("draft",draft))}}
                    description.onSuccess{result->
                        val community=result.optJSONObject("community")
                        community?.optString("expected")?.takeIf{it.isNotEmpty()}?.let{profile.groups.put("DG18",JSONObject().put("Text",it).toString())}
                        runCatching{Profile.parse(profile.json());store.save()}.onSuccess{revision++;editing=null
                            if(community?.optString("status")=="unresolved")message="Anschrift gespeichert. Gemeindeschlüssel konnte nicht automatisch ermittelt werden: "+community.optString("message")
                        }.onFailure{profile.groups=old;message=it.message}
                    }.onFailure{profile.groups=old;message=it.message}
                }
            } else runCatching{Profile.parse(profile.json());store.save()}.onSuccess{revision++;editing=null}.onFailure{profile.groups=old;message=it.message}
        }
        if(profile.kind=="epassport"&&group=="DG1")PassportDataEditor(editingValue,{editing=null},::saveGroup)
        else if(profile.kind=="epassport"&&group=="DG2")PassportPortraitPicker(profile,{editing=null},::saveGroup)
        else if(profile.kind=="eid")EidGroupEditor(group,editingValue,{editing=null},::saveGroup)
        else {
            var value by remember(profile.id,group){mutableStateOf(if(profile.kind=="eid")editableGroupText(editingValue) else editingValue)}
            AlertDialog(onDismissRequest={editing=null},title={Text("$group bearbeiten")},text={OutlinedTextField(value,{value=it.take(65536)},modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=260.dp),label={Text("$group Inhalt")})},confirmButton={TextButton({saveGroup(value)}){Text("Speichern")}},dismissButton={TextButton({editing=null},Modifier.semantics{testTagsAsResourceId=true}.testTag("cancel-group")){Text("Abbrechen")}})
        }
    }
    legal?.let{title->
        val text=remember(title){when(title){
            "Impressum"->"Phoenix Android\nhttps://github.com/Zimphi/Phoenix-Android\n\nVerantwortlichen- und Kontaktangaben sind vor Veröffentlichung einer App-Distribution zu ergänzen."
            "Datenschutz"->"Diese Entwicklungsversion speichert synthetische Profilentwürfe lokal auf dem Gerät. Keine Analyse- oder Werbedienste.\n\nDie Hintergrundaktivierung verwendet den Phoenix Lizenz-Testrelay über HTTPS. Übermittelt werden pseudonyme Geräte- und Anfragehashes sowie die ausgewählten Core-Produkte. Der private Geräteschlüssel bleibt im Android Keystore. Die native Lizenzprüfung benötigt zusätzlich authentifizierte Zeit von Netnod bzw. Cloudflare. Profile und NFC-APDUs werden dafür nicht übertragen. Bei ausdrücklich gestarteter WLAN-Simulation wird das ausgewählte synthetische eID-Profil über eine verschlüsselte Remote-IFD-Verbindung an eine gekoppelte AusweisApp im lokalen Netzwerk übermittelt. Die Kopplung verwendet einen zeitlich begrenzten Code; anschließend werden die Kommunikationszertifikate geprüft. Beim Verlassen der Simulation oder beim Wechsel in den Hintergrund endet der WLAN-Kartenleser. Die optionale PKI-Geräteanmeldung sendet nach dem Scannen eines QR-Codes die Einmalberechtigung, den öffentlichen Geräteschlüssel, den Gerätenamen und signierte Anfragen an den im QR-Code festgelegten Phoenix-Server. Kennung und Schlüsselzuordnung bleiben lokal gespeichert; der private PKI-Geräteschlüssel bleibt im Android Keystore. Status und Abmeldung verwenden signierte HTTPS-Anfragen. Die Cloud-Simulation ist nicht umgesetzt."
            else->context.assets.open("THIRD_PARTY_NOTICES.txt").use{ProfileStore.read(it)}+"\n\n"+context.assets.open("LICENSE.txt").use{ProfileStore.read(it)}+"\n\n"+context.assets.open("LGPL-2.1.txt").use{ProfileStore.read(it)}+"\n\n"+context.assets.open("ODbL-1.0.txt").use{ProfileStore.read(it)}
        }}
        AlertDialog(onDismissRequest={legal=null},title={Text(title)},text={Text(text,Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()))},confirmButton={TextButton({legal=null},Modifier.semantics{testTagsAsResourceId=true}.testTag("close-legal")){Text("Schließen")}})
    }
}
