package de.phoenix.simulator

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.MessageDigest

/** Restart recovery; Android backup is disabled and authentication tokens are never saved. */
internal class PendingCloudSignup(context:Context) {
    private val preferences=context.getSharedPreferences("phoenix-pending-cloud-v1",Context.MODE_PRIVATE)
    private val alias="phoenix-pending-cloud-v1"
    private fun entry(email:String,environment:String)="entry-"+MessageDigest.getInstance("SHA-256").digest((environment+":"+email).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        return store.getKey(alias,null) as? SecretKey?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generateKey()
        }
    }
    fun save(value:JSONObject){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD(alias.toByteArray(Charsets.US_ASCII))
        val bytes=cipher.iv+cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val encoded=Base64.encodeToString(bytes,Base64.NO_WRAP)
        val edit=preferences.edit().putString("state",encoded)
        for((name,stored) in preferences.all)if(name.startsWith("entry-")&&stored is String){
            val prior=decode(stored)
            if(prior==null||prior.optLong("expires")<=System.currentTimeMillis()-86400000)edit.remove(name)
        }
        if(value.has("email")&&value.has("environment"))edit.putString(entry(value.getString("email"),value.getString("environment")),encoded)
        check(edit.commit())
    }
    private fun decode(raw:String):JSONObject? {
        return runCatching{
            require(raw.length<=8192)
            val bytes=Base64.decode(raw,Base64.NO_WRAP);require(bytes.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            cipher.updateAAD(alias.toByteArray(Charsets.US_ASCII))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
        }.getOrNull()
    }
    fun read():JSONObject?=preferences.getString("state",null)?.let(::decode)
    fun find(email:String,environment:String):JSONObject?=preferences.getString(entry(email,environment),null)?.let(::decode)?.takeIf{
        it.optString("email")==email&&it.optString("environment")==environment&&it.optLong("expires")>System.currentTimeMillis()-86400000
    }
    fun remove(email:String,environment:String){check(preferences.edit().remove(entry(email,environment)).commit())}
    fun clear(){check(preferences.edit().remove("state").commit())}
}
