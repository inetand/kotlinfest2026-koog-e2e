package harness.pool

import harness.device.DeviceRegistry
import harness.device.DeviceSlot
import harness.graph.SpecificationRunner
import harness.platform.UsageError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

/**
 * Spreads the tests of a run across its devices: the waiting tests are one queue, and one worker per device takes
 * the next test until none is left. A worker owns its device, so devices need no lock. Each result reaches the
 * collector as soon as its test ends, in completion order.
 *
 * The queue and the workers are children of the `channelFlow` scope, which belongs to the collector. The flow
 * completes when all workers are done, and cancelling the collector cancels them all, down to a replay in flight.
 * Nothing is launched into a global scope, and no cancellation is caught.
 */
@Component
class TestPool(
    private val devices: DeviceRegistry,
    private val runner: SpecificationRunner,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun results(runId: String, testIds: List<String>): Flow<ResultLine> =
        channelFlow {
            val waiting = produce(capacity = Channel.UNLIMITED) { testIds.distinct().forEach { send(it) } }
            devices.slots.forEach { slot ->
                launch { for (testId in waiting) send(runOne(runId, testId, slot)) }
            }
        }

    /** A specification that cannot be read is reported and does not stop the others. */
    private suspend fun runOne(runId: String, testId: String, slot: DeviceSlot): ResultLine =
        try {
            ResultLine(runner.run(runId, testId, slot))
        } catch (e: UsageError) {
            ResultLine(testId, "INVALID", EXIT_USAGE, reason = e.message)
        }
}
