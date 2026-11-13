package harness.graph

import harness.config.Backend
import harness.device.DeviceSlot
import harness.device.ExecutionResult
import harness.run.AttemptRecord
import harness.run.RunBudget
import harness.run.RunOutcome
import harness.run.RunRecord
import harness.run.TestPaths
import harness.scenario.Scenario
import harness.spec.Specification
import java.time.Instant

/** The input of one graph run: one test of one run, on the device its worker owns. */
data class RunRequest(val runId: String, val testId: String, val device: DeviceSlot)

/** The immutable identity of one test within one run, fixed by the resolve node. */
data class RunContext(
    val runId: String,
    val testId: String,
    val spec: Specification,
    val device: DeviceSlot,
    val backend: Backend,
    val appId: String,
    val paths: TestPaths,
    val startedAt: Instant,
    val budget: RunBudget,
    /** How the scenario was resolved: `fresh`, `stale (<why>)`, `missing`, `unreadable`. */
    val scenarioStatus: String,
)

/**
 * The explicit state of the attempt loop. It is a value: every node receives it in its input and returns a copy in
 * its output, so the attempt number, the remaining retries, the deadline and the evidence in flight travel on the
 * edges. Nothing outside this value remembers where the loop stands.
 */
data class AttemptState(
    val context: RunContext,
    val scenario: Scenario,
    val attemptNumber: Int,
    val retriesRemaining: Int,
    val previousAttempts: List<AttemptRecord> = emptyList(),
    /** The evidence of the attempt in flight, between execute and validate. */
    val currentEvidence: ExecutionResult? = null,
    val attemptStartedAt: Instant? = null,
    val warnings: List<String> = emptyList(),
    /** When every attempt of this test must be over. */
    val deadline: Instant,
)

/** The output of `validate`: the state with the judged attempt added, and that attempt. */
data class Validated(val state: AttemptState, val record: AttemptRecord)

/** The output of `retry`, and what the edges of the attempt subgraph branch on. */
sealed interface AttemptDecision {
    data class Retry(val next: AttemptState) : AttemptDecision
    data class Finish(val outcome: AttemptOutcome) : AttemptDecision
}

/** The final result of the attempt loop (or of a block before it): everything the report is written from. */
data class AttemptOutcome(
    val context: RunContext,
    val outcome: RunOutcome,
    val attempts: List<AttemptRecord> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** The scenario that was replayed; null when nothing was. */
    val scenario: Scenario? = null,
)

/** The output of `resolve`: whether the test may be replayed, or why it ends before touching the device. */
sealed interface Resolved {
    data class Ready(val attempt: AttemptState) : Resolved
    data class Blocked(val outcome: AttemptOutcome) : Resolved
}

/** The output of the whole graph: the written record and where it is. */
data class Reported(val record: RunRecord, val recordPath: String, val reportPath: String)
