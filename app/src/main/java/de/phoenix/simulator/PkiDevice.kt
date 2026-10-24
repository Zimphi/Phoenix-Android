package de.phoenix.simulator

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Independent installation identity; no user-auth prompt, private key never exported. */
internal class PkiDevice(private val context:Context) {
    @Volatile private var verifiedAt=0L
    fun verifiedConnection()=connected()&&verifiedAt>0&&android.os.SystemClock.elapsedRealtime()-verifiedAt<300000
    private val preferences=context.getSharedPreferences("phoenix-pki-device-v1",Context.MODE_PRIVATE)
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    private fun b64(value:ByteArray)=Base64.encodeToString(value,Base64.NO_WRAP)
    private fun hash(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString(""){"%02x".format(it)}
    private fun random()=ByteArray(24).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
    private fun message(vararg values:Any)=JSONArray().apply{values.forEach{put(it)}}.toString().replace("\\/","/").toByteArray(Charsets.UTF_8)
    private fun sign(alias:String,bytes:ByteArray)=b64(Signature.getInstance("SHA256withECDSA").run{
        initSign(store().getKey(alias,null) as java.security.PrivateKey);update(bytes);sign()
    })
    private fun createKey():String {
        val alias="phoenix-pki-device-${UUID.randomUUID()}"
        try {
            val spec=KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false).build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").run{initialize(spec);generateKeyPair()}
            val key=store().getKey(alias,null) as java.security.PrivateKey
            val info=KeyFactory.getInstance(key.algorithm,"AndroidKeyStore").getKeySpec(key,KeyInfo::class.java)
            val hardware=if(Build.VERSION.SDK_INT>=31)info.securityLevel in listOf(KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,KeyProperties.SECURITY_LEVEL_STRONGBOX) else info.isInsideSecureHardware
            check(hardware&&!info.isUserAuthenticationRequired){"Hardwaregestützter PKI-Geräteschlüssel nicht verfügbar"}
            return alias
        }catch(error:Exception){store().deleteEntry(alias);throw error}
    }
    private fun request(origin:String,path:String,body:JSONObject,headers:Map<String,String> = emptyMap()):JSONObject {
        val connection=URL(origin+path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod="POST";connection.instanceFollowRedirects=false
            connection.connectTimeout=10000;connection.readTimeout=15000;connection.useCaches=false;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            headers.forEach{(key,value)->connection.setRequestProperty(key,value)}
            connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=connection.responseCode
            check(code==200){if(code==401)"Gerätezugang abgelehnt oder QR-Code abgelaufen" else "PKI-Gerätedienst nicht verfügbar"}
            check(connection.contentType?.startsWith("application/json")==true){"Ungültige Serverantwort"}
            val bytes=connection.inputStream.use{input->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true){val count=input.read(buffer);if(count<0)break;check(out.size()+count<=65536){"Serverantwort zu groß"};out.write(buffer,0,count)}
                out.toByteArray()
            }
            return JSONObject(String(bytes,Charsets.UTF_8))
        }finally{connection.disconnect()}
    }
    @Synchronized fun enroll(raw:String):String {
        check(!connected()){"Bestehenden Gerätezugang zuerst trennen oder lokal entfernen"}
        val qr=Uri.parse(raw)
        require(qr.scheme=="phoenix"&&qr.host=="device"&&qr.path.isNullOrEmpty()&&qr.userInfo==null&&qr.port == -1
            &&qr.queryParameterNames==setOf("server","environment")){"Kein Phoenix-PKI-QR-Code"}
        val origin=qr.getQueryParameter("server")?:error("Server fehlt")
        val environment=qr.getQueryParameter("environment")?:error("Umgebung fehlt")
        val expected=when(environment){"production"->"https://epassport-simulator.de";"preview"->"https://preview.epassport-simulator.de";else->error("Unbekannte Umgebung")}
        require(origin==expected&&qr.getQueryParameters("server").size==1&&qr.getQueryParameters("environment").size==1){"Unzulässiger Phoenix-Server"}
        val fragment=qr.fragment?:error("Einmalberechtigung fehlt")
        require(fragment.matches(Regex("code=[A-Za-z0-9_-]{43}"))){"Ungültige Einmalberechtigung"}
        val code=fragment.removePrefix("code=");val codeHash=hash(code.toByteArray(Charsets.US_ASCII))
        check(!preferences.contains("pending_code")||preferences.getString("pending_code",null)==codeHash){
            "Registrierung ausstehend: denselben QR-Code erneut scannen oder lokalen Zugang entfernen"
        }
        // Retain only the pending key/hash so rescanning after a lost response is idempotent.
        val alias=if(preferences.getString("pending_code",null)==codeHash)preferences.getString("pending_alias",null)?:error("Ausstehender Schlüssel fehlt") else createKey().also{
            check(preferences.edit().putString("pending_code",codeHash).putString("pending_alias",it).commit()){"Gerätespeicher nicht verfügbar"}
        }
        val publicKey=store().getCertificate(alias).publicKey.encoded
        val name=(Build.MANUFACTURER+" "+Build.MODEL).filterNot{it.isISOControl()}.take(64).ifBlank{"Android"}
        val nonce=random();val timestamp=System.currentTimeMillis()/1000
        val body=JSONObject().put("code",code).put("public_key",b64(publicKey)).put("name",name).put("nonce",nonce).put("timestamp",timestamp)
            .put("signature",sign(alias,message("phoenix-device-register-v1",environment,origin,code,hash(publicKey),name,nonce,timestamp)))
        val result=request(origin,"/api/auth/device/register",body)
        val id=result.getString("device_id")
        require(UUID.fromString(id).toString()==id&&result.getString("key_hash")==hash(publicKey)&&result.getString("environment")==environment){"Falsche Gerätezuordnung"}
        check(preferences.edit().putString("id",id).putString("origin",origin).putString("environment",environment).putString("alias",alias)
            .remove("pending_code").remove("pending_alias").commit()){"Gerätezuordnung konnte nicht gespeichert werden"}
        return "Gerät registriert. Zentralen PKI-Status prüfen."
    }
    // Snapshot getters must not wait for a synchronized HTTPS operation on the UI thread.
    fun identity():JSONObject {
        val saved=preferences.all
        return runCatching{JSONObject(saved["account"] as? String?:"{}")}.getOrDefault(JSONObject()).put("user_id",saved["user_id"] as? String?:"")
    }
    companion object {
        internal fun identityFromStatus(result:JSONObject):JSONObject {
            check(result.getBoolean("authenticated")){"Geräteanmeldung fehlgeschlagen"}
            val owner=result.getString("user_id")
            require(UUID.fromString(owner).toString()==owner){"Benutzerkennung fehlt"}
            val account=result.optJSONObject("account")
            require(account==null||account.getString("user_id")==owner){"Falsche Benutzerzuordnung"}
            val identity=JSONObject().put("user_id",owner)
            val pseudonym=account?.optString("account_id")
            if(pseudonym?.matches(Regex("phx_[0-9a-f]{64}"))==true)identity.put("account_id",pseudonym)
            for(key in listOf("username","display_name","email")) {
                val value=account?.opt(key)
                if(value is String&&value.length<=254&&!value.any{it.isISOControl()}&&value.isNotBlank())identity.put(key,value)
            }
            return identity
        }
    }
    fun userId()=preferences.getString("user_id",null)
    fun environment()=preferences.getString("environment",null)
    fun connected()=preferences.contains("id")
    fun hasLocalKey()=connected()||preferences.contains("pending_alias")
    @Synchronized fun forget():String {
        // A lost server response must not trap an unusable local connection.
        listOf("alias","pending_alias").mapNotNull{preferences.getString(it,null)}.distinct().forEach{store().deleteEntry(it)}
        check(preferences.edit().clear().commit()){"Lokaler Speicher nicht verfügbar"}
        return "Lokaler Schlüssel entfernt. Eine noch aktive Gerätefreigabe bitte in den Web-Einstellungen sperren."
    }
    private fun authenticated(action:String):JSONObject {
        val id=preferences.getString("id",null)?:error("Noch nicht verbunden")
        val origin=preferences.getString("origin",null)?:error("Server fehlt")
        val environment=preferences.getString("environment",null)?:error("Umgebung fehlt")
        require(origin==if(environment=="preview")"https://preview.epassport-simulator.de" else "https://epassport-simulator.de"){"Unzulässiger Server"}
        val alias=preferences.getString("alias",null)?:error("Geräteschlüssel fehlt")
        val nonce=random();val timestamp=System.currentTimeMillis()/1000;val path="/api/auth/device/$action"
        val signature=sign(alias,message("phoenix-device-request-v1",environment,"POST",origin+path,id,nonce,timestamp,hash("{}".toByteArray())))
        return request(origin,path,JSONObject(),mapOf("Authorization" to "PhoenixDevice $id","X-Phoenix-Nonce" to nonce,
            "X-Phoenix-Timestamp" to timestamp.toString(),"X-Phoenix-Signature" to signature))
    }
    @Synchronized internal fun diagnosticsProof(origin:String,method:String,path:String,bodyHash:String,uploadNonce:String):Map<String,String> {
        val id=preferences.getString("id",null)?:error("Bitte zuerst in den Einstellungen bei Phoenix anmelden")
        val environment=preferences.getString("environment",null)?:error("Anmeldeumgebung fehlt")
        val authOrigin=when(environment){
            "production"->"https://epassport-simulator.de"
            "preview"->"https://preview.epassport-simulator.de"
            else->error("Unbekannte Anmeldeumgebung")
        }
        check(preferences.getString("origin",null)==authOrigin)
        val alias=preferences.getString("alias",null)?:error("Geräteschlüssel fehlt")
        val salt=NfcUploadProof.salt();val timestamp=System.currentTimeMillis()/1000
        val nonce=NfcUploadProof.nonce(origin,method,path,bodyHash,salt,uploadNonce)
        val signature=sign(alias,message("phoenix-device-request-v1",environment,"POST",authOrigin+"/api/auth/device/status",id,nonce,timestamp,hash("{}".toByteArray())))
        return mapOf("Authorization" to "PhoenixDevice $id","X-Phoenix-Nonce" to nonce,"X-Phoenix-Timestamp" to timestamp.toString(),
            "X-Phoenix-Signature" to signature,"X-Phoenix-Diagnostics-Environment" to environment,"X-Phoenix-Diagnostics-Salt" to salt,"X-Report-Nonce" to uploadNonce)
    }
    @Synchronized fun status():String {
        verifiedAt=0
        val result=authenticated("status")
        val identity=identityFromStatus(result)
        check(preferences.edit().putString("user_id",identity.getString("user_id")).putString("account",identity.toString()).commit()){"Benutzerkennung konnte nicht gespeichert werden"}
        verifiedAt=android.os.SystemClock.elapsedRealtime()
        return if(result.optBoolean("pki_ready"))"Angemeldet · Zentrale PKI verfügbar" else "Angemeldet · Zentrale PKI noch nicht verfügbar"
    }
    @Synchronized fun disconnect():String {
        check(authenticated("disconnect").getBoolean("disconnected")){"Sperrung nicht bestätigt"}
        val alias=preferences.getString("alias",null);check(preferences.edit().clear().commit()){"Lokaler Speicher nicht verfügbar"}
        if(alias!=null)store().deleteEntry(alias)
        return "Gerätezugang gesperrt"
    }
}
