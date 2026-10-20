package de.phoenix.simulator

import org.json.JSONObject
import java.util.concurrent.Executors

/** Closing an authenticated socket must not block the Android lifecycle thread. */
internal object RemoteReader {
    private val worker=Executors.newSingleThreadExecutor()
    fun stop() { worker.execute { runCatching { NativeCore.call(JSONObject().put("op","ifd_stop")) } } }
    fun addresses():String=runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter{it.isUp&&!it.isLoopback}.flatMap{it.inetAddresses.toList()}
            .filterIsInstance<java.net.Inet4Address>().filter{it.isSiteLocalAddress}.joinToString(" · "){it.hostAddress?:""}
    }.getOrDefault("")
}
