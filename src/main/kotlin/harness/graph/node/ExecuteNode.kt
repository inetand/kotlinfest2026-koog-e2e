package harness.graph.node

import harness.Event
import harness.EventSink
import harness.device.CheckpointEvidence
import harness.device.DeviceRegistry
import harness.device.ExecutionFailure
import harness.device.ExecutionListener
import harness.device.ExecutionResult
import harness.graph.AttemptState
import harness.graph.GraphNode
import harness.run.RunRecordRepository
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Replays the scenario from its first step into a fresh attempt directory, capturing evidence at every checkpoint.
 * It knows the device only as a `DeviceDriver`. A replay still running at the test's deadline is cut off and counts
 * as an environment failure: `withTimeoutOrNull` cancels the driver's coroutine, and a driver that waits on an
 * external process is interrupted through the same cancellation.
 */
@Component
class ExecuteNode(
    private val devices: DeviceRegistry,
    private val records: RunRecordRepository,
    private val events: EventSink,
    private val clock: Clock,
) : GraphNode<AttemptState, AttemptState> {

    override val name = "execute"

    override suspend fun run(input: AttemptState): AttemptState {
        val context = input.context
        val device = context.device.name
        val directory = records.createAttemptDirectory(context.paths, input.attemptNumber)
        val startedAt = clock.instant()
        events.emit(Event.AttemptStarted(device, context.testId, input.attemptNumber, context.budget.maxAttempts))

        val listener = object : ExecutionListener {
            override fun checkpointCaptured(evidence: CheckpointEvidence) = events.emit(Event.CheckpointCaptured(device, context.testId, evidence.name))
        }
        val remaining = Duration.between(startedAt, input.deadline)
        val execution = if (remaining.isNegative || remaining.isZero) {
            timedOut(input, 0)
        } else {
            withTimeoutOrNull(remaining.toMillis().milliseconds) { devices.driver(context.device).execute(input.scenario, directory, listener) }
                ?: timedOut(input, remaining.toMillis())
        }
        events.emit(Event.ReplayEnded(device, context.testId, execution.failure?.summary ?: "every step ran"))
        return input.copy(currentEvidence = execution, attemptStartedAt = startedAt)
    }

    private suspend fun timedOut(state: AttemptState, durationMs: Long): ExecutionResult {
        val message = "the attempt time budget of ${state.context.budget.attemptTimeout.seconds} s was used up before the replay finished"
        val log = records.writeAttemptNote(state.context.paths, state.attemptNumber, "timeout.log", message + "\n")
        return ExecutionResult(ExecutionFailure.Environment(message), log.toString(), emptyList(), durationMs)
    }
}
