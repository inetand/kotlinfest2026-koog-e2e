package harness.pool

import harness.graph.Reported
import harness.run.RunOutcome
import harness.run.reason
import kotlinx.serialization.Serializable

const val EXIT_OK = 0
const val EXIT_FAIL = 1
const val EXIT_INCONCLUSIVE = 2
const val EXIT_REVIEW_REQUIRED = 3
const val EXIT_NOT_RUN = 4
const val EXIT_USAGE = 64

/** What stdout carries for one test: exactly one JSON line, for whatever consumes the run. */
@Serializable
data class ResultLine(
    val testId: String,
    val outcome: String,
    val exitCode: Int,
    val device: String? = null,
    val attempts: Int = 0,
    val reason: String? = null,
    val record: String? = null,
    val report: String? = null,
) {
    constructor(reported: Reported) : this(
        testId = reported.record.specId,
        outcome = reported.record.outcome.status,
        exitCode = exitCodeOf(reported.record.outcome),
        device = reported.record.device,
        attempts = reported.record.attempts.size,
        reason = reported.record.outcome.reason(),
        record = reported.recordPath,
        report = reported.reportPath,
    )
}

fun exitCodeOf(outcome: RunOutcome): Int =
    when (outcome) {
        RunOutcome.Pass, is RunOutcome.PassWithWarnings -> EXIT_OK
        is RunOutcome.Fail -> EXIT_FAIL
        is RunOutcome.Inconclusive -> EXIT_INCONCLUSIVE
        is RunOutcome.ReviewRequired -> EXIT_REVIEW_REQUIRED
        is RunOutcome.NotRun -> EXIT_NOT_RUN
    }

/** The exit code of a run is its worst test: a usage error outranks everything, then a failure, and so on. */
fun worstExitCode(codes: List<Int>): Int =
    listOf(EXIT_USAGE, EXIT_FAIL, EXIT_INCONCLUSIVE, EXIT_REVIEW_REQUIRED, EXIT_NOT_RUN).firstOrNull { it in codes } ?: EXIT_OK
