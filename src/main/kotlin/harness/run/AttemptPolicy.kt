package harness.run

import org.springframework.stereotype.Component

/**
 * Decides what follows a validated attempt. A pass or a fail is final: evidence decided it, and replaying would
 * only hide a flaky app. A harness fault is final too. Everything else is a transient failure, retried unchanged
 * while retries and time remain, then inconclusive.
 */
@Component
class AttemptPolicy(private val rules: OutcomeRules) {

    fun decide(record: AttemptRecord, warnings: List<String>, retriesRemaining: Int, timeLeft: Boolean): NextAttempt {
        val verdict = record.verdict
        if (verdict.overall == Ruling.PASS || verdict.overall == Ruling.FAIL) {
            return NextAttempt.Finish(rules.outcomeOf(verdict, warnings))
        }
        if (verdict.harnessFault != null) {
            return NextAttempt.Finish(RunOutcome.Inconclusive("harness: ${verdict.note}"))
        }
        val reason = record.execution.failure?.summary ?: verdict.note ?: "the evidence did not decide every criterion"
        if (retriesRemaining > 0 && timeLeft) {
            return NextAttempt.Retry(reason)
        }
        val limit = if (!timeLeft) "the attempt time budget is used up" else "no retries remain"
        return NextAttempt.Finish(RunOutcome.Inconclusive("$reason; $limit after ${record.number} attempt(s)"))
    }
}
