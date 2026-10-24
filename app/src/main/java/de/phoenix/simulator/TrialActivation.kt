package de.phoenix.simulator

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Existing first-start trial policy. Card APDUs never cross this connection. */
internal object TrialActivation {
    private const val ENDPOINT="https://phoenix-trial-hash-signing-prototype.phoenix-license-relay.workers.dev/v1/issue-trial-license"
    @Synchronized fun activate(terminal:Boolean=false) {
        val state=NativeCore.call(JSONObject().put("op",if(terminal)"trial_terminal_prepare" else "trial_prepare"))
        if(state.optBoolean("active"))return
        val bytes=state.getJSONObject("request").toString().toByteArray(Charsets.UTF_8)
        val connection=URL(ENDPOINT).openConnection() as HttpsURLConnection
        try{
            connection.requestMethod="POST";connection.instanceFollowRedirects=false
            connection.connectTimeout=15000;connection.readTimeout=20000;connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use{it.write(bytes)}
            check(connection.responseCode==200){"Testaktivierung derzeit nicht verfügbar"}
            val delivery=connection.inputStream.use{JSONObject(ProfileStore.read(it))}
            NativeCore.call(JSONObject().put("op",if(terminal)"trial_terminal_receive" else "trial_receive").put("delivery",delivery))
        }finally{connection.disconnect()}
    }
}
