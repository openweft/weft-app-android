package io.openweft.weftapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.openweft.weftapp.failover.DirectBackend
import io.openweft.weftapp.failover.Endpoint
import io.openweft.weftapp.failover.Supervisor
import io.openweft.weftapp.failover.WebInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole app: a full-screen WebView hosting weft-webui, plus a native
 * [Supervisor] that mirrors weft-app-core's failover logic.
 *
 * Mobile uses the *multi-origin* failover model (the WebView can't host a
 * loopback TCP gateway): every DC's origin is injected as
 * `window.__WEFT_ENDPOINTS__`, so the SPA's own API client rotates across
 * them. The native supervisor:
 *   - picks which DC to load first (the preferred healthy one), and
 *   - raises the SPA's "connection switched" banner on every change via
 *     `__weftFailoverNotice`, and reloads onto the active DC if the
 *     current page itself became unreachable.
 *
 * TODO: load the endpoint list from config / DNS SRV instead of the
 * hard-coded sample below; add the SSH / per-app-VPN WireGuard transports
 * (see Backend.kt).
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var supervisor: Supervisor
    private lateinit var endpoints: List<Endpoint>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        endpoints = loadEndpoints()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        setContentView(web)

        // Inject the endpoint list before any page script runs.
        web.evaluateJavascript(WebInject.initScript(endpoints), null)

        supervisor = Supervisor(
            endpoints,
            onSwitch = { sw ->
                lifecycleScope.launch(Dispatchers.Main) {
                    web.evaluateJavascript(WebInject.failoverNotice(sw.fromName, sw.toName), null)
                    // If we have no page loaded yet, or the active DC moved,
                    // (re)point the WebView at the active origin.
                    sw.toName?.let { name ->
                        endpoints.firstOrNull { it.name == name }?.let { web.loadUrl(it.backend.url()) }
                    }
                }
            },
        )
        supervisor.run(lifecycleScope)

        // Load the first preferred healthy DC once the first probe round
        // completes; until then show the preferred one optimistically.
        lifecycleScope.launch {
            val ep = withContext(Dispatchers.Default) { supervisor.activeEndpoint() } ?: endpoints.first()
            web.loadUrl(ep.backend.url())
        }
    }

    /** Loads endpoints from assets/app.json (same schema as the desktop /
     *  iOS apps), falling back to a sample three-DC mesh setup.
     *  TODO: DNS SRV discovery and a user-editable config. */
    private fun loadEndpoints(): List<Endpoint> {
        runCatching {
            val json = assets.open("app.json").bufferedReader().use { it.readText() }
            val eps = io.openweft.weftapp.failover.Config.parse(json)
            if (eps.isNotEmpty()) return eps
        }
        return listOf(
            Endpoint("DC-A", DirectBackend("10.80.0.11", 8080)),
            Endpoint("DC-B", DirectBackend("10.80.1.11", 8080)),
            Endpoint("DC-C", DirectBackend("10.80.2.11", 8080)),
        )
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
