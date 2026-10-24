// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

/** Canonical tuple contains only constrained ASCII fields; matches the Worker JSON tuple. */
internal object NfcUploadProof {
    fun salt():String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(32).also{java.security.SecureRandom().nextBytes(it)})
    fun nonce(origin:String,method:String,path:String,bodyHash:String,salt:String,uploadNonce:String):String {
        require(origin.matches(Regex("https://[a-z0-9.-]+")))
        require(method in listOf("POST","PUT","GET","DELETE"))
        require(path.matches(Regex("/v1/nfc-reports(/[0-9a-f-]{36})?")))
        require(bodyHash.matches(Regex("[0-9a-f]{64}"))&&salt.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(uploadNonce.isEmpty()||uploadNonce.matches(Regex("[A-Za-z0-9_-]{43}")))
        return NfcUploadCrypto.sha256("[\"phoenix-diagnostics-v1\",\"$origin\",\"$method\",\"$path\",\"$bodyHash\",\"$salt\",\"$uploadNonce\"]".toByteArray(Charsets.US_ASCII))
    }
}
