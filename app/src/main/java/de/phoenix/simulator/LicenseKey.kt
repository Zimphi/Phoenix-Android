package de.phoenix.simulator

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** The private activation key never leaves Android Keystore. No profile data are stored here. */
internal object LicenseKey {
    private const val ALIAS="phoenix-license-binding-v1"
    data class Proof(val publicKey:ByteArray,val signature:ByteArray,val strongBox:Boolean)
    @Synchronized fun prove(context:Context,challenge:ByteArray):Proof {
        require(challenge.size in 32..256){"Ungültige Geräte-Challenge"}
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(!store.containsAlias(ALIAS)){
            val strongBox=Build.VERSION.SDK_INT>=28&&context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
            if(Build.VERSION.SDK_INT>=28&&strongBox){
                try{generate(true)}catch(error:StrongBoxUnavailableException){generate(false)}
            }else generate(false)
        }
        val privateKey=store.getKey(ALIAS,null) as java.security.PrivateKey
        val info=KeyFactory.getInstance(privateKey.algorithm,"AndroidKeyStore").getKeySpec(privateKey,KeyInfo::class.java)
        val hardware=if(Build.VERSION.SDK_INT>=31)info.securityLevel in listOf(KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT,KeyProperties.SECURITY_LEVEL_STRONGBOX) else info.isInsideSecureHardware
        check(hardware){"Hardwaregestützte Gerätebindung ist nicht verfügbar"}
        val signature=Signature.getInstance("SHA256withECDSA").run{initSign(privateKey);update(challenge);sign()}
        return Proof(store.getCertificate(ALIAS).publicKey.encoded,signature,Build.VERSION.SDK_INT>=31&&info.securityLevel==KeyProperties.SECURITY_LEVEL_STRONGBOX)
    }
    private fun generate(strongBox:Boolean){
        val spec=KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false)
        if(Build.VERSION.SDK_INT>=28)spec.setIsStrongBoxBacked(strongBox)
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").run{initialize(spec.build());generateKeyPair()}
    }
}
