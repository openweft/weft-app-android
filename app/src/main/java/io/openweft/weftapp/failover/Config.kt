package io.openweft.weftapp.failover

import org.json.JSONObject

/**
 * Loads [Endpoint]s from JSON in the same schema as weft-app-core's
 * shell.Config (and the desktop / iOS apps), so a cluster ships one
 * config for every client. Only the Direct transport is wired on Android
 * today; SSH / WireGuard entries throw (see [Backend]).
 */
object Config {

    fun parse(json: String): List<Endpoint> {
        val arr = JSONObject(json).getJSONArray("endpoints")
        val out = ArrayList<Endpoint>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("name")
            when (o.getString("kind")) {
                "direct" -> {
                    val (host, port) = splitHostPort(o.getString("addr"))
                    out.add(Endpoint(name, DirectBackend(host, port, o.optBoolean("tls", false))))
                }
                "ssh" -> {
                    val (sshHost, sshPort) = splitHostPort(o.getString("ssh_addr"))
                    val (webHost, webPort) = splitHostPort(o.getString("webui_addr"))
                    out.add(
                        Endpoint(
                            name,
                            SshForwardBackend(
                                sshHost = sshHost,
                                sshPort = sshPort,
                                user = o.optString("user", System.getProperty("user.name") ?: "weft"),
                                keyPath = o.getString("key_path"),
                                knownHostsPath = o.getString("known_hosts_path"),
                                webuiHost = webHost,
                                webuiPort = webPort,
                            ),
                        ),
                    )
                }
                "wireguard" ->
                    throw IllegalArgumentException("wireguard transport needs the VpnService tunnel (see WireGuardTunnel.kt) — wire DirectBackend over the mesh once the VPN is up ($name)")
                else -> throw IllegalArgumentException("unknown transport kind for $name")
            }
        }
        return out
    }

    private fun splitHostPort(s: String): Pair<String, Int> {
        val i = s.lastIndexOf(':')
        require(i > 0) { "addr must be host:port, got $s" }
        val port = s.substring(i + 1).toIntOrNull() ?: throw IllegalArgumentException("bad port in $s")
        return s.substring(0, i) to port
    }
}
