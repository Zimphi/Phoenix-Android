// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.tech.IsoDep
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal data class NfcTestUi(
    val phase:String="intro",val master:Boolean=true,val connected:Boolean=false,val busy:Boolean=false,
    val qr:String="",val pairCode:String="",val peer:String="",val title:String="So funktioniert’s",val detail:String="",
    val progress:Int=0,val step:Int=0,val direction:Int=0,val caseName:String="epassport",val verdict:String="inconclusive",val report:String?=null,val error:String?=null
)

internal class NfcTestSession(private val activity:Activity):AutoCloseable {
    val ui=MutableStateFlow(NfcTestUi())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val adapter=NfcAdapter.getDefaultAdapter(activity)
    private val linkLock=Any()
    private val sendLock=Mutex()
    private var listener:NfcTestTls.Listener?=null
    private var channel:NfcTestTls.Channel?=null
    @Volatile private var epoch=0
    @Volatile private var radioEpoch=0
    private val work=mutableSetOf<Job>()
    private val radioLock=Any()
    private var activeIso:IsoDep?=null
    private var local=JSONObject()
    private var peer=JSONObject()
    @Volatile private var round=""
    private var direction=0
    private var readerEvidence:JSONObject?=null
    private val results=mutableListOf<JSONObject>()
    private var deadline:Job?=null
    private val reading=AtomicBoolean()
    private var protocol:NfcTestProtocol?=null
    private var testId=""
    private var exporting=false
    private val discovery=NfcTestDiscovery(activity)
    private val cases=listOf("epassport","eid","samsung")
    private var caseIndex=0
    private var material=JSONObject()
    private var startedAt=""

