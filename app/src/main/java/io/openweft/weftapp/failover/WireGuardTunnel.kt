package io.openweft.weftapp.failover

import android.content.Context

/**
 * WireGuard transport scaffolding for Android.
 *
 * Unlike the desktop apps (which run a userspace wireguard-go netstack
 * in-process), Android routes WireGuard through the system VPN: the app
 * must hold BIND_VPN_SERVICE and bring up a tunnel via the official
 * com.wireguard.android:tunnel GoBackend. Once the tunnel is up the mesh
 * addresses are directly reachable, so each DC endpoint is then a plain
 * [DirectBackend] over the mesh.
 *
 * Flow:
 *   1. VpnService.prepare(context) → if it returns an Intent, start it for
 *      result to get the user's VPN consent.
 *   2. Build a com.wireguard.config.Config from the WireGuard settings.
 *   3. backend.setState(tunnel, UP, config).
 *   4. Build the Supervisor with DirectBackend endpoints on the mesh IPs.
 *
 * NOTE: skeleton — not compiled in this scaffold's CI. Add the dependency
 *   implementation("com.wireguard.android:tunnel:1.0.20230706")
 * and declare the VpnService in AndroidManifest.xml (see manifest).
 */
object WireGuardTunnel {

    /** Returns true if the tunnel is up (or was already up). Implement with
     *  GoBackend.setState once the dependency + VPN consent are wired. */
    fun bringUp(context: Context, wgConfigJson: String): Boolean {
        // TODO:
        //   val backend = GoBackend(context)
        //   val cfg = Config.parse(BufferedReader(StringReader(toWgQuick(wgConfigJson))))
        //   backend.setState(WeftTunnel(), Tunnel.State.UP, cfg)
        return false
    }
}
