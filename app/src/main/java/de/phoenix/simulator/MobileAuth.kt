package de.phoenix.simulator

import android.app.Activity
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.*
import androidx.credentials.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

/** Pending mail proof and setup tokens live only for this Activity's lifetime. */
internal class MobileAuth(private val activity:Activity) {
    var environment by mutableStateOf("production"); private set
    var email by mutableStateOf(""); private set
    var phase by mutableStateOf("idle"); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var completed by mutableIntStateOf(0); private set
    var opened by mutableIntStateOf(0); private set
    var recovered by mutableStateOf(false); private set
    private var installationSecret:String?=null
    private var setupToken:String?=null
    private var expiresAt=0L
    private var pairingUri:String?=null
    private val scope=MainScope()
    private val credentials=CredentialManager.create(activity)
    private val device=PkiDevice(activity.applicationContext)
    private val saved=PendingCloudSignup(activity.applicationContext)
    private val origin get()=if(environment=="preview")"https://preview.epassport-simulator.de" else "https://epassport-simulator.de"
    fun hasPending()=installationSecret!=null||pairingUri!=null
    init {
        saved.read()?.let{state->
            if(state.optLong("expires")>System.currentTimeMillis()-86400000&&state.optString("environment") in listOf("production","preview")){
                environment=state.getString("environment");email=state.optString("email");expiresAt=state.getLong("expires")
                installationSecret=state.optString("secret").takeIf{it.isNotBlank()};pairingUri=state.optString("pairing").takeIf{it.isNotBlank()}
                phase=if(pairingUri!=null)"enroll" else "mail";recovered=true
                message="Offene Registrierung wiederhergestellt."
            }else saved.clear()
        }
    }
    private fun savePending(){saved.save(JSONObject().put("environment",environment).put("email",email).put("secret",installationSecret?:"")
        .put("pairing",pairingUri?:"").put("expires",expiresAt))}

