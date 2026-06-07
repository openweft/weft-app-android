package io.openweft.weftapp.failover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigTest {

    @Test fun parsesDirectEndpoints() {
        val eps = Config.parse(
            """{ "endpoints": [
                { "name": "DC-A", "kind": "direct", "addr": "10.80.0.11:8080" },
                { "name": "DC-B", "kind": "direct", "addr": "10.80.1.11:8080", "tls": true }
            ] }"""
        )
        assertEquals(2, eps.size)
        assertEquals("DC-A", eps[0].name)
        assertEquals("http://10.80.0.11:8080", eps[0].backend.url())
        assertEquals("https://10.80.1.11:8080", eps[1].backend.url())
    }

    @Test fun parsesSshEndpoint() {
        val eps = Config.parse(
            """{ "endpoints": [ {
                "name": "DC-A", "kind": "ssh",
                "ssh_addr": "bastion-a:22", "user": "weft",
                "key_path": "/data/key", "known_hosts_path": "/data/known_hosts",
                "webui_addr": "127.0.0.1:8443"
            } ] }"""
        )
        assertEquals(1, eps.size)
        assertEquals("DC-A", eps[0].name)
        assertEquals("ssh://bastion-a:22/127.0.0.1:8443", eps[0].backend.target())
    }

    @Test fun wireguardIsUnsupportedForNow() {
        assertThrows(IllegalArgumentException::class.java) {
            Config.parse("""{ "endpoints": [ { "name": "DC-A", "kind": "wireguard", "addr": "10.0.0.1:8080" } ] }""")
        }
    }

    @Test fun badAddrThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Config.parse("""{ "endpoints": [ { "name": "DC-A", "kind": "direct", "addr": "no-port" } ] }""")
        }
    }
}
