package de.phoenix.simulator

import android.app.Instrumentation
import android.os.Bundle
import android.util.Base64
import org.json.JSONObject

class MobileAuthInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        val result=Bundle()
        try {
            val attempt="a".repeat(64);val token="b".repeat(64);val origin="https://epassport-simulator.de"
            val valid="$origin/auth/android#attempt=$attempt&token_hash=$token"
            check(MobileAuth.callback(valid,origin,attempt)==token)
            for(raw in listOf(valid.replace("https:","http:"),valid.replace("epassport-simulator.de","evil.invalid"),
                valid.replace("/auth/android","/auth/android/"),valid.replace("#","?"),valid.replace("attempt=$attempt","attempt=${"c".repeat(64)}"),
                "$valid&extra=x",valid.replace("#","?extra=x#"),valid.replace("https://","https://user@"),valid.replace(".de/",".de:443/"),valid.replace(token,"short"))){
                check(runCatching{MobileAuth.callback(raw,origin,attempt)}.isFailure){"Untrusted app link accepted"}
            }
            check(runCatching{MobileAuth.callback(valid,origin,"")}.isFailure)
            check(MobileAuth.attempt("synthetic")=="b3cc0475bb78a5026098858e9889acf666d31062d513d303314eca31d36e72f2")
            // Device-protected storage is a separate test namespace; real pending signup remains untouched.
            val context=targetContext.createDeviceProtectedStorageContext()
            val pending=PendingCloudSignup(context)
            try {
                val fixture=JSONObject().put("email","synthetic@example.invalid").put("secret","synthetic-only").put("environment","preview").put("expires",System.currentTimeMillis()+900000)
                pending.save(fixture);check(pending.read().toString()==fixture.toString())
                pending.clear();check(pending.read()==null)
                check(pending.find("synthetic@example.invalid","preview").toString()==fixture.toString())
                check(pending.find("other@example.invalid","preview")==null)
                check(pending.find("synthetic@example.invalid","production")==null)
                pending.save(fixture)
                val preferences=context.getSharedPreferences("phoenix-pending-cloud-v1",0)
                val encrypted=preferences.getString("state",null)!!
                check(!encrypted.contains("synthetic-only"))
                val damaged=Base64.decode(encrypted,Base64.NO_WRAP);damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
                check(preferences.edit().putString("state",Base64.encodeToString(damaged,Base64.NO_WRAP)).commit())
                check(pending.read()==null)
            }finally{pending.remove("synthetic@example.invalid","preview");pending.clear()}
            result.putString("stream","PASS: strict App Link origin/path/attempt/token validation; SHA-256 attempt binding; AES-GCM round trip and tamper rejection.")
            finish(-1,result)
        }catch(error:Throwable){result.putString("stream","FAIL: ${error.javaClass.simpleName}: ${error.message}");finish(1,result)}
    }
}
