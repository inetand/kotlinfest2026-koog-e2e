package harness.graph.node

import harness.Event
import harness.EventSink
import harness.graph.AttemptDecision
import harness.graph.AttemptOutcome
import harness.graph.GraphNode
import harness.graph.Validated
import harness.run.AttemptPolicy
import harness.run.NextAttempt
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Coordinates the attempt loop after a validated attempt: finish, or replay the unchanged scenario once more. The
 * budget is read from the state and only ever decreases: a retry returns a copy with one more attempt and one less
 * retry, which is why the loop always ends.
 */
@Component
class RetryNode(
    private val policy: AttemptPolicy,
    private val events: EventSink,
    private val clock: Clock,
) : GraphNode<Validated, AttemptDecision> {

    override val name = "retry"

    override suspend fun run(input: Validated): AttemptDecision {
        val state = input.state
        val record = input.record
        return when (val next = policy.decide(record, state.warnings, state.retriesRemaining, clock.instant().isBefore(state.deadline))) {
            is NextAttempt.Retry -> {
                events.emit(Event.Retrying(state.context.device.name, state.context.testId, next.reason))
                AttemptDecision.Retry(
                    state.copy(
                        attemptNumber = state.attemptNumber + 1,
                        retriesRemaining = state.retriesRemaining - 1,
                        warnings = state.warnings + "attempt ${record.number} did not pass (${next.reason}); the unchanged scenario was replayed",
                    ),
                )
            }
            is NextAttempt.Finish -> AttemptDecision.Finish(AttemptOutcome(state.context, next.outcome, state.previousAttempts, state.warnings, state.scenario))
        }
    }
}
