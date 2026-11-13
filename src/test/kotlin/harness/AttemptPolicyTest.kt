package harness

import harness.device.ExecutionFailure
import harness.device.ExecutionResult
import harness.run.AttemptPolicy
import harness.run.AttemptRecord
import harness.run.AttemptVerdict
import harness.run.NextAttempt
import harness.run.OutcomeRules
import harness.run.Ruling
import harness.run.RunOutcome
import harness.scenario.FlowStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The retry rule is an ordinary class, so each of its branches is tested with no graph, no agent and no device. */
class AttemptPolicyTest {

    private val policy = AttemptPolicy(OutcomeRules())
    private val busy = ExecutionFailure.StepFailed(1, FlowStep.Back, "the device was busy")

    private fun record(overall: Ruling, failure: ExecutionFailure? = null, harnessFault: String? = null) =
        AttemptRecord(1, "attempt-01", ExecutionResult(failure, "device.log", emptyList(), 10), AttemptVerdict(overall, emptyList(), note = harnessFault, harnessFault = harnessFault), "start", "end")

    @Test
    fun `a pass is final`() {
        assertEquals(NextAttempt.Finish(RunOutcome.Pass), policy.decide(record(Ruling.PASS), emptyList(), retriesRemaining = 2, timeLeft = true))
    }

    @Test
    fun `a fail is final even when retries remain`() {
        val next = policy.decide(record(Ruling.FAIL), emptyList(), retriesRemaining = 2, timeLeft = true)

        assertIs<RunOutcome.Fail>(assertIs<NextAttempt.Finish>(next).outcome)
    }

    @Test
    fun `a transient failure is retried while retries and time remain`() {
        assertEquals(NextAttempt.Retry(busy.summary), policy.decide(record(Ruling.INCONCLUSIVE, busy), emptyList(), retriesRemaining = 1, timeLeft = true))
    }

    @Test
    fun `a transient failure with no retries left is inconclusive`() {
        val next = policy.decide(record(Ruling.INCONCLUSIVE, busy), emptyList(), retriesRemaining = 0, timeLeft = true)

        assertIs<RunOutcome.Inconclusive>(assertIs<NextAttempt.Finish>(next).outcome)
    }

    @Test
    fun `a transient failure after the deadline is inconclusive even when retries remain`() {
        val next = policy.decide(record(Ruling.INCONCLUSIVE, busy), emptyList(), retriesRemaining = 2, timeLeft = false)

        assertIs<RunOutcome.Inconclusive>(assertIs<NextAttempt.Finish>(next).outcome)
    }

    @Test
    fun `a harness fault is not retried`() {
        val next = policy.decide(record(Ruling.INCONCLUSIVE, harnessFault = "malformed_judgement"), emptyList(), retriesRemaining = 2, timeLeft = true)

        assertIs<RunOutcome.Inconclusive>(assertIs<NextAttempt.Finish>(next).outcome)
    }
}
