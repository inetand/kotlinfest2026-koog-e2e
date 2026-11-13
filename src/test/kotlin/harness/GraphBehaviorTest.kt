package harness

import harness.device.FakeBehavior
import harness.device.FakeScript
import harness.graph.Outcome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Behavior: the real graph is executed against a fake device, and the test looks only at what comes out and at
 * what the device was asked to do.
 */
class GraphBehaviorTest {

    private val tap = scenario("tap button")

    @Test
    fun `a test that succeeds finishes after one attempt`() {
        val wiring = Wiring(FakeScript(mapOf(tap.action to FakeBehavior.Succeeds)))

        val result = runBlocking { wiring.runner.run(tap, "kiosk-1", maxAttempts = 3) }

        assertEquals(Outcome.Passed, result.outcome)
        assertEquals(1, result.attempts)
        assertEquals(1, wiring.drivers.single().callsOf(tap.action))
    }

    @Test
    fun `a test that fails once passes on the retry`() {
        val wiring = Wiring(FakeScript(mapOf(tap.action to FakeBehavior.FailsOnce)))

        val result = runBlocking { wiring.runner.run(tap, "kiosk-1", maxAttempts = 3) }

        assertEquals(Outcome.Passed, result.outcome)
        assertEquals(2, result.attempts)
        assertEquals(2, wiring.drivers.single().callsOf(tap.action))
    }

    @Test
    fun `a test that keeps failing stops when the attempt budget is spent`() {
        val wiring = Wiring(FakeScript(mapOf(tap.action to FakeBehavior.AlwaysFails)))

        val result = runBlocking { wiring.runner.run(tap, "kiosk-1", maxAttempts = 3) }

        assertIs<Outcome.Exhausted>(result.outcome)
        assertEquals(3, result.attempts)
        assertEquals(3, wiring.drivers.single().callsOf(tap.action), "the device is never asked beyond the budget")
    }

    @Test
    fun `a budget of one attempt means no retry`() {
        val wiring = Wiring(FakeScript(mapOf(tap.action to FakeBehavior.FailsOnce)))

        val result = runBlocking { wiring.runner.run(tap, "kiosk-1", maxAttempts = 1) }

        assertIs<Outcome.Exhausted>(result.outcome)
        assertEquals(1, result.attempts)
    }
}
