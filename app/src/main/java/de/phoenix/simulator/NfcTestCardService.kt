// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import org.json.JSONObject

class NfcTestCardService:HostApduService() {
    override fun processCommandApdu(commandApdu:ByteArray,extras:Bundle?):ByteArray? {
        val started=System.nanoTime();val received=java.time.Instant.now().toString()
        synchronized(lock){
            if(!full)return protocol?.respond(commandApdu)?:NfcTestProtocol.status(0x6985)
            val session=generation
            val header=commandApdu.take(4).joinToString(""){"%02X".format(it)}
            entries++;if(header=="0CA4040C")trigger=true
            if(header.startsWith("00A404"))selected=true
            if(header.startsWith("0C"))control=true
            val command=commandApdu.clone()
            val evidence=mutableMapOf<String,Any>("header" to header,"length" to command.size,"response_length" to 0,"status" to 0,"received_at" to received,"duration_ms" to -1L,"dispatch_ms" to -1L,"discarded" to false)
            if(observations.size<32)observations+=evidence
            worker.execute{
                if(synchronized(lock){session!=generation||!full}){synchronized(lock){evidence["discarded"]=true};return@execute}
                val response=runCatching{NfcTestNative.transmit(command).also{require(it.size in 2..65535)}}.getOrElse{NfcTestProtocol.status(0x6f00)}
                synchronized(lock){evidence["response_length"]=response.size;evidence["status"]=((response[response.size-2].toInt() and 255) shl 8) or (response.last().toInt() and 255);evidence["duration_ms"]=(System.nanoTime()-started)/1000000}
                Handler(Looper.getMainLooper()).post{synchronized(lock){if(session==generation&&full){evidence["dispatch_ms"]=(System.nanoTime()-started)/1000000;sendResponseApdu(response)}else evidence["discarded"]=true}}
            }
            return null
        }
    }
    override fun onDeactivated(reason:Int){synchronized(lock){
        protocol?.deactivated=true;deactivated=true;generation++
        observations.filter{it["dispatch_ms"] == -1L}.forEach{it["discarded"]=true}
        if(full){val session=generation;worker.execute{if(synchronized(lock){session==generation&&full})runCatching{NfcTestNative.reset()}}}
    }}
    companion object {
        private val lock=Any()
        private val worker=Executors.newSingleThreadExecutor()
        private var protocol:NfcTestProtocol?=null
        private var full=false
        private var generation=0L
        private var selected=false
        private var control=false
        private var trigger=false
        private var entries=0
        private var deactivated=false
        private val observations=mutableListOf<MutableMap<String,Any>>()
        internal fun prepare(kind:String):JSONObject=worker.submit<JSONObject>{NfcTestNative.prepareCard(kind)}.get()
        internal fun install(value:NfcTestProtocol){synchronized(lock){protocol?.close();protocol=value;full=false;generation++}}
        internal fun installDocument(){synchronized(lock){protocol?.close();protocol=null;full=true;generation++;selected=false;control=false;trigger=false;entries=0;deactivated=false;observations.clear()}}
        internal fun snapshot():Map<String,Any> = synchronized(lock) {
            val p=protocol
            mapOf("selected" to (if(full)selected else p?.selected==true),"control" to (if(full)control else p?.control==true),"trigger" to (if(full)trigger else p?.trigger==true),
                "apdus" to (if(full)observations.map{it.toMap()} else p?.observations?.toList()?:emptyList<Map<String,Any>>()),
                "entries" to (if(full)entries else p?.entries?:0),"deactivated" to (if(full)deactivated else p?.deactivated?:true))
        }
        internal fun clear(){synchronized(lock){protocol?.close();protocol=null;full=false;generation++};worker.execute{runCatching{NfcTestNative.close()}}}
        internal fun active()=synchronized(lock){protocol!=null||full}
    }
}
