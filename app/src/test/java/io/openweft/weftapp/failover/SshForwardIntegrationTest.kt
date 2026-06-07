package io.openweft.weftapp.failover

import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Base64
import kotlin.concurrent.thread

/**
 * End-to-end proof that SshForwardBackend forwards bytes through a real
 * SSH server. An in-process Apache MINA sshd (direct-tcpip forwarding
 * enabled) sits in front of a tiny HTTP echo; the backend opens a loopback
 * forward and a real GET is driven through it. Verifies host-key
 * verification too (the known_hosts file pins the server's host key).
 *
 * Runs in the JVM unit-test suite: ./gradlew :app:testDebugUnitTest
 */
class SshForwardIntegrationTest {

    @Test fun forwardsHttpThroughSsh() {
        // 1. HTTP echo "webui".
        val echo = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val echoPort = echo.localPort
        val body = "served-by-ssh"
        thread(isDaemon = true) {
            echo.use {
                val s = it.accept()
                s.getInputStream().read(ByteArray(8192)) // consume request line(s)
                s.getOutputStream().write(
                    ("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray()
                )
                s.getOutputStream().flush()
                s.close()
            }
        }

        // 2. Host + client RSA keypairs.
        val hostKp = rsa()
        val clientKp = rsa()

        // 3. MINA SSH server: our host key, accept-all pubkey auth, allow
        //    direct-tcpip forwarding.
        val sshd = SshServer.setUpDefaultServer()
        sshd.host = "127.0.0.1"
        sshd.port = 0
        sshd.keyPairProvider = KeyPairProvider.wrap(hostKp)
        sshd.publickeyAuthenticator = PublickeyAuthenticator { _, _, _ -> true }
        sshd.forwardingFilter = AcceptAllForwardingFilter.INSTANCE
        sshd.start()
        val sshPort = sshd.port

        try {
            // 4. Client key file (PKCS#8 PEM) + known_hosts pinning the host key.
            val keyFile = File.createTempFile("weft_client", ".pem").apply { deleteOnExit() }
            keyFile.writeText(pkcs8Pem(clientKp))
            val knownHosts = File.createTempFile("weft_known", "_hosts").apply { deleteOnExit() }
            knownHosts.writeText(knownHostsLine("127.0.0.1", sshPort, hostKp.public))

            // 5. Backend through the SSH forward.
            val backend = SshForwardBackend(
                sshHost = "127.0.0.1", sshPort = sshPort, user = "weft",
                keyPath = keyFile.absolutePath, knownHostsPath = knownHosts.absolutePath,
                webuiHost = "127.0.0.1", webuiPort = echoPort,
            )

            assertTrue("probe should connect + start forward", backend.probe())

            val url = backend.url()
            assertTrue("url should be a real loopback port, got $url", !url.endsWith(":0"))

            // 6. GET through loopback -> SSH -> echo.
            val conn = (URL("$url/").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            val got = conn.inputStream.bufferedReader().use { it.readText() }
            assertEquals(body, got)
        } finally {
            sshd.stop(true)
        }
    }

    private fun rsa(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** PKCS#8 PEM ("BEGIN PRIVATE KEY") — sshj loads this for client auth. */
    private fun pkcs8Pem(kp: KeyPair): String {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(kp.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----\n"
    }

    /** A known_hosts line: "[host]:port ssh-rsa <base64-wire-key>". */
    private fun knownHostsLine(host: String, port: Int, pub: PublicKey): String {
        val buf = Buffer.PlainBuffer().putPublicKey(pub)
        val b64 = Base64.getEncoder().encodeToString(buf.compactData)
        val type = KeyType.fromKey(pub).toString()
        return "[$host]:$port $type $b64\n"
    }
}
