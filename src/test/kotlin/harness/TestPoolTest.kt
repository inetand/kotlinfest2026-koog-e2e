package harness

import harness.device.FakeBehavior
import harness.device.FakeScript
import harness.graph.Outcome
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Concurrency: what overlaps, what is limited, and what happens to the workers when the caller goes away. */
class TestPoolTest {

    private val scenarios = listOf("one", "two", "three", "four", "five", "six").map { scenario(it) }

    private fun wiring(devices: Int, permits: Int, script: FakeScript = FakeScript()) =
        Wiring(script, devices, permits, deviceLatency = 20.milliseconds, verifierLatency = 40.milliseconds)

    @Test
    fun `every test runs exactly once and the results are collected from all workers`() {
        val wiring = wiring(devices = 3, permits = 2, FakeScript(mapOf("two" to FakeBehavior.FailsOnce, "five" to FakeBehavior.AlwaysFails)))

        val results = runBlocking { wiring.pool.run(scenarios, maxAttempts = 2).toList() }

        assertEquals(scenarios.map { it.id }.sorted(), results.map { it.scenarioId }.sorted())
        assertEquals(setOf("five"), results.filter { it.outcome is Outcome.Exhausted }.map { it.scenarioId }.toSet())
        assertEquals(2, results.single { it.scenarioId == "two" }.attempts)
    }

    @Test
    fun `devices work at the same time while the shared verifier stays within its permits`() {
        val wiring = wiring(devices = 3, permits = 2)

        runBlocking { wiring.pool.run(scenarios, maxAttempts = 1).toList() }

        assertEquals(3, peak(wiring.events.events) { it is Event.Acting }, "all three devices act at once")
        assertEquals(2, peak(wiring.events.events, start = { it is Event.VerifyStarted }, end = { it is Event.VerifyFinished }), "never more than two verifications in flight")
    }

    @Test
    fun `one permit serializes the verifier but not the devices`() {
        val wiring = wiring(devices = 3, permits = 1)

        runBlocking { wiring.pool.run(scenarios, maxAttempts = 1).toList() }

        assertEquals(3, peak(wiring.events.events) { it is Event.Acting })
        assertEquals(1, peak(wiring.events.events, start = { it is Event.VerifyStarted }, end = { it is Event.VerifyFinished }))
    }

    @Test
    fun `cancelling the collector stops every worker`() {
        val wiring = wiring(devices = 3, permits = 2, FakeScript(scenarios.associate { it.action to FakeBehavior.AlwaysFails }))

        val (atCancel, later) = runBlocking {
            val job = launch { wiring.pool.run(scenarios, maxAttempts = 1_000).toList() }
            delay(150.milliseconds)
            job.cancelAndJoin()
            val atCancel = wiring.events.events.size
            delay(150.milliseconds)
            atCancel to wiring.events.events.size
        }

        assertTrue(atCancel > 0, "the workers were running")
        assertEquals(atCancel, later, "no worker outlives its collector")
    }

    /** The most intervals open at once, where [start] opens one and [end] closes one. */
    private fun peak(events: List<Event>, end: (Event) -> Boolean = { it is Event.Acted }, start: (Event) -> Boolean): Int {
        var open = 0
        var peak = 0
        for (event in events) {
            if (start(event)) open++
            if (end(event)) open--
            peak = maxOf(peak, open)
        }
        return peak
    }
}
