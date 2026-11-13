package harness.graph.node

import harness.graph.AttemptState
import harness.graph.GraphNode
import harness.graph.Validated
import harness.run.AttemptRecord
import harness.run.AttemptValidator
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Judges the attempt in flight from its stored evidence and turns it into a record. This is where a run may wait
 * for a model permit. The state it returns has the record appended and the in-flight evidence cleared.
 */
@Component
class ValidateNode(
    private val validator: AttemptValidator,
    private val clock: Clock,
) : GraphNode<AttemptState, Validated> {

    override val name = "validate"

    override suspend fun run(input: AttemptState): Validated {
        val execution = checkNotNull(input.currentEvidence) { "validate runs after execute" }
        val context = input.context
        val verdict = validator.validate(context.spec, input.scenario, execution, context.device.name)
        val record = AttemptRecord(
            number = input.attemptNumber,
            directory = context.paths.attemptDirectory(input.attemptNumber).toString(),
            execution = execution,
            verdict = verdict,
            startedAt = (input.attemptStartedAt ?: clock.instant()).toString(),
            finishedAt = clock.instant().toString(),
        )
        return Validated(input.copy(previousAttempts = input.previousAttempts + record, currentEvidence = null), record)
    }
}
