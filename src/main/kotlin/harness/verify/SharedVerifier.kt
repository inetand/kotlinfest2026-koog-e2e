package harness.verify

import harness.Event
import harness.EventSink
import harness.Scenario
import harness.device.Screen
import harness.graph.Verdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration

/**
 * Stands in for the shared LLM call of the talk: one verifier serves every device, takes a while, and must not be
 * called by too many at once. The verdict itself is a fixed rule (the screen must equal the expected one), so no
 * model is involved. Only this call is limited by the [Semaphore]; device work is never held back by it.
 * `withPermit` releases the permit when the call ends, fails or is cancelled.
 */
class SharedVerifier(
    private val permits: Int,
    private val latency: Duration,
    private val events: EventSink,
) {
    private val semaphore = Semaphore(permits)

    suspend fun verify(device: String, scenario: Scenario, screen: Screen): Verdict =
        semaphore.withPermit {
            events.emit(Event.VerifyStarted(device, scenario.id, inFlight = permits - semaphore.availablePermits, permits = permits))
            delay(latency)
            val verdict = if (screen.text == scenario.expectedScreen) {
                Verdict.Pass
            } else {
                Verdict.Fail("expected \"${scenario.expectedScreen}\" but saw \"${screen.text}\"")
            }
            events.emit(Event.VerifyFinished(device, scenario.id, verdict is Verdict.Pass))
            verdict
        }
}
