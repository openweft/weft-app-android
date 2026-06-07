package io.openweft.weftapp.failover

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Reaches one datacenter's weft-webui. Mirrors weft-app-core's
 * `transport.Backend`. The [Supervisor] only ever asks "are you healthy?"
 * ([probe]) — the WebView connects to the chosen DC directly.
 */
interface Backend {
    /** True if the DC is reachable and serving. */
    fun probe(): Boolean
    /** Stable, log-friendly description of where this points. */
    fun target(): String
    /** Origin to hand the WebView, e.g. "http://10.80.0.11:8080". */
    fun url(): String
}

/**
 * Plain TCP probe — used when the device is already on the mesh (a
 * per-app WireGuard VPN), or against a local SSH forward. SSH and
 * WireGuard transports proper are TODO on mobile:
 *
 *  - SSH: a local forward via a Kotlin SSH client (e.g. sshj), exposing a
 *    loopback port that this backend then targets.
 *  - WireGuard: Android's VpnService / per-app VPN, after which the mesh
 *    address is directly reachable and this DirectBackend suffices.
 */
class DirectBackend(
    private val host: String,
    private val port: Int,
    private val tls: Boolean = false,
    private val timeoutMs: Int = 4_000,
) : Backend {
    override fun probe(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }

    override fun target(): String = "$host:$port"
    override fun url(): String = (if (tls) "https" else "http") + "://$host:$port"
}
