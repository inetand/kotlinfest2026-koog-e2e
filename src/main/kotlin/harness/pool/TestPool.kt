package harness.pool

import harness.Scenario
import harness.device.DeviceRegistry
import harness.graph.TestResult
import harness.graph.TestRunner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

/**
 * Spreads tests across the devices: the waiting tests are one queue, and one worker per device takes the next test
 * until none is left. Results reach the collector as each test ends, in completion order.
 *
 * The workers are children of the `channelFlow` scope, which belongs to the collector. The flow completes when all
 * of them are done, a failure in one cancels the others, and cancelling the collector cancels them all. Nothing is
 * launched into a global scope, and no cancellation is caught.
 */
@Component
class TestPool(
    private val devices: DeviceRegistry,
    private val runner: TestRunner,
) {

    fun run(scenarios: List<Scenario>, maxAttempts: Int): Flow<TestResult> =
        channelFlow {
            val waiting = Channel<Scenario>(Channel.UNLIMITED)
            scenarios.forEach { waiting.send(it) }
            waiting.close()

            devices.names.forEach { device ->
                launch {
                    for (scenario in waiting) {
                        send(runner.run(scenario, device, maxAttempts))
                    }
                }
            }
        }
}
