package harness.graph

import harness.Event
import harness.EventSink
import harness.Scenario
import org.springframework.stereotype.Component

/** Runs one scenario on one device as one independent graph execution. */
@Component
class TestRunner(
    private val agents: AgentFactory,
    private val events: EventSink,
) {

    suspend fun run(scenario: Scenario, device: String, maxAttempts: Int): TestResult {
        val agent = agents.agent(id = "${scenario.id}@$device")
        val result = try {
            agent.run(RunRequest(scenario, device, maxAttempts), null)
        } finally {
            agent.close()
        }
        events.emit(Event.Finished(result))
        return result
    }
}
