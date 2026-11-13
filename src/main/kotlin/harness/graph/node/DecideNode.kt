package harness.graph.node

import harness.graph.Decision
import harness.graph.GraphNode
import harness.graph.Outcome
import harness.graph.TestResult
import harness.graph.Verdict
import harness.graph.Verified
import org.springframework.stereotype.Component

/**
 * Decides what follows a verified attempt: finish on a pass, retry while the budget lasts, finish as exhausted
 * otherwise. A pure function of its input, which is what makes each branch testable in isolation.
 */
@Component
class DecideNode : GraphNode<Verified, Decision> {

    override val name = "decide"

    override suspend fun run(input: Verified): Decision {
        val state = input.state
        val request = state.request
        return when (val verdict = input.verdict) {
            Verdict.Pass -> Decision.Finish(TestResult(request.scenario.id, request.device, Outcome.Passed, state.attempt))
            is Verdict.Fail ->
                if (state.remaining > 0) {
                    Decision.Retry(state.next())
                } else {
                    Decision.Finish(TestResult(request.scenario.id, request.device, Outcome.Exhausted(verdict.reason), state.attempt))
                }
        }
    }
}
