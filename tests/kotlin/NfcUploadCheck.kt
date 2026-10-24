package de.phoenix.simulator

import java.io.File

fun main(args:Array<String>){
    val salts=(1..100).map{NfcUploadProof.salt()}
    check(salts.toSet().size==100 && salts.all{it.matches(Regex("[A-Za-z0-9_-]{43}"))})
    val certificate=File(args[0]).readBytes()
    val plaintext="""{"format":"phoenix-nfc-upload-v1","report_id":"00000000-0000-0000-0000-000000000001","nonce":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","report":{"schema":"phoenix-nfc-suite-v1","synthetic":true}}""".toByteArray()
    val first=NfcUploadCrypto.encrypt(certificate,plaintext)
    val second=NfcUploadCrypto.encrypt(certificate,plaintext)
    check(!first.contentEquals(second)&&!String(first,Charsets.ISO_8859_1).contains("synthetic"))
    check(runCatching{NfcUploadCrypto.encrypt(certificate.copyOf().also{it[100]=(it[100].toInt() xor 1).toByte()},plaintext)}.isFailure)
    check(runCatching{NfcUploadCrypto.encrypt(certificate,plaintext,0)}.isFailure)
    check(runCatching{NfcUploadCrypto.encrypt(certificate,plaintext,2524608000000)}.isFailure)
    check(runCatching{NfcUploadCrypto.encrypt(certificate,ByteArray(1048577))}.isFailure)
    val bodyHash=NfcUploadCrypto.sha256("{}".toByteArray());val salt="A".repeat(43)
    val nonce=NfcUploadProof.nonce("https://phoenix-nfc-diagnostics.phoenix-license-relay.workers.dev","POST","/v1/nfc-reports",bodyHash,salt,"")
    check(nonce=="c9b770baa15c01aeeff1db00fc08d5cb2e01537db8497ebec560efe3e57205fe")
    check(nonce!=NfcUploadProof.nonce("https://phoenix-nfc-diagnostics.phoenix-license-relay.workers.dev","PUT","/v1/nfc-reports",bodyHash,salt,""))
    check(nonce!=NfcUploadProof.nonce("https://phoenix-nfc-diagnostics.phoenix-license-relay.workers.dev","POST","/v1/nfc-reports",bodyHash,salt,salt))
    for(origin in listOf("http://diagnostics.epassport-simulator.de","https://x.test/path","https://x.test\n"))
        check(runCatching{NfcUploadProof.nonce(origin,"POST","/v1/nfc-reports",bodyHash,salt,"")}.isFailure)
    File(args[1]).writeBytes(first);File(args[2]).writeBytes(plaintext)
    println("Pinned OpenPGP encryption and cross-language request-binding checks passed")
}
