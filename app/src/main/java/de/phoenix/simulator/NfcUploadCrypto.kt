// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.jcajce.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date

/** Only the bundled, pinned public certificate is accepted; no network key lookup. */
internal object NfcUploadCrypto {
    const val PRIMARY="FD3DF3CC2F3B4D2116D413A5C59CD4AAABD99160"
    const val SUBKEY="0A0C0B92467C2A50EB9578DA64FE711F9A5E204A"
    private const val CERTIFICATE_SHA256="0b4bcd66ae5f6fa27479d31f50f486c10d47e61d75072f0ef97d5b8554a51b89"
    private val provider=BouncyCastleProvider()
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private fun fingerprint(key:PGPPublicKey)=key.fingerprint.joinToString(""){"%02X".format(it)}
    fun encrypt(certificate:ByteArray,plaintext:ByteArray,now:Long=System.currentTimeMillis()):ByteArray {
        require(plaintext.size in 1..1048576&&certificate.size<=16384)
        require(sha256(certificate)==CERTIFICATE_SHA256){"Empfängerschlüssel stimmt nicht mit der App überein"}
        val rings=PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(ByteArrayInputStream(certificate)),JcaKeyFingerprintCalculator())
        require(rings.size()==1)
        val ring=rings.keyRings.next();val primary=ring.publicKey
        require(fingerprint(primary)==PRIMARY&&primary.isMasterKey&&!primary.hasRevocation())
        // The whole certificate is pinned; still verify binding, key flags and expiry explicitly.
        fun validUntil(key:PGPPublicKey,seconds:Long):Boolean=now>=key.creationTime.time&&
            (seconds==0L||now<Math.addExact(key.creationTime.time,Math.multiplyExact(seconds,1000L)))
        require(validUntil(primary,primary.validSeconds)){"Empfängerschlüssel ist abgelaufen oder noch nicht gültig"}
        val key=ring.publicKeys.asSequence().single{fingerprint(it)==SUBKEY}
        require(key.isEncryptionKey&&!key.hasRevocation())
        val binding=key.getSignaturesOfType(PGPSignature.SUBKEY_BINDING).asSequence().filter{signature->
            signature.init(JcaPGPContentVerifierBuilderProvider().setProvider(provider),primary)
            signature.creationTime.time<=now&&signature.verifyCertification(primary,key)
        }.maxByOrNull{it.creationTime.time}?:error("Gültige Empfängerbindung fehlt")
        val packets=requireNotNull(binding.hashedSubPackets)
        require(packets.keyFlags and (KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE)!=0&&validUntil(key,packets.keyExpirationTime))
        val output=ByteArrayOutputStream();val random=SecureRandom()
        val encrypted=PGPEncryptedDataGenerator(JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
            .setWithIntegrityPacket(true).setSecureRandom(random).setProvider(provider))
        encrypted.addMethod(JcePublicKeyKeyEncryptionMethodGenerator(key).setProvider(provider).setSecureRandom(random))
        encrypted.open(output,ByteArray(65536)).use{stream->
            PGPLiteralDataGenerator().open(stream,PGPLiteralData.BINARY,"phoenix-nfc-report.json",plaintext.size.toLong(),Date(0)).use{it.write(plaintext)}
        }
        return output.toByteArray().also{require(it.size<=2097152)}
    }
}
