package io.openweft.weftapp.failover

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * SSH local-forward transport for Android, via sshj. Mirrors
 * weft-app-core's transport.SSHForward: it opens an SSH connection to a
 * per-DC endpoint and forwards a loopback port to the webui's internal
 * address, so the WebView loads http://127.0.0.1:<port> and the platform
 * exposes no public web listener.
 *
 * The connection + forwarder are established lazily on first url()/probe()
 * and reused; if they drop, the next call re-establishes them.
 *
 * NOTE: not compiled in this scaffold's CI (needs the Android toolchain).
 * The sshj API used here is stable across 0.3x.
 */
class SshForwardBackend(
    private val sshHost: String,
    private val sshPort: Int,
    private val user: String,
    private val keyPath: String,
    private val knownHostsPath: String,
    private val webuiHost: String,
    private val webuiPort: Int,
) : Backend {

    @Volatile private var client: SSHClient? = null
    @Volatile private var localPort: Int = 0
    private val lock = Any()

    private fun ensureUp(): Boolean = synchronized(lock) {
        client?.let { if (it.isConnected) return true }
        return try {
            val c = SSHClient()
            c.addHostKeyVerifier(OpenSSHKnownHosts(File(knownHostsPath)))
            c.connect(sshHost, sshPort)
            c.authPublickey(user, keyPath)

            // Bind a loopback listener and forward it to the webui over SSH.
            val ss = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            localPort = ss.localPort
            val params = Parameters("127.0.0.1", localPort, webuiHost, webuiPort)
            val forwarder = c.newLocalPortForwarder(params, ss)
            Thread({ runCatching { forwarder.listen() } }, "ssh-forward-$sshHost").apply {
                isDaemon = true
                start()
            }
            client = c
            true
        } catch (_: Exception) {
            client?.runCatching { close() }
            client = null
            false
        }
    }

    override fun probe(): Boolean = ensureUp()

    override fun target(): String = "ssh://$sshHost:$sshPort/$webuiHost:$webuiPort"

    override fun url(): String {
        if (!ensureUp()) return "http://127.0.0.1:0" // unreachable until a probe succeeds
        return "http://127.0.0.1:$localPort"
    }
}
