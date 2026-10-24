// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Synthetic transport probe, deliberately separate from the licensed document protocols. */
internal class NfcTestProtocol(key:ByteArray,private val direction:Int):AutoCloseable {
    private val secret=key.clone()
    private var next=0
    var selected=false;private set
    var control=false;private set
    var trigger=false;private set
    var entries=0;private set
    val observations=mutableListOf<Map<String,Any>>()
    var deactivated=false

    init {require(key.size==32 && direction in 0..1)}

    private fun header(step:Int)=if(step==0)byteArrayOf(12,0x84.toByte(),0,0) else byteArrayOf(12,0xa4.toByte(),4,12)
    private fun payload(step:Int)=byteArrayOf(direction.toByte(),step.toByte())+"PHOENIX-NFC".toByteArray(Charsets.US_ASCII)
    private fun crypt(mode:Int,step:Int,response:Boolean,data:ByteArray):ByteArray {
        val nonce=ByteArray(12).apply{this[0]=direction.toByte();this[1]=if(response)1 else 0;this[11]=step.toByte()}
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode,SecretKeySpec(secret,"AES"),GCMParameterSpec(128,nonce))
            updateAAD(header(step)+byteArrayOf(if(response)1 else 0))
            doFinal(data)
        }
    }
    fun command(step:Int):ByteArray {
        require(step in 0..1)
        val encrypted=crypt(Cipher.ENCRYPT_MODE,step,false,payload(step))
        return header(step)+byteArrayOf(encrypted.size.toByte())+encrypted+byteArrayOf(0)
    }
    @Synchronized fun respond(apdu:ByteArray):ByteArray {
        entries++
        val result=process(apdu)
        if(observations.size<16)observations+=mapOf("header" to apdu.take(4).joinToString(""){"%02X".format(it)},"length" to apdu.size,
            "response_length" to result.size,"status" to (((result[result.size-2].toInt() and 255) shl 8) or (result.last().toInt() and 255)))
        return result
    }
    private fun process(apdu:ByteArray):ByteArray {
        if(deactivated)return status(0x6985)
        if(apdu.contentEquals(SELECT) && !selected){selected=true;return status(0x9000)}
        if(!selected || next>1)return status(0x6985)
        val step=next
        if(apdu.size!=35 || !apdu.copyOfRange(0,4).contentEquals(header(step)) || apdu[4]!=29.toByte() || apdu.last()!=0.toByte())return status(0x6700)
        val plain=runCatching{crypt(Cipher.DECRYPT_MODE,step,false,apdu.copyOfRange(5,34))}.getOrNull()?:return status(0x6982)
        if(!MessageDigest.isEqual(plain,payload(step)))return status(0x6982)
        if(step==0)control=true else trigger=true
        next++
        return crypt(Cipher.ENCRYPT_MODE,step,true,plain)+status(0x9000)
    }
    fun verify(response:ByteArray,step:Int):Boolean {
        if(response.size!=31 || !response.takeLast(2).toByteArray().contentEquals(status(0x9000)))return false
        return runCatching{MessageDigest.isEqual(crypt(Cipher.DECRYPT_MODE,step,true,response.copyOfRange(0,29)),payload(step))}.getOrDefault(false)
    }
    @Synchronized override fun close(){deactivated=true;secret.fill(0)}
    companion object {
        // Proprietary diagnostic AID; never claims the eID or ePassport application.
        val SELECT=byteArrayOf(0,0xa4.toByte(),4,0,8,0xf0.toByte(),0x50,0x48,0x4f,0x45,0x4e,0x49,0x58,0)
        fun status(value:Int)=byteArrayOf((value ushr 8).toByte(),value.toByte())
    }
}
