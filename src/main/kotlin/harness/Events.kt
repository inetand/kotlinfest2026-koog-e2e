package harness

import org.springframework.stereotype.Component
import kotlin.time.TimeSource

/** What happens during a run, as data. The console renders these; tests record them to observe overlap. */
sealed interface Event {
    val device: String
    val testId: String

    data class AttemptStarted(override val device: String, override val testId: String, val attempt: Int, val maxAttempts: Int) : Event
    data class CheckpointCaptured(override val device: String, override val testId: String, val name: String) : Event
    data class ReplayEnded(override val device: String, override val testId: String, val summary: String) : Event
    data class JudgeStarted(override val device: String, override val testId: String, val inUse: Int, val limit: Int) : Event
    data class JudgeFinished(override val device: String, override val testId: String, val ruling: String) : Event
    data class Retrying(override val device: String, override val testId: String, val reason: String) : Event
    data class Finished(override val device: String, override val testId: String, val status: String, val attempts: Int) : Event
}

fun interface EventSink {
    fun emit(event: Event)
}

/**
 * Prints one line per event to stderr with the time since start, so overlapping device and judge work is visible.
 * Stdout is left to the result lines, one JSON object per test, for whatever consumes the run.
 */
@Component
class ConsoleEventSink : EventSink {

    private val started = TimeSource.Monotonic.markNow()

    override fun emit(event: Event) {
        val (what, detail) = when (event) {
            is Event.AttemptStarted -> "replay >" to "attempt ${event.attempt}/${event.maxAttempts}"
            is Event.CheckpointCaptured -> "evidence" to event.name
            is Event.ReplayEnded -> "replay <" to event.summary
            is Event.JudgeStarted -> "judge >" to "${event.inUse}/${event.limit} permits in use"
            is Event.JudgeFinished -> "judge <" to event.ruling
            is Event.Retrying -> "retry" to event.reason
            is Event.Finished -> "RESULT" to "${event.status} after ${event.attempts} attempt(s)"
        }
        System.err.println("%6dms  %-9s %-14s %-9s %s".format(started.elapsedNow().inWholeMilliseconds, event.device, event.testId, what, detail))
    }
}
