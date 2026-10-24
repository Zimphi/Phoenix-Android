// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import javax.net.ssl.*

/** QR-pinned TLS, one peer, no trust store or pairing material persisted. */
internal object NfcTestTls {
    private val random=SecureRandom()
    fun bytes(size:Int)=ByteArray(size).also(random::nextBytes)
    fun digest(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value)

    class Channel(private val socket:SSLSocket):AutoCloseable {
        private val input=DataInputStream(socket.inputStream)
        private val output=DataOutputStream(socket.outputStream)
        fun read():ByteArray {val size=input.readInt();require(size in 1..32768);return ByteArray(size).also(input::readFully)}
        @Synchronized fun write(data:ByteArray){require(data.size in 1..32768);output.writeInt(data.size);output.write(data);output.flush()}
        override fun close(){socket.close()}
    }
    class Listener(private val socket:SSLServerSocket,val pin:ByteArray,val token:ByteArray):AutoCloseable {
        val port get()=socket.localPort
        private val expires=System.nanoTime()+300000000000L
        private var attempts=0
        @Volatile private var pending:SSLSocket?=null
        fun accept(code:String?=null):Channel {
            require(attempts++<5)
            val remaining=(expires-System.nanoTime())/1000000;require(remaining>0)
            socket.soTimeout=minOf(300000,remaining).toInt()
            val peer=socket.accept() as SSLSocket;pending=peer
            try {
                peer.soTimeout=10000;peer.startHandshake()
                val channel=Channel(peer)
                when(DataInputStream(peer.inputStream).readUnsignedByte()){
                    1->{val incoming=ByteArray(32);DataInputStream(peer.inputStream).readFully(incoming);require(MessageDigest.isEqual(incoming,token)){"Kopplung ungültig"}}
                    2->NfcCodePairing.authenticate(channel,requireNotNull(code),true,pin)
                    else->error("Invalid pairing method")
                }
                require(System.nanoTime()<expires)
                DataOutputStream(peer.outputStream).run{writeByte(1);flush()}
                peer.soTimeout=45000;pending=null
                return channel
            }catch(error:Exception){peer.close();pending=null;throw error}
        }
        override fun close(){socket.close();pending?.close();pending=null;token.fill(0)}
    }
    fun listen(address:InetAddress):Listener {
        val pair=KeyPairGenerator.getInstance("EC").run{initialize(256,random);generateKeyPair()}
        val alg=der(0x30,byteArrayOf(6,8,0x2a,0x86.toByte(),0x48,0xce.toByte(),0x3d,4,3,2))
        val name=der(0x30,der(0x31,der(0x30,byteArrayOf(6,3,0x55,4,3)+der(12,"Phoenix NFC Test".toByteArray()))))
        val format=SimpleDateFormat("yyyyMMddHHmmss'Z'",java.util.Locale.ROOT).apply{timeZone=TimeZone.getTimeZone("UTC")}
        val validity=der(0x30,der(24,format.format(Date(System.currentTimeMillis()-300000)).toByteArray())+
            der(24,format.format(Date(System.currentTimeMillis()+3600000)).toByteArray()))
        val serial=der(2,java.math.BigInteger(1,bytes(16)).toByteArray())
        val body=der(0x30,der(0xa0,der(2,byteArrayOf(2)))+serial+alg+name+validity+name+pair.public.encoded)
        val signature=Signature.getInstance("SHA256withECDSA").run{initSign(pair.private);update(body);sign()}
        val encoded=der(0x30,body+alg+der(3,byteArrayOf(0)+signature))
        val cert=CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream()) as X509Certificate
        cert.verify(pair.public)
        val password=bytes(24).map{('a'.code+(it.toInt() and 15)).toChar()}.toCharArray()
        val store=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null);setKeyEntry("session",pair.private,password,arrayOf(cert))}
        val manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(store,password)}
        password.fill('\u0000')
        val tls=SSLContext.getInstance("TLSv1.2").apply{init(manager.keyManagers,null,random)}
        val socket=tls.serverSocketFactory.createServerSocket(0,1,address) as SSLServerSocket
        socket.enabledProtocols=arrayOf("TLSv1.2");socket.soTimeout=300000
        return Listener(socket,digest(cert.encoded),bytes(32))
    }
    fun validateEndpoint(host:String,port:Int) {
        require(port in 1..65535)
        require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
        val parts=host.split('.').map(String::toInt);require(parts.all{it in 0..255})
        val address=InetAddress.getByAddress(parts.map(Int::toByte).toByteArray())
        require(address.isSiteLocalAddress || address.isLoopbackAddress){"Nur lokales WLAN erlaubt"}
    }
    fun connect(host:String,port:Int,pin:ByteArray,token:ByteArray):Channel {
        require(pin.size==32&&token.size==32)
        return connectAuthenticated(host,port,pin,token,null)
    }
    fun connectCode(host:String,port:Int,code:String):Channel {
        require(code.matches(Regex("[0-9]{6}")))
        return connectAuthenticated(host,port,null,null,code)
    }
    private fun connectAuthenticated(host:String,port:Int,pin:ByteArray?,token:ByteArray?,code:String?):Channel {
        validateEndpoint(host,port)
        val manager=object:X509TrustManager {
            override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String){throw CertificateException("Client certificate unsupported")}
            override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String){
                if(chain.size!=1 || (pin!=null&&!MessageDigest.isEqual(digest(chain[0].encoded),pin)))throw CertificateException("QR-Zertifikat stimmt nicht überein")
                chain[0].checkValidity()
            }
        }
        val tls=SSLContext.getInstance("TLSv1.2").apply{init(null,arrayOf(manager),random)}
        val socket=tls.socketFactory.createSocket() as SSLSocket
        try {
            socket.enabledProtocols=arrayOf("TLSv1.2");socket.connect(InetSocketAddress(host,port),8000);socket.soTimeout=8000
            socket.startHandshake()
            val channel=Channel(socket)
            if(code==null)DataOutputStream(socket.outputStream).run{writeByte(1);write(requireNotNull(token));flush()}
            else {
                DataOutputStream(socket.outputStream).run{writeByte(2);flush()}
                val certificate=socket.session.peerCertificates.single() as X509Certificate
                NfcCodePairing.authenticate(channel,code,false,digest(certificate.encoded))
            }
            require(DataInputStream(socket.inputStream).readUnsignedByte()==1)
            socket.soTimeout=45000
            return channel
        }catch(error:Exception){socket.close();throw error}
    }
    private fun der(tag:Int,data:ByteArray):ByteArray {
        val length=when{data.size<128->byteArrayOf(data.size.toByte());data.size<256->byteArrayOf(0x81.toByte(),data.size.toByte());else->byteArrayOf(0x82.toByte(),(data.size ushr 8).toByte(),data.size.toByte())}
        return byteArrayOf(tag.toByte())+length+data
    }
}