    private fun update(block:(NfcTestUi)->NfcTestUi){ui.value=block(ui.value)}
    private fun launchTask(block:suspend CoroutineScope.()->Unit){
        val generation=epoch
        val job=scope.launch(start=CoroutineStart.LAZY){
            try{block()}catch(_:CancellationException){}catch(_:Exception){if(generation==epoch){
                val state=ui.value
                if((!state.master&&state.connected)||(state.phase=="result"&&state.report!=null))stop("Die Verbindung zum anderen Telefon wurde beendet. Die temporäre Kopplung ist aufgehoben.")
                else fail("Die Verbindung konnte nicht hergestellt oder fortgesetzt werden. Bitte koppelt beide Telefone erneut.")
            }}
            finally{work.remove(coroutineContext[Job])}
        }
        work+=job;job.start()
    }
    private fun wifiAddress():Inet4Address {
        val manager=activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network=manager.activeNetwork?:error("Kein WLAN")
        require(manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)
        return manager.getLinkProperties(network)?.linkAddresses?.map{it.address}?.filterIsInstance<Inet4Address>()
            ?.firstOrNull{it.isSiteLocalAddress}?:error("Kein lokales IPv4-WLAN")
    }
    private fun prerequisites(){require(adapter?.isEnabled==true){"NFC aktivieren"};require(activity.packageManager.hasSystemFeature("android.hardware.nfc.hce"));wifiAddress()}
    fun create(){
        if(ui.value.busy||ui.value.connected)return
        reset();update{it.copy(phase="pair",busy=true,title="QR-Code wird erstellt")}
        val generation=epoch
        launchTask {
            val prepared=withContext(Dispatchers.IO){
                prerequisites();TrialActivation.activate();TrialActivation.activate(terminal=true);local=NfcTestMetadata.collect(activity)
                val value=NfcTestTls.listen(wifiAddress())
                synchronized(linkLock){if(generation!=epoch){value.close();error("Abgebrochen")};listener=value}
                value
            }
            if(generation!=epoch){prepared.close();return@launchTask}
            val qr=JSONObject().put("v",1).put("host",wifiAddress().hostAddress).put("port",prepared.port)
                .put("pin",encode(prepared.pin)).put("token",encode(prepared.token)).toString()
            update{it.copy(qr=qr,busy=false,title="Zweites Telefon koppeln",detail="Öffne dort NFC-Test → An Test teilnehmen und scanne diesen Code.")}
            val code=java.security.SecureRandom().nextInt(1000000).toString().padStart(6,'0')
            discovery.advertise(prepared.port){ready->scope.launch{if(generation==epoch&&ready)update{it.copy(pairCode=code)}}}
            val accepted=withContext(Dispatchers.IO){
                var connected:NfcTestTls.Channel?=null
                for(attempt in 0 until 5){
                    try{connected=adopt(prepared.accept(code),generation);break}
                    catch(error:Exception){if(generation!=epoch||attempt==4)throw error}
                }
                requireNotNull(connected)
            }
            if(generation!=epoch){accepted.close();return@launchTask}
            listener?.close();listener=null;discovery.close()
            connected(accepted,generation)
        }
    }
    fun join(qr:String){
        reset();update{it.copy(master=false,phase="pair",busy=true,title="Verbindung wird hergestellt")}
        val generation=epoch
        launchTask {
            val joined=withContext(Dispatchers.IO){
                prerequisites();TrialActivation.activate();TrialActivation.activate(terminal=true);local=NfcTestMetadata.collect(activity);require(qr.length in 1..2048)
                val code=JSONObject(qr);require(code.getInt("v")==1)
                adopt(NfcTestTls.connect(code.getString("host"),code.getInt("port"),decode(code.getString("pin")),decode(code.getString("token"))),generation)
            }
            if(generation!=epoch){joined.close();return@launchTask}
            connected(joined,generation)
        }
    }
    fun joinCode(code:String){
        if(!code.matches(Regex("[0-9]{6}")))return
        reset();update{it.copy(master=false,phase="pair",busy=true,title="Master wird im WLAN gesucht")}
        val generation=epoch
        launchTask {
            withContext(Dispatchers.IO){prerequisites();TrialActivation.activate();TrialActivation.activate(terminal=true);local=NfcTestMetadata.collect(activity)}
            val endpoints=discovery.find();require(endpoints.isNotEmpty())
            var joined:NfcTestTls.Channel?=null
            for(endpoint in endpoints){
                try{joined=withContext(Dispatchers.IO){adopt(NfcTestTls.connectCode(requireNotNull(endpoint.address.hostAddress),endpoint.port,code),generation)};break}
                catch(error:Exception){if(generation!=epoch)throw error}
            }
            connected(requireNotNull(joined),generation)
        }
    }
    private fun adopt(value:NfcTestTls.Channel,generation:Int):NfcTestTls.Channel = synchronized(linkLock){
        if(generation!=epoch){value.close();error("Abgebrochen")}
        channel=value;value
    }
    private suspend fun connected(value:NfcTestTls.Channel,generation:Int){
        channel=value
        launchTask{while(generation==epoch){delay(10000);send(JSONObject().put("op","ping"))}}
        send(JSONObject().put("op","hello").put("device",local))
        while(generation==epoch){
            val message=withContext(Dispatchers.IO){JSONObject(String(value.read(),Charsets.UTF_8))}
            if(generation==epoch)receive(message)
        }
    }
    private suspend fun send(value:JSONObject){
        val link=channel?:error("Verbindung getrennt")
        sendLock.withLock{withContext(Dispatchers.IO){link.write(value.toString().toByteArray(Charsets.UTF_8))}}
    }
    private suspend fun receive(message:JSONObject){
        when(message.getString("op")) {
            "ping"->require(ui.value.connected)
            "hello"->{
                require(!ui.value.connected && peer.length()==0)
                peer=NfcTestMetadata.clean(message.getJSONObject("device"))
                update{it.copy(connected=true,busy=false,qr="",pairCode="",peer=peer.getString("model"),phase="ready",title="Alles bereit",detail="Lege beide Telefone entsperrt Rücken an Rücken.")}
            }
            "round"->{
                require(!ui.value.master&&ui.value.connected&&ui.value.phase in listOf("ready","running"))
                stopRadio();round=UUID.fromString(message.getString("round")).toString();direction=message.getInt("direction");require(direction in 0..1)
                caseIndex=message.getInt("case_index");require(caseIndex in cases.indices)
                testId=UUID.fromString(message.getString("test_id")).toString()
                update{it.copy(phase="running",direction=direction,caseName=cases[caseIndex],title="${caseLabel()} läuft",detail="Bitte folge den Hinweisen auf dem Master.")}
                if(cases[caseIndex]=="samsung"){
                    val key=decode(message.getString("key"));require(key.size==32);protocol=NfcTestProtocol(key,direction);key.fill(0)
                }
                if(direction==1){
                    material=if(cases[caseIndex]=="samsung")JSONObject()else withContext(Dispatchers.IO){NfcTestCardService.prepare(cases[caseIndex])}
                    armCard();send(roundMessage("armed").put("material",material))
                }else{material=message.getJSONObject("material");armReader()}
            }
            "stage"->{
                require(ui.value.master)
                if(direction!=0||message.getString("round")!=round||ui.value.phase!="running")return
                val step=message.getInt("step");require(step in 1..3)
                update{it.copy(step=maxOf(step,it.step),progress=maxOf(it.progress,baseProgress()+step*4))}
            }
            "armed"->{require(ui.value.master && direction==1 && message.getString("round")==round);material=message.getJSONObject("material");armReader()}
            "reader"->{require(ui.value.master && direction==0 && message.getString("round")==round);readerEvidence=cleanReader(message.getJSONObject("evidence"));finishDirection(JSONObject(NfcTestCardService.snapshot()))}
            "snapshot"->{require(!ui.value.master && direction==1 && message.getString("round")==round);send(roundMessage("card").put("evidence",JSONObject(NfcTestCardService.snapshot())));stopRadio()}
            "card"->{require(ui.value.master && direction==1 && message.getString("round")==round);finishDirection(cleanCard(message.getJSONObject("evidence")))}
            "repeat"->{
                require(!ui.value.master&&ui.value.phase=="result"&&ui.value.connected)
                update{it.copy(phase="ready",title="Bereit für den nächsten Test",progress=0,step=0)}
            }
            "done"->{require(!ui.value.master && ui.value.phase=="running");stopRadio();update{it.copy(phase="result",title="Test beendet",detail="Das gemeinsame Ergebnis wird auf dem Master angezeigt.",progress=100)}}
            "abort"->{stop("Die Verbindung wurde auf dem anderen Telefon beendet.")}
            else->error("Unbekannte Testnachricht")
        }
    }
    fun start(){
        if(!ui.value.master||!ui.value.connected||ui.value.phase!="ready")return
        results.clear();testId=UUID.randomUUID().toString();startedAt=java.time.Instant.now().toString()
        update{it.copy(verdict="inconclusive",report=null,error=null)}
        launchTask{beginDirection(0)}
    }
    private fun baseProgress()=(direction*cases.size+caseIndex)*100/(cases.size*2)
    private fun caseLabel()=when(cases[caseIndex]){"epassport"->"ePassport-Session";"eid"->"eID-Session";else->"Samsung-Auswahltest"}
    private suspend fun beginDirection(value:Int,index:Int=0){
        stopRadio();direction=value;caseIndex=index;round=UUID.randomUUID().toString();readerEvidence=null
        val key=NfcTestTls.bytes(32)
        if(cases[caseIndex]=="samsung")protocol=NfcTestProtocol(key,direction)
        update{it.copy(phase="running",progress=baseProgress()+1,step=0,direction=value,caseName=cases[caseIndex],title=caseLabel(),
            detail=if(value==1&&index==0)"Jetzt wechseln die NFC-Rollen. Hebe ein Telefon kurz an und lege es wieder Rücken an Rücken auf." else "${caseLabel()} · ${if(value==0)"Master als Karte" else "Zweites Telefon als Karte"}. Bitte lass beide Telefone liegen.")}
        material=if(direction==0&&cases[caseIndex]!="samsung")withContext(Dispatchers.IO){NfcTestCardService.prepare(cases[caseIndex])}else JSONObject()
        if(value==0)armCard()
        try{send(roundMessage("round").put("direction",direction).put("case_index",caseIndex).put("test_id",testId).put("key",encode(key)).put("material",material))}finally{key.fill(0)}
        deadline=scope.launch{delay(120000);if(ui.value.phase=="running")fail("Keine vollständige NFC-Session innerhalb von 120 Sekunden. Prüfe die Position beider Telefone und wiederhole den Test.")}
    }
    private fun armCard(){
        val nfc=adapter?:error("NFC fehlt")
        nfc.disableReaderMode(activity)
        if(cases[caseIndex]=="samsung")NfcTestCardService.install(requireNotNull(protocol))else NfcTestCardService.installDocument()
        require(CardEmulation.getInstance(nfc).setPreferredService(activity,ComponentName(activity,NfcTestCardService::class.java)))
    }
    private fun armReader(){
        val p=protocol;val generation=epoch;val currentRound=round;val radioGeneration=radioEpoch
        val documentMaterial=material;val kind=cases[caseIndex]
        adapter?.enableReaderMode(activity,{tag->
            if(generation!=epoch||currentRound!=round||radioGeneration!=radioEpoch||!reading.compareAndSet(false,true))return@enableReaderMode
            scope.launch {
                if(generation!=epoch||currentRound!=round||radioGeneration!=radioEpoch)return@launch
                val evidence=withContext(Dispatchers.IO){readTag(tag,p,generation,radioGeneration,kind,documentMaterial)}
                if(generation!=epoch||round!=currentRound){reading.set(false);return@launch}
                launchTask {
                    if(ui.value.master){readerEvidence=evidence;send(roundMessage("snapshot"))}
                    else send(roundMessage("reader").put("evidence",evidence))
                }
            }
        },NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,null)
    }
    private fun readTag(tag:android.nfc.Tag,p:NfcTestProtocol?,generation:Int,radioGeneration:Int,kind:String,documentMaterial:JSONObject):JSONObject {
        fun stage(value:Int){scope.launch{if(generation==epoch&&radioGeneration==radioEpoch){
            if(ui.value.master)update{it.copy(step=value,progress=baseProgress()+value*4)}
            else launchTask{send(roundMessage("stage").put("step",value))}
        }}}
        fun current(){check(generation==epoch&&radioGeneration==radioEpoch)}
        val evidence=JSONObject().put("radio",false).put("control",false).put("select",false).put("status",0).put("error","none")
        val started=System.nanoTime()
        val transcript=JSONArray()
        try {
            current()
            val it=IsoDep.get(tag)?:error("unsupported")
            synchronized(radioLock){current();activeIso=it}
            it.connect();current();it.timeout=10000
            fun exchange(command:ByteArray):ByteArray {
                current();val sent=System.nanoTime()
                if(transcript.length()==64){transcript.remove(0)}
                val entry=JSONObject().put("header",command.take(4).joinToString(""){"%02X".format(it)}).put("length",command.size)
                    .put("response_length",0).put("status",0).put("sent_at",java.time.Instant.now().toString())
                transcript.put(entry)
                try{
                    val response=it.transceive(command)
                    entry.put("response_length",response.size).put("status",if(response.size>=2)((response[response.size-2].toInt() and 255) shl 8) or (response.last().toInt() and 255)else 0)
                    return response
                }finally{entry.put("duration_ms",(System.nanoTime()-sent)/1000000)}
            }
            run {
                if(kind!="samsung"){
                    evidence.put("radio",true);stage(1)
                    val full=runCatching{NfcTestNative.inspectCard(documentMaterial,NfcDocumentExchange{command->exchange(command)})}.getOrElse{JSONObject().put("status","fail")}
                    val passed=full.optString("status")=="pass"
                    evidence.put("full",full).put("control",passed).put("select",passed).put("status",if(passed)0x9000 else 0).put("error",if(passed)"none" else "protocol_failed")
                    stage(3)
                }else{
                val synthetic=requireNotNull(p)
                require(exchange(NfcTestProtocol.SELECT).contentEquals(NfcTestProtocol.status(0x9000)))
                evidence.put("radio",true);stage(1)
                current();val control=synthetic.verify(exchange(synthetic.command(0)),0);evidence.put("control",control)
                if(control){stage(2)
                    current();val response=exchange(synthetic.command(1));stage(3)
                    evidence.put("select",synthetic.verify(response,1))
                    if(response.size>=2)evidence.put("status",((response[response.size-2].toInt() and 255) shl 8) or (response.last().toInt() and 255))
                }else evidence.put("error","authentication")
                }
            }
        }catch(_:Exception){evidence.put("error","nfc_interrupted")}
        return evidence.put("apdus",transcript).put("duration_ms",(System.nanoTime()-started)/1000000)
    }
    private suspend fun finishDirection(card:JSONObject){
        val reader=requireNotNull(readerEvidence);deadline?.cancel();deadline=null
        val device=if(direction==0)local else peer
        val correlated=card.optBoolean("selected")&&card.optBoolean("control")&&(!card.optBoolean("deactivated",true)||card.optBoolean("trigger"))
        val assessment=NfcTestAssessment.assess(device.optString("model"),device.optString("firmware"),device.optString("library_sha256"),
            reader.optBoolean("radio"),correlated,reader.optBoolean("control"),reader.optBoolean("select"),card.optBoolean("trigger"),reader.optInt("status"))
        // A synthetic probe is not calibrated PACE/EAC or NCI-level proof of a native cause.
        val verdict=if(cases[caseIndex]!="samsung")if(reader.optJSONObject("full")?.let{it.optString("status")=="pass"&&it.optString("kind")==cases[caseIndex]}==true&&correlated)"not_observed"else"inconclusive"
            else when(assessment){"known_signature","not_observed"->assessment;else->"inconclusive"}
        results+=JSONObject().put("kind",cases[caseIndex]).put("finished_at",java.time.Instant.now().toString()).put("direction",direction).put("round",round).put("card_model",device.optString("model"))
            .put("reader",reader).put("hce",card).put("verdict",verdict)
        stopRadio()
        if(caseIndex<cases.lastIndex){delay(700);beginDirection(direction,caseIndex+1)}else if(direction==0){delay(700);beginDirection(1)}else{
            val overall=when{results.any{it.getString("verdict")=="known_signature"}->"known_signature";results.size==cases.size*2&&results.all{it.getString("verdict")=="not_observed"}->"not_observed";else->"inconclusive"}
            val report=JSONObject().put("schema","phoenix-nfc-suite-v1").put("test_id",testId).put("started_at",startedAt).put("finished_at",java.time.Instant.now().toString()).put("timezone",java.time.ZoneId.systemDefault().id)
                .put("master",local).put("peer",peer).put("directions",JSONArray(results)).put("verdict",overall)
                .put("patch_required",JSONObject.NULL).put("scope","Complete licensed ePassport and eID sessions followed by a separate authenticated Samsung transport probe, then all tests in the opposite NFC direction. Native patch diagnosis is scoped to observed evidence.")
                .put("probe_header","0CA4040C").put("probe_length",35)
            update{it.copy(phase="result",progress=100,verdict=overall,report=report.toString(2),title="Test abgeschlossen")}
            send(roundMessage("done"))
        }
    }
    private fun roundMessage(op:String)=JSONObject().put("op",op).put("round",round)
    fun disconnect(){
        if(ui.value.master&&ui.value.report==null&&local.length()>0&&peer.length()>0)update{it.copy(report=JSONObject()
            .put("schema","phoenix-nfc-suite-v1").put("master",local).put("peer",peer).put("directions",JSONArray(results))
            .put("verdict","inconclusive").put("reason","cancelled").put("patch_required",JSONObject.NULL).toString(2))}
        stop("Die temporäre Kopplung wurde aufgehoben.")
    }
    fun repeat(){
        if(!ui.value.master||!ui.value.connected||ui.value.phase!="result")return
        results.clear();testId=UUID.randomUUID().toString();startedAt=java.time.Instant.now().toString()
        update{it.copy(phase="running",verdict="inconclusive",report=null,error=null,progress=0,step=0,title="Test wird vorbereitet")}
        launchTask{send(roundMessage("repeat"));beginDirection(0)}
    }
    fun exportStarted(){exporting=true}
    fun exportFinished(){exporting=false}
    fun reset(){stop("");ui.value=NfcTestUi();local=JSONObject();peer=JSONObject();results.clear()}
    fun background(){if(!exporting&&(ui.value.connected||ui.value.phase=="pair"))disconnect()}
    private fun stopRadio(){
        radioEpoch++
        synchronized(radioLock){runCatching{activeIso?.close()};activeIso=null}
        deadline?.cancel();deadline=null
        runCatching{adapter?.disableReaderMode(activity)}
        runCatching{adapter?.let{CardEmulation.getInstance(it).unsetPreferredService(activity)}}
        NfcTestCardService.clear();protocol?.close();protocol=null;material=JSONObject();reading.set(false)
    }
    private fun stop(detail:String){
        epoch++;discovery.close();work.toList().forEach{it.cancel()};work.clear();stopRadio();synchronized(linkLock){runCatching{channel?.close()};channel=null;runCatching{listener?.close()};listener=null}
        update{it.copy(phase="ended",connected=false,busy=false,qr="",pairCode="",title="Verbindung beendet",detail=detail)}
    }
    private fun fail(detail:String){
        val before=ui.value
        stop(detail)
        val partial=if(before.master&&local.length()>0)JSONObject().put("schema","phoenix-nfc-suite-v1").put("master",local).put("peer",peer)
            .put("test_id",testId).put("started_at",startedAt).put("finished_at",java.time.Instant.now().toString())
            .put("directions",JSONArray(results)).put("verdict","inconclusive").put("reason",detail).put("patch_required",JSONObject.NULL).toString(2)else null
        update{it.copy(phase="result",verdict="inconclusive",error=detail,report=partial,title="Keine eindeutige Aussage")}
    }
    override fun close(){stop("");scope.cancel()}
    companion object {
        private fun encode(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        private fun decode(value:String):ByteArray{require(value.length<=64);return Base64.getUrlDecoder().decode(value)}
        internal fun cleanReader(input:JSONObject)=JSONObject().apply {
            for(key in listOf("radio","control","select"))put(key,input.getBoolean(key))
            val status=input.getInt("status");require(status in 0..65535);put("status",status)
            val error=input.getString("error");require(error in listOf("none","authentication","nfc_interrupted","protocol_failed"));put("error",error)
            val duration=input.getLong("duration_ms");require(duration in 0..120000);put("duration_ms",duration)
            put("apdus",cleanApdus(input.getJSONArray("apdus"),64))
            input.optJSONObject("full")?.let{full->
                val state=full.getString("status");require(state in listOf("pass","fail"))
                val clean=JSONObject().put("status",state)
                if(state=="pass"){
                    clean.put("kind",casesForValidation(full.getString("kind")))
                    val expected=listOf("PACE","Secure Messaging","PA","CA","TA","authorized_DG_read","unauthorized_read_rejected","authenticated_SELECT")
                    val checks=full.getJSONArray("checks");require(checks.length()==expected.size)
                    require((0 until checks.length()).map{checks.getString(it)}.toSet()==expected.toSet())
                    clean.put("checks",checks)
                    val groups=full.getJSONArray("data_groups");require(groups.length() in 1..21)
                    val numbers=(0 until groups.length()).map{groups.getInt(it)};require(numbers.all{it in 1..21}&&numbers.distinct().size==numbers.size)
                    clean.put("data_groups",JSONArray(numbers))
                }
                put("full",clean)
            }
        }
        private fun casesForValidation(kind:String):String{require(kind in listOf("eid","epassport"));return kind}
        private fun cleanApdus(input:JSONArray,max:Int)=JSONArray().apply {
            require(input.length()<=max)
            for(index in 0 until input.length()){
                val entry=input.getJSONObject(index);val header=entry.getString("header");require(header.matches(Regex("[0-9A-F]{0,8}")))
                val clean=JSONObject().put("header",header)
                for(key in listOf("length","response_length","status")){val number=entry.getInt(key);require(number in 0..65535);clean.put(key,number)}
                for(key in listOf("duration_ms","dispatch_ms"))if(entry.has(key)){val number=entry.getLong(key);require(number in -1..120000);clean.put(key,number)}
                for(key in listOf("sent_at","received_at"))if(entry.has(key)){val time=entry.getString(key);require(time.length<=40);java.time.Instant.parse(time);clean.put(key,time)}
                if(entry.has("discarded"))clean.put("discarded",entry.getBoolean("discarded"))
                put(clean)
            }
        }
        private fun cleanCard(input:JSONObject)=JSONObject().apply {
            for(key in listOf("selected","control","trigger","deactivated"))put(key,input.getBoolean(key))
            val entries=input.getInt("entries");require(entries in 0..1000);put("entries",entries)
            put("apdus",cleanApdus(input.getJSONArray("apdus"),32))
        }
    }
}
