package de.phoenix.simulator

import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val original=NfcTestAssessment.ORIGINAL
    fun verdict(model:String="SM-G991B",firmware:String="G991BXXSJHZC2",hash:String?=original,
                radio:Boolean=true,correlated:Boolean=true,control:Boolean=true,selected:Boolean=false,
                reached:Boolean=false,status:Int=0x6e00)=NfcTestAssessment.assess(model,firmware,hash,radio,correlated,control,selected,reached,status)
    check(verdict()=="known_signature")
    check(verdict(hash=NfcTestAssessment.PATCHED)=="unconfirmed")
    check(verdict(firmware="other")=="unconfirmed")
    check(verdict(model="other")=="unconfirmed")
    check(verdict(hash=null)=="unconfirmed")
    check(verdict(radio=false)=="unconfirmed")
    check(verdict(correlated=false)=="unconfirmed")
    check(verdict(control=false)=="unconfirmed")
    check(verdict(reached=true)=="unconfirmed")
    check(verdict(status=0x9000)=="unconfirmed")
    check(verdict(selected=true,reached=true,status=0x9000)=="not_observed")
    val key=ByteArray(32){it.toByte()}
    val reader=NfcTestProtocol(key,0)
    val card=NfcTestProtocol(key,0)
    check(card.respond(NfcTestProtocol.SELECT).contentEquals(byteArrayOf(0x90.toByte(),0)))
    val control=reader.command(0)
    check(control.size==35)
    check(reader.verify(card.respond(control),0))
    val select=reader.command(1)
    check(select.size==35)
    check(select.take(4)==listOf(12.toByte(),0xa4.toByte(),4.toByte(),12.toByte()))
    val damaged=select.clone().apply{this[10]=(this[10].toInt() xor 1).toByte()}
    check(card.respond(damaged).contentEquals(byteArrayOf(0x69.toByte(),0x82.toByte())))
    val reply=card.respond(select)
    check(reader.verify(reply,1))
    check(!reader.verify(reply.clone().apply{this[5]=(this[5].toInt() xor 1).toByte()},1))
    check(card.respond(select).contentEquals(byteArrayOf(0x69.toByte(),0x85.toByte())))
    check(!NfcTestProtocol(key,1).verify(reply,1))
    val malformed=NfcTestProtocol(key,0)
    check(malformed.respond(control).contentEquals(NfcTestProtocol.status(0x6985)))
    malformed.respond(NfcTestProtocol.SELECT)
    check(malformed.respond(select).contentEquals(NfcTestProtocol.status(0x6700)))
    for(index in listOf(0,1,2,3,4,34)){
        val altered=control.clone().apply{this[index]=(this[index].toInt() xor 1).toByte()}
        check(malformed.respond(altered).contentEquals(NfcTestProtocol.status(0x6700)))
    }
    check(malformed.respond(control.copyOf(34)).contentEquals(NfcTestProtocol.status(0x6700)))
    check(malformed.respond(control.copyOf(36)).contentEquals(NfcTestProtocol.status(0x6700)))
    check(reader.verify(malformed.respond(control),0))
    malformed.close()
    check(malformed.respond(select).contentEquals(NfcTestProtocol.status(0x6985)))
    check(!reader.verify(byteArrayOf(0x90.toByte(),0),1))
    check(runCatching{NfcTestProtocol(ByteArray(31),0)}.isFailure)
    check(runCatching{NfcTestProtocol(ByteArray(32),2)}.isFailure)
    key.fill(0)
    reader.close();card.close()
    val server=NfcTestTls.listen(InetAddress.getLoopbackAddress())
    val executor=Executors.newSingleThreadExecutor()
    try {
        val accepted=executor.submit<Boolean> {
            server.accept().use { channel ->
                check(channel.read().contentEquals("hello".toByteArray()))
                channel.write("world".toByteArray());true
            }
        }
        NfcTestTls.connect("127.0.0.1",server.port,server.pin,server.token).use { channel ->
            channel.write("hello".toByteArray())
            check(channel.read().contentEquals("world".toByteArray()))
            check(runCatching{channel.write(ByteArray(32769))}.isFailure)
        }
        check(accepted.get(5,TimeUnit.SECONDS))
    } finally {server.close();executor.shutdownNow()}
    rejectPairing(true);rejectPairing(false)
    checkCode("123456",true);checkCode("123457",false);checkCode("000001",true,"000001")
    checkBinding()
    rejectMalformedPake()
    for(code in listOf("12345","1234567","12345x","１２３４５６"))check(runCatching{NfcTestTls.connectCode("127.0.0.1",1,code)}.isFailure)
    check(runCatching { NfcTestTls.validateEndpoint("8.8.8.8",443) }.isFailure)
    check(runCatching { NfcTestTls.validateEndpoint("192.168.0.1",0) }.isFailure)
    for(host in listOf("localhost","192.168.0.256","192.168.0.1.attacker.test","::1"))check(runCatching{NfcTestTls.validateEndpoint(host,1234)}.isFailure)
}

