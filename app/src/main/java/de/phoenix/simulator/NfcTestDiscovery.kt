// SPDX-License-Identifier: Apache-2.0
package de.phoenix.simulator

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import kotlin.coroutines.resume

/** Local discovery advertises only a random service name and port, never the code. */
internal class NfcTestDiscovery(context:Context):AutoCloseable {
    private val manager=context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration:NsdManager.RegistrationListener?=null
    fun advertise(port:Int,onReady:(Boolean)->Unit){
        val listener=object:NsdManager.RegistrationListener {
            override fun onServiceRegistered(info:NsdServiceInfo){onReady(true)}
            override fun onRegistrationFailed(info:NsdServiceInfo,error:Int){onReady(false)}
            override fun onServiceUnregistered(info:NsdServiceInfo){}
            override fun onUnregistrationFailed(info:NsdServiceInfo,error:Int){}
        }
        registration=listener
        manager.registerService(NsdServiceInfo().apply{serviceName="Phoenix NFC "+java.util.UUID.randomUUID().toString().take(8);serviceType=TYPE;setPort(port)},NsdManager.PROTOCOL_DNS_SD,listener)
    }
    suspend fun find():List<InetSocketAddress> {
        val found=Channel<NsdServiceInfo>(8)
        val listener=object:NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type:String){}
            override fun onServiceFound(info:NsdServiceInfo){if(info.serviceType.contains("phoenixnfctest"))found.trySend(info)}
            override fun onServiceLost(info:NsdServiceInfo){}
            override fun onDiscoveryStopped(type:String){}
            override fun onStartDiscoveryFailed(type:String,error:Int){found.close()}
            override fun onStopDiscoveryFailed(type:String,error:Int){}
        }
        val endpoints=mutableListOf<InetSocketAddress>()
        try {
            manager.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,listener)
            for(index in 0 until 8){
                val service=withTimeoutOrNull(if(endpoints.isEmpty())6000L else 1000L){found.receiveCatching().getOrNull()}?:break
                val address=withTimeoutOrNull(2500){resolve(service)}
                if(address!=null&&address.address is java.net.Inet4Address&&address.address.isSiteLocalAddress)endpoints+=address
            }
        }finally{runCatching{manager.stopServiceDiscovery(listener)};found.close()}
        return endpoints.distinct()
    }
    @Suppress("DEPRECATION") private suspend fun resolve(info:NsdServiceInfo):InetSocketAddress?=suspendCancellableCoroutine{continuation->
        manager.resolveService(info,object:NsdManager.ResolveListener {
            override fun onResolveFailed(service:NsdServiceInfo,error:Int){if(continuation.isActive)continuation.resume(null)}
            override fun onServiceResolved(service:NsdServiceInfo){
                val host=if(android.os.Build.VERSION.SDK_INT>=34)service.hostAddresses.firstOrNull{it is java.net.Inet4Address&&it.isSiteLocalAddress}else service.host
                if(continuation.isActive)continuation.resume(host?.let{InetSocketAddress(it,service.port)})
            }
        })
    }
    override fun close(){registration?.let{runCatching{manager.unregisterService(it)}};registration=null}
    companion object {private const val TYPE="_phoenixnfctest._tcp."}
}
