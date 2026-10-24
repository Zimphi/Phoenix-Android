// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import org.bouncycastle.crypto.agreement.jpake.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger

/** J-PAKE prevents offline guessing; participant identities bind it to this TLS certificate. */
internal object NfcCodePairing {
    fun authenticate(channel:NfcTestTls.Channel,code:String,server:Boolean,pin:ByteArray) {
        require(code.matches(Regex("[0-9]{6}"))&&pin.size==32)
        val binding=pin.joinToString(""){"%02x".format(it)}
        val own=(if(server)"master-" else "peer-")+binding
        val other=(if(server)"peer-" else "master-")+binding
        val password=code.toCharArray()
        val participant=try{JPAKEParticipant(own,password)}finally{password.fill('\u0000')}
        val r1=participant.createRound1PayloadToSend()
        channel.write(pack(r1.participantId,listOf(r1.gx1,r1.gx2,*r1.knowledgeProofForX1,*r1.knowledgeProofForX2)))
        val (id1,n1)=unpack(channel.read(),6);require(id1==other)
        participant.validateRound1PayloadReceived(JPAKERound1Payload(id1,n1[0],n1[1],arrayOf(n1[2],n1[3]),arrayOf(n1[4],n1[5])))
        val r2=participant.createRound2PayloadToSend()
        channel.write(pack(r2.participantId,listOf(r2.a,*r2.knowledgeProofForX2s)))
        val (id2,n2)=unpack(channel.read(),3);require(id2==other)
        participant.validateRound2PayloadReceived(JPAKERound2Payload(id2,n2[0],arrayOf(n2[1],n2[2])))
        val key=participant.calculateKeyingMaterial()
        val r3=participant.createRound3PayloadToSend(key)
        channel.write(pack(r3.participantId,listOf(r3.macTag)))
        val (id3,n3)=unpack(channel.read(),1);require(id3==other)
        participant.validateRound3PayloadReceived(JPAKERound3Payload(id3,n3[0]),key)
    }
    private fun pack(id:String,numbers:List<BigInteger>):ByteArray=ByteArrayOutputStream().run {
        DataOutputStream(this).use{out->out.writeUTF(id);numbers.forEach{val bytes=it.toByteArray();require(bytes.size in 1..512);out.writeInt(bytes.size);out.write(bytes)}}
        toByteArray()
    }
    private fun unpack(bytes:ByteArray,count:Int):Pair<String,List<BigInteger>> {
        require(bytes.size<=8192)
        val input=DataInputStream(ByteArrayInputStream(bytes));val id=input.readUTF();require(id.matches(Regex("(master|peer)-[0-9a-f]{64}")))
        val numbers=(0 until count).map{val size=input.readInt();require(size in 1..512);BigInteger(ByteArray(size).also(input::readFully))}
        require(input.available()==0);return id to numbers
    }
}
