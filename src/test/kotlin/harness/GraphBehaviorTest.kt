package harness

import harness.device.fake.FakeBehavior
import harness.run.RunOutcome
import harness.run.RunRecord
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Behavior: the real graph is executed against a fake device and a rule judge, and the test looks only at what
 * comes out (the outcome, the record on disk) and at what the device was asked to do.
 */
class GraphBehaviorTest {

    @Test
    fun `a test whose evidence proves every criterion passes after one replay`() {
        val wiring = Wiring(scripts = mapOf("save" to script()))
        wiring.workspace.test("save")

        val reported = wiring.run("save")

        assertEquals(RunOutcome.Pass, reported.record.outcome)
        assertEquals(1, reported.record.attempts.size)
        assertEquals(1, wiring.device.replaysOf("save"))
    }

    @Test
    fun `a replay that fails once is replayed and the pass carries a warning`() {
        val wiring = Wiring(scripts = mapOf("save" to script(FakeBehavior.FAILS_ONCE)))
        wiring.workspace.test("save")

        val reported = wiring.run("save")

        val outcome = assertIs<RunOutcome.PassWithWarnings>(reported.record.outcome)
        assertContains(outcome.warnings.single(), "attempt 1 did not pass")
        assertEquals(2, reported.record.attempts.size)
        assertEquals(2, wiring.device.replaysOf("save"))
    }

    @Test
    fun `a replay that keeps failing ends inconclusive when the attempt budget is spent`() {
        val wiring = Wiring(scripts = mapOf("save" to script(FakeBehavior.ALWAYS_FAILS)), maxAttempts = 3)
        wiring.workspace.test("save")

        val reported = wiring.run("save")

        val outcome = assertIs<RunOutcome.Inconclusive>(reported.record.outcome)
        assertContains(outcome.why, "no retries remain after 3 attempt(s)")
        assertEquals(3, wiring.device.replaysOf("save"), "the device is never asked beyond the budget")
    }

    @Test
    fun `evidence that contradicts a criterion fails the test without a retry`() {
        val wiring = Wiring(scripts = mapOf("save" to script(screen = "Something went wrong")))
        wiring.workspace.test("save")

        val reported = wiring.run("save")

        assertIs<RunOutcome.Fail>(reported.record.outcome)
        assertEquals(1, wiring.device.replaysOf("save"), "a failure shown by evidence is final")
    }

    @Test
    fun `a stale scenario needs review and never reaches the device`() {
        val wiring = Wiring()
        wiring.workspace.test("save", stale = true)

        val reported = wiring.run("save")

        assertEquals("stale_scenario", assertIs<RunOutcome.ReviewRequired>(reported.record.outcome).reason)
        assertEquals(0, wiring.device.replaysOf("save"))
    }

    @Test
    fun `a specification without a scenario is not run`() {
        val wiring = Wiring()
        wiring.workspace.test("save", withScenario = false)

        assertIs<RunOutcome.NotRun>(wiring.run("save").record.outcome)
    }

    @Test
    fun `a replay still running at the deadline is cut off and the test ends inconclusive`() {
        val wiring = Wiring(scripts = mapOf("save" to script()), attemptTimeout = Duration.ofMillis(100), stepLatency = Duration.ofMillis(300))
        wiring.workspace.test("save")

        val reported = wiring.run("save")

        val outcome = assertIs<RunOutcome.Inconclusive>(reported.record.outcome)
        assertContains(outcome.why, "the attempt time budget is used up")
        assertEquals(1, reported.record.attempts.size, "no retry starts after the deadline")
    }

    @Test
    fun `every test leaves a record and a report on disk`() {
        val wiring = Wiring(scripts = mapOf("save" to script()))
        wiring.workspace.test("save")

        val reported = wiring.run("save")

        val stored = testJson.decodeFromString<RunRecord>(Path.of(reported.recordPath).readText())
        assertEquals(reported.record, stored)
        assertTrue(Path.of(reported.reportPath).readText().startsWith("# save: PASS"))
        assertTrue(Path.of(stored.attempts.single().execution.checkpoints.single().hierarchy!!.path).exists(), "the evidence the verdict cites is kept")
    }
}
