package io.openweft.weftapp.failover

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mirrors weft-app-core's failover supervisor tests, so the Kotlin port
 * is verified to behave identically: fail over fast, fail back slow.
 */
class SupervisorTest {

    private class FakeBackend(@Volatile var up: Boolean) : Backend {
        override fun probe() = up
        override fun target() = "fake"
        override fun url() = "http://fake"
    }

    private class Clock(var t: Long = 0)

    private fun sup(clock: Clock, vararg backends: FakeBackend): Pair<Supervisor, MutableList<Switch>> {
        val switches = mutableListOf<Switch>()
        val names = listOf("A", "B", "C")
        val eps = backends.mapIndexed { i, b -> Endpoint(names[i], b) }
        val s = Supervisor(eps, Options(holdDownMs = 15_000, now = { clock.t })) { switches.add(it) }
        return s to switches
    }

    @Test fun coldStartPicksTopHealthy() = runTest {
        val clock = Clock()
        val (s, sw) = sup(clock, FakeBackend(true), FakeBackend(true))
        s.round()
        assertEquals("A", s.activeEndpoint()?.name)
        assertEquals("A", sw.last().toName)
        assertNull(sw.last().fromName)
    }

    @Test fun failoverIsImmediate() = runTest {
        val clock = Clock()
        val a = FakeBackend(true); val b = FakeBackend(true)
        val (s, sw) = sup(clock, a, b)
        s.round() // -> A
        a.up = false
        clock.t += 1_000
        s.round()
        assertEquals("B", s.activeEndpoint()?.name)
        assertEquals("A", sw.last().fromName)
        assertEquals("B", sw.last().toName)
    }

    @Test fun failBackWaitsForHoldDown() = runTest {
        val clock = Clock()
        val a = FakeBackend(true); val b = FakeBackend(true)
        val (s, _) = sup(clock, a, b)
        s.round() // -> A
        a.up = false; clock.t += 1_000; s.round() // -> B
        a.up = true;  clock.t += 1_000; s.round() // within hold-down: stay B
        assertEquals("B", s.activeEndpoint()?.name)
        clock.t += 20_000; s.round()              // past hold-down: back to A
        assertEquals("A", s.activeEndpoint()?.name)
    }

    @Test fun allDownThenRecoverImmediately() = runTest {
        val clock = Clock()
        val a = FakeBackend(true); val b = FakeBackend(true)
        val (s, sw) = sup(clock, a, b)
        s.round() // -> A
        a.up = false; b.up = false; clock.t += 1_000; s.round()
        assertNull(s.activeEndpoint())
        assert(sw.last().allDown)
        b.up = true; clock.t += 1_000; s.round()  // freshly-up sole DC taken at once
        assertEquals("B", s.activeEndpoint()?.name)
    }
}
