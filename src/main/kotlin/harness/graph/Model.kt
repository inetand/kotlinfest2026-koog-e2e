package harness.graph

import harness.Scenario
import harness.device.Screen

/** The input of one graph run: one scenario on one device, with its attempt budget. */
data class RunRequest(val scenario: Scenario, val device: String, val maxAttempts: Int)

/**
 * Where the attempt loop stands. This is the only carrier of the attempt number and the remaining budget: every node
 * receives it in its input and passes it on in its output, so the loop needs no state outside the data on its edges.
 */
data class AttemptState(val request: RunRequest, val attempt: Int, val remaining: Int) {

    /** The state of the following attempt. */
    fun next(): AttemptState = copy(attempt = attempt + 1, remaining = remaining - 1)

    companion object {
        fun first(request: RunRequest): AttemptState = AttemptState(request, attempt = 1, remaining = request.maxAttempts - 1)
    }
}

/** The output of `execute`: the state it was given and the screen the device showed. */
data class Executed(val state: AttemptState, val screen: Screen)

/** The output of `verify`. */
data class Verified(val state: AttemptState, val verdict: Verdict)

sealed interface Verdict {
    data object Pass : Verdict
    data class Fail(val reason: String) : Verdict
}

/** The output of `decide`, and what the edges of the attempt subgraph branch on. */
sealed interface Decision {
    data class Retry(val next: AttemptState) : Decision
    data class Finish(val result: TestResult) : Decision
}

/** The output of one graph run. */
data class TestResult(val scenarioId: String, val device: String, val outcome: Outcome, val attempts: Int)

sealed interface Outcome {
    val label: String

    data object Passed : Outcome {
        override val label = "PASSED"
    }

    /** Every attempt failed and the budget is spent. */
    data class Exhausted(val lastReason: String) : Outcome {
        override val label = "EXHAUSTED"
    }
}
