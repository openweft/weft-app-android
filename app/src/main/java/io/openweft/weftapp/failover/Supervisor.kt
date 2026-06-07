package io.openweft.weftapp.failover

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Health standing of one datacenter.
 */
enum class Health { UNKNOWN, UP, DOWN }

/**
 * An active-DC change, delivered to [Supervisor.onSwitch].
 *
 * [fromName] is null on the first selection; when [allDown] is true no DC
 * is healthy and [toName] is null.
 */
data class Switch(val fromName: String?, val toName: String?, val allDown: Boolean)

/**
 * One datacenter: a display name plus the [Backend] used to reach its
 * weft-webui.
 */
data class Endpoint(val name: String, val backend: Backend)

/**
 * Tuning. Matches weft-app-core's failover.Options.
 */
data class Options(
    val intervalMs: Long = 3_000,
    val holdDownMs: Long = 15_000,
    val now: () -> Long = { System.currentTimeMillis() },
)

/**
 * Read-only per-endpoint status for a status sheet.
 */
data class EndpointStatus(val name: String, val target: String, val health: Health, val active: Boolean)

/**
 * Selects a healthy DC from an ordered endpoint list with hysteresis —
 * fail over fast, fail back slow — a line-for-line port of
 * weft-app-core's `failover.Supervisor`, so the desktop and mobile apps
 * behave identically.
 *
 *  - A DC that fails a probe is dropped immediately.
 *  - A recovered, more-preferred DC must stay healthy for [Options.holdDownMs]
 *    before it is re-selected, so a flapping DC never whipsaws the active
 *    connection.
 *  - When nothing is active (cold start / all-down recovery) any healthy
 *    DC is taken at once.
 */
class Supervisor(
    endpoints: List<Endpoint>,
    private val opts: Options = Options(),
    private val onSwitch: ((Switch) -> Unit)? = null,
) {
    private class State(val ep: Endpoint) {
        var health: Health = Health.UNKNOWN
        var healthy: Boolean = false
        var upSince: Long = 0
    }

    private val mutex = Mutex()
    private val eps = endpoints.map { State(it) }
    @Volatile private var active: Int = -1

    /** Currently selected endpoint, or null when every DC is down. */
    suspend fun activeEndpoint(): Endpoint? = mutex.withLock {
        if (active < 0) null else eps[active].ep
    }

    suspend fun status(): List<EndpointStatus> = mutex.withLock {
        eps.mapIndexed { i, s ->
            EndpointStatus(s.ep.name, s.ep.backend.target(), s.health, i == active)
        }
    }

    /** Probe loop; launch on [scope]. Runs one round immediately. */
    fun run(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                round()
                delay(opts.intervalMs)
            }
        }
    }

    /** One probe round: probe all DCs, update health, re-select. Internal —
     *  visible for tests. */
    internal suspend fun round() {
        val now = opts.now()
        val results = BooleanArray(eps.size)
        // Probe sequentially on the IO dispatcher; the list is small (one
        // per DC) and probes are cheap.
        for (i in eps.indices) {
            results[i] = runCatching { eps[i].ep.backend.probe() }.getOrDefault(false)
        }

        val sw = mutex.withLock {
            for (i in eps.indices) {
                val s = eps[i]
                if (results[i]) {
                    if (!s.healthy) s.upSince = now
                    s.healthy = true
                    s.health = Health.UP
                } else {
                    s.healthy = false
                    s.health = Health.DOWN
                    s.upSince = 0
                }
            }
            reselectLocked(now)
        }
        if (sw != null) onSwitch?.invoke(sw)
    }

    /** Returns the Switch to emit, or null if nothing changed. Caller holds
     *  the mutex. */
    private fun reselectLocked(now: Long): Switch? {
        val prev = active
        val activeHealthy = prev >= 0 && eps[prev].healthy

        var best = -1
        for (i in eps.indices) {
            val s = eps[i]
            if (!s.healthy) continue
            if (!activeHealthy) { best = i; break }      // nothing working: take top healthy now
            if (i == prev) { best = i; break }           // reached active before any better: keep it
            if (i < prev && now - s.upSince >= opts.holdDownMs) { best = i; break } // failed back
        }

        if (best == prev) return null
        val fromName = if (prev >= 0) eps[prev].ep.name else null
        active = best
        return if (best < 0) Switch(fromName, null, true)
        else Switch(fromName, eps[best].ep.name, false)
    }
}
