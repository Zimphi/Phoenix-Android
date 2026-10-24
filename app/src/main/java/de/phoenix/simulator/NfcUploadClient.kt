// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal data class NfcUploadReceipt(val id:String,val expiresAt:Long,val sha256:String)

internal class NfcUploadClient(private val context:Context) {
    companion object {const val ORIGIN="https://phoenix-nfc-diagnostics.phoenix-license-relay.workers.dev"}
    private val account=PkiDevice(context)
    private suspend fun request(method:String,path:String,body:ByteArray=byteArrayOf(),nonce:String=""):JSONObject=withContext(Dispatchers.IO){
        coroutineScope {
            val connection=URL(ORIGIN+path).openConnection() as HttpsURLConnection
            val closer=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{connection.disconnect()}}
            try{
                ensureActive()
                connection.requestMethod=method;connection.instanceFollowRedirects=false;connection.useCaches=false
                connection.connectTimeout=10000;connection.readTimeout=15000
                account.diagnosticsProof(ORIGIN,method,path,NfcUploadCrypto.sha256(body),nonce).forEach{(k,v)->connection.setRequestProperty(k,v)}
                if(method in listOf("POST","PUT")){
                    connection.doOutput=true;connection.setFixedLengthStreamingMode(body.size)
                    connection.setRequestProperty("Content-Type",if(method=="PUT")"application/pgp-encrypted" else "application/json")
                    connection.outputStream.use{it.write(body)}
                }
                val code=connection.responseCode
                check(code in listOf(200,201)){when(code){
                    401,403->"Phoenix-Anmeldung ungültig oder Gerätezugang gesperrt"
                    409,410->"Upload konnte nicht abgeschlossen werden; bitte erneut senden"
                    413->"Diagnoselog ist zu groß"
                    429->"Zu viele Diagnose-Uploads; bitte später erneut versuchen"
                    else->"Diagnose-Service derzeit nicht erreichbar (HTTP $code)"
                }}
                check(connection.contentType?.startsWith("application/json")==true){"Ungültige Serverantwort"}
                val bytes=connection.inputStream.use{input->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                    while(true){ensureActive();val n=input.read(buffer);if(n<0)break;check(out.size()+n<=16384){"Serverantwort zu groß"};out.write(buffer,0,n)}
                    out.toByteArray()
                }
                JSONObject(String(bytes,Charsets.UTF_8))
            }finally{closer.cancel()}
        }
    }
    private suspend fun retry(block:suspend()->JSONObject):JSONObject {
        try{return block()}catch(error:java.io.IOException){currentCoroutineContext().ensureActive();delay(500);return block()}
    }
    suspend fun send(raw:String,progress:suspend(String)->Unit):NfcUploadReceipt {
        val report=withContext(Dispatchers.Default){NfcUploadReport.clean(raw)}
        progress("Anmeldung und Upload vorbereiten …")
        val requestId=UUID.randomUUID().toString()
        val offer=retry{request("POST","/v1/nfc-reports",JSONObject().put("request_id",requestId).toString().toByteArray())}
        val id=offer.getString("id");require(UUID.fromString(id).toString()==id)
        val nonce=offer.getString("nonce");require(nonce.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(offer.getString("recipient")==NfcUploadCrypto.PRIMARY&&offer.getString("encryption_subkey")==NfcUploadCrypto.SUBKEY)
        require(offer.getLong("upload_expires_at")>Instant.now().epochSecond&&offer.getInt("max_bytes")==1900000)
        val expires=offer.getLong("expires_at");require(expires in Instant.now().epochSecond..Instant.now().epochSecond+15*86400)
        progress("Log auf diesem Telefon mit OpenPGP verschlüsseln …")
        val ciphertext=withContext(Dispatchers.Default){
            val plain=JSONObject().put("format","phoenix-nfc-upload-v1").put("report_id",id).put("nonce",nonce)
                .put("created_at",Instant.now().toString()).put("report",report).toString().toByteArray(Charsets.UTF_8)
            try{NfcUploadCrypto.encrypt(context.assets.open("nfc-diagnostics-recipient.asc").use{it.readBytes()},plain)}finally{plain.fill(0)}
        }
        try{
            require(ciphertext.size<=offer.getInt("max_bytes"));currentCoroutineContext().ensureActive();progress("Verschlüsselte Datei übertragen …")
            val receipt=retry{request("PUT","/v1/nfc-reports/$id",ciphertext,nonce)}
            require(receipt.getString("id")==id&&receipt.getString("sha256")==NfcUploadCrypto.sha256(ciphertext)
                &&receipt.getInt("bytes")==ciphertext.size&&receipt.getLong("expires_at")==expires)
            return NfcUploadReceipt(id,expires,receipt.getString("sha256"))
        }finally{ciphertext.fill(0)}
    }
    suspend fun delete(id:String){
        require(UUID.fromString(id).toString()==id)
        val result=retry{request("DELETE","/v1/nfc-reports/$id")}
        check(result.getBoolean("deleted")&&result.getString("id")==id){"Löschung nicht bestätigt"}
    }
}