    fun selectEnvironment(value:String){check(!busy&&phase=="idle"&&!hasPending());require(value in listOf("preview","production"));environment=value}
    fun editEmail(value:String){if(!busy&&phase=="idle"&&!hasPending())email=value.take(254)}
    private fun perform(action:suspend ()->Unit){
        if(busy)return
        busy=true;message=""
        scope.launch {
            try{check(!device.connected()||phase=="enroll"){"Bestehenden Gerätezugang zuerst abmelden"};action()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){message=when(error){
                is androidx.credentials.exceptions.GetCredentialCancellationException,is androidx.credentials.exceptions.CreateCredentialCancellationException->"Passkey-Vorgang abgebrochen. Du kannst es erneut versuchen."
                else->"Anmeldung nicht abgeschlossen. Prüfe Code, Verbindung und Kontoeinrichtung und versuche es erneut."
            }}finally{busy=false;completed++}
        }
    }
    private suspend fun request(path:String,body:JSONObject):JSONObject=withContext(Dispatchers.IO){
        val connection=URL(origin+"/api/auth/mobile/"+path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod="POST";connection.instanceFollowRedirects=false;connection.useCaches=false
            connection.connectTimeout=10000;connection.readTimeout=20000;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            check(connection.responseCode==200){"Authentication rejected"}
            check(connection.contentType?.startsWith("application/json")==true)
            val bytes=connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true){val n=input.read(buffer);if(n<0)break;check(output.size()+n<=65536);output.write(buffer,0,n)}
                output.toByteArray()
            }
            JSONObject(String(bytes,Charsets.UTF_8))
        } finally{connection.disconnect()}
    }
    fun signup()=perform {
        check(phase in listOf("idle","mail"))
        email=email.trim().lowercase(java.util.Locale.ROOT)
        require(email.length in 3..254&&android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
        if(installationSecret==null)saved.find(email,environment)?.let{state->installationSecret=state.optString("secret").takeIf{it.isNotBlank()};expiresAt=state.optLong("expires")}
        val secret=installationSecret?:Base64.encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)},Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        if(installationSecret==null||expiresAt<=System.currentTimeMillis())expiresAt=System.currentTimeMillis()+900000
        installationSecret=secret;phase="mail";savePending()
        val sent=request("signup",JSONObject().put("email",email).put("secret",secret))
        check(sent.getString("attempt")==attempt(secret))
        phase="mail";message="Öffne den Link in deiner E-Mail oder gib den Einmalcode ein."
    }
    private suspend fun confirm(otp:String?=null,tokenHash:String?=null){
        check(phase=="mail"&&System.currentTimeMillis()<expiresAt)
        val secret=installationSecret?:error("Registration expired")
        val body=JSONObject().put("email",email).put("secret",secret)
        if(otp!=null){require(otp.matches(Regex("[0-9]{6,10}")));body.put("otp",otp)}
        else if(tokenHash!=null){require(tokenHash.matches(Regex("[0-9a-fA-F]{64}")));body.put("token_hash",tokenHash)}
        setupToken=request("confirm",body).getString("setup_token")
        phase="passkey";recovered=false;message="E-Mail bestätigt. Erstelle jetzt deinen Passkey."
    }
    fun confirmCode(code:String)=perform{confirm(otp=code)}
    fun resumeRegistration()=perform{confirm()}
    fun acceptLink(raw:String){
        opened++
        val pending=installationSecret
        val token=runCatching{callback(raw,origin,pending?.let(::attempt)?:"")}.getOrNull()
        if(token==null||phase!="mail"||busy){message="Dieser Link gehört zu keiner offenen Registrierung in dieser App. Starte die Registrierung hier oder gib den Code ein.";return}
        perform{confirm(tokenHash=token)}
    }
    fun createPasskey()=perform{
        if(android.os.Build.VERSION.SDK_INT<28)throw IllegalStateException("Passkeys benötigen Android 9 oder neuer")
        check(phase=="passkey"&&System.currentTimeMillis()<expiresAt)
        val token=setupToken?:error("Setup expired")
        val options=request("registration/options",JSONObject().put("token",token))
        if(!options.optBoolean("registered")) {
            val publicKey=options.getJSONObject("options")
            publicKey.put("authenticatorSelection",(publicKey.optJSONObject("authenticatorSelection")?:JSONObject()).put("userVerification","required"))
            val response=credentials.createCredential(activity,CreatePublicKeyCredentialRequest(publicKey.toString())) as CreatePublicKeyCredentialResponse
            request("registration/verify",JSONObject().put("token",token).put("challenge_id",options.getString("challenge_id"))
                .put("credential",JSONObject(response.registrationResponseJson)))
        }
        setupToken=null;phase="login"
        // Setup advances the server's second-resolution password epoch.
        delay(1100)
        loginPasskey()
    }
    private suspend fun loginPasskey(again:Boolean=false){
        if(android.os.Build.VERSION.SDK_INT<28)throw IllegalStateException("Passkeys benötigen Android 9 oder neuer")
        val challenge=request("login/options",JSONObject())
        val options=challenge.getJSONObject("options").put("userVerification","required")
        val result=credentials.getCredential(activity,GetCredentialRequest(listOf(GetPublicKeyCredentialOption(options.toString()))))
        val credential=result.credential as? PublicKeyCredential?:error("Unexpected credential")
        val resultBody=request("login/verify",JSONObject().put("challenge_id",challenge.getString("challenge_id"))
            .put("credential",JSONObject(credential.authenticationResponseJson)))
        if(resultBody.optBoolean("sign_in_again")){
            check(!again);delay(1100);loginPasskey(true);return
        }
        pairingUri=resultBody.getString("pairing_uri");expiresAt=System.currentTimeMillis()+120000;phase="enroll";savePending()
        finishEnrollment()
    }
    private suspend fun finishEnrollment(){
        val uri=pairingUri?:error("No pending enrollment")
        withContext(Dispatchers.IO){if(!device.connected())device.enroll(uri)}
        clearPending();message="Mit Phoenix verbunden."
        withContext(Dispatchers.IO){runCatching{device.status()}}
    }
    fun login()=perform{loginPasskey()}
    fun retryEnrollment()=perform{finishEnrollment()}
    fun reset(){if(busy)return;setupToken=null;phase="idle";message=if(hasPending())"Deine Registrierung bleibt für später gespeichert." else ""}
    fun resumePending(){if(busy||!hasPending())return;phase=if(pairingUri!=null)"enroll" else "mail";recovered=true;message=""}
    fun changeEmail(){if(busy)return;saved.clear();installationSecret=null;setupToken=null;pairingUri=null;expiresAt=0;phase="idle";email="";recovered=false;message=""}
    private fun clearPending(){saved.remove(email,environment);installationSecret=null;setupToken=null;pairingUri=null;expiresAt=0;phase="idle";message="";recovered=false;saved.clear()}
    fun close(){scope.cancel();installationSecret=null;setupToken=null;pairingUri=null}
    companion object {
        internal fun attempt(secret:String)=MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.US_ASCII)).joinToString(""){"%02x".format(it)}
        internal fun callback(raw:String,origin:String,pending:String):String {
            require(raw.length<=2048&&pending.matches(Regex("[0-9a-f]{64}")))
            val uri=Uri.parse(raw)
            require(uri.scheme=="https"&&uri.authority==Uri.parse(origin).authority&&uri.userInfo==null&&uri.port == -1
                &&uri.path=="/auth/android"&&uri.encodedQuery==null)
            val fragment=uri.encodedFragment?:error("Missing proof")
            require(fragment.matches(Regex("attempt=[0-9a-f]{64}&token_hash=[0-9a-fA-F]{64}")))
            val parts=fragment.split('&');require(parts[0]=="attempt=$pending")
            return parts[1].removePrefix("token_hash=")
        }
    }
}
