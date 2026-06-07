package io.openweft.weftapp.failover

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Kotlin side of the JS contract in weft-webui's
 * `src/lib/endpoints.ts` — identical to weft-app-core's `webinject`
 * package, so the SPA behaves the same regardless of which client shell
 * hosts it.
 */
object WebInject {

    /** `window.__WEFT_ENDPOINTS__ = {...}` — evaluate at document start. */
    fun initScript(endpoints: List<Endpoint>): String {
        val arr = JSONArray()
        for (e in endpoints) {
            arr.put(JSONObject().put("name", e.name).put("url", e.backend.url()))
        }
        val cfg = JSONObject().put("endpoints", arr)
        return "window.__WEFT_ENDPOINTS__ = $cfg;"
    }

    /** `window.__weftFailoverNotice(from,to)` — raises the SPA banner. */
    fun failoverNotice(from: String?, to: String?): String {
        val f = JSONObject.quote(from ?: "")
        val t = JSONObject.quote(to ?: "")
        return "window.__weftFailoverNotice && window.__weftFailoverNotice($f,$t);"
    }
}
