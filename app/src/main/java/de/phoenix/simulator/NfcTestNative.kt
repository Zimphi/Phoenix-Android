// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import org.json.JSONObject

/** JNI only; PACE, SM, PA, CA and TA run in the pinned separately licensed Rust cores. */
internal object NfcTestNative {
    init{check(NativeCore.available){"Native Core-Bibliothek fehlt"}}
    private external fun prepare(kind:String):String
    external fun transmit(command:ByteArray):ByteArray
    external fun reset()
    external fun close()
    private external fun inspect(material:String,transport:NfcDocumentExchange):String
    fun prepareCard(kind:String):JSONObject=checked{prepare(kind)}
    fun inspectCard(material:JSONObject,exchange:NfcDocumentExchange):JSONObject=checked{inspect(material.toString(),exchange)}
    private fun checked(call:()->String):JSONObject {
        val value=try{JSONObject(call())}catch(error:LinkageError){throw IllegalStateException("Native NFC-Testkomponente muss aktualisiert werden",error)}
        check(!value.has("error")){"Native Protokollprüfung nicht erfolgreich: ${value.optString("error").take(240)}"}
        return value
    }
}

/** Callbacks invoked only by the existing native terminal while IsoDep remains on its worker. */
internal class NfcDocumentExchange(private val transmit:(ByteArray)->ByteArray) {
    @Suppress("unused") fun exchange(command:ByteArray):ByteArray=transmit(command)
}
