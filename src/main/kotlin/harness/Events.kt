package harness

import harness.graph.TestResult
import org.springframework.stereotype.Component
import kotlin.time.TimeSource

/** What happens during a run, as data. The console renders these; tests record them to observe overlap. */
sealed interface Event {
    data class Acting(val device: String, val scenarioId: String, val attempt: Int) : Event
    data class Acted(val device: String, val scenarioId: String, val screen: String) : Event
    data class VerifyStarted(val device: String, val scenarioId: String, val inFlight: Int, val permits: Int) : Event
    data class VerifyFinished(val device: String, val scenarioId: String, val passed: Boolean) : Event
    data class Finished(val result: TestResult) : Event
}

fun interface EventSink {
    fun emit(event: Event)
}

/** Prints one line per event with the time since start, so overlapping device and verifier work is visible. */
@Component
class ConsoleEventSink : EventSink {

    private val started = TimeSource.Monotonic.markNow()

    override fun emit(event: Event) {
        val line = when (event) {
            is Event.Acting -> row(event.device, "act", "${event.scenarioId} (attempt ${event.attempt})")
            is Event.Acted -> row(event.device, "screen", "${event.scenarioId} -> \"${event.screen}\"")
            is Event.VerifyStarted -> row(event.device, "verify >", "${event.scenarioId} [${event.inFlight}/${event.permits} permits in use]")
            is Event.VerifyFinished -> row(event.device, "verify <", "${event.scenarioId} ${if (event.passed) "pass" else "fail"}")
            is Event.Finished -> row(event.result.device, "RESULT", "${event.result.scenarioId} ${event.result.outcome.label} after ${event.result.attempts} attempt(s)")
        }
        println(line)
    }

    private fun row(device: String, what: String, detail: String): String =
        "%6dms  %-8s %-9s %s".format(started.elapsedNow().inWholeMilliseconds, device, what, detail)
}