private fun rejectPairing(wrongPin:Boolean) {
    val server=NfcTestTls.listen(InetAddress.getLoopbackAddress())
    val executor=Executors.newSingleThreadExecutor()
    try {
        val accepted=executor.submit<Boolean>{runCatching{server.accept().close()}.isSuccess}
        val pin=server.pin.clone();val token=server.token.clone()
        if(wrongPin)pin[0]=(pin[0].toInt() xor 1).toByte() else token[0]=(token[0].toInt() xor 1).toByte()
        check(runCatching{NfcTestTls.connect("127.0.0.1",server.port,pin,token).close()}.isFailure)
        check(!accepted.get(10,TimeUnit.SECONDS))
    }finally{server.close();executor.shutdownNow()}
}

private fun checkCode(code:String,expected:Boolean,serverCode:String="123456"){
    val server=NfcTestTls.listen(InetAddress.getLoopbackAddress())
    val executor=Executors.newSingleThreadExecutor()
    try{
        val accepted=executor.submit<Boolean>{runCatching{server.accept(serverCode).use{channel->channel.write("authenticated".toByteArray())}}.isSuccess}
        val joined=runCatching{NfcTestTls.connectCode("127.0.0.1",server.port,code).use{channel->check(channel.read().contentEquals("authenticated".toByteArray()))}}.isSuccess
        check(joined==expected);check(accepted.get(20,TimeUnit.SECONDS)==expected)
    }finally{server.close();executor.shutdownNow()}
}
private fun rejectMalformedPake(){
    val server=NfcTestTls.listen(InetAddress.getLoopbackAddress())
    val executor=Executors.newSingleThreadExecutor()
    try{
        val accepted=executor.submit<Boolean>{server.accept().use{channel->channel.read();channel.write(byteArrayOf(0,1,0));true}}
        check(runCatching{NfcTestTls.connect("127.0.0.1",server.port,server.pin,server.token).use{channel->NfcCodePairing.authenticate(channel,"123456",false,server.pin)}}.isFailure)
        check(accepted.get(10,TimeUnit.SECONDS))
    }finally{server.close();executor.shutdownNow()}
}
private fun checkBinding(){
    val server=NfcTestTls.listen(InetAddress.getLoopbackAddress())
    val executor=Executors.newSingleThreadExecutor()
    try{
        val accepted=executor.submit<Boolean>{runCatching{server.accept().use{channel->NfcCodePairing.authenticate(channel,"123456",true,server.pin)}}.isSuccess}
        val fakePin=server.pin.clone().apply{this[0]=(this[0].toInt() xor 1).toByte()}
        val joined=runCatching{NfcTestTls.connect("127.0.0.1",server.port,server.pin,server.token).use{channel->NfcCodePairing.authenticate(channel,"123456",false,fakePin)}}.isSuccess
        check(!joined);check(!accepted.get(20,TimeUnit.SECONDS))
    }finally{server.close();executor.shutdownNow()}
}
