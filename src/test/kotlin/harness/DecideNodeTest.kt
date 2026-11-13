package harness

import harness.graph.AttemptState
import harness.graph.Decision
import harness.graph.Outcome
import harness.graph.RunRequest
import harness.graph.TestResult
import harness.graph.Verdict
import harness.graph.Verified
import harness.graph.node.DecideNode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** A node body is an ordinary class, so its rule is tested with no graph, no agent and no device. */
class DecideNodeTest {

    private val request = RunRequest(scenario("tap button"), "kiosk-1", maxAttempts = 3)
    private val first = AttemptState.first(request)
    private val last = AttemptState(request, attempt = 3, remaining = 0)

    private fun decide(input: Verified): Decision = runBlocking { DecideNode().run(input) }

    @Test
    fun `a pass finishes with the attempt number it took`() {
        assertEquals(
            Decision.Finish(TestResult("tap-button", "kiosk-1", Outcome.Passed, attempts = 1)),
            decide(Verified(first, Verdict.Pass)),
        )
    }

    @Test
    fun `a failure with budget left retries with the next attempt and one less remaining`() {
        assertEquals(
            Decision.Retry(AttemptState(request, attempt = 2, remaining = 1)),
            decide(Verified(first, Verdict.Fail("busy"))),
        )
    }

    @Test
    fun `a failure with no budget left finishes as exhausted`() {
        assertEquals(
            Decision.Finish(TestResult("tap-button", "kiosk-1", Outcome.Exhausted("busy"), attempts = 3)),
            decide(Verified(last, Verdict.Fail("busy"))),
        )
    }
}
