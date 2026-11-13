package harness.graph.node

import harness.Event
import harness.EventSink
import harness.device.DeviceRegistry
import harness.graph.AttemptState
import harness.graph.Executed
import harness.graph.GraphNode
import org.springframework.stereotype.Component

/** Performs the scenario's action on the request's device. It knows the device only as a `DeviceDriver`. */
@Component
class ExecuteNode(
    private val devices: DeviceRegistry,
    private val events: EventSink,
) : GraphNode<AttemptState, Executed> {

    override val name = "execute"

    override suspend fun run(input: AttemptState): Executed {
        val request = input.request
        events.emit(Event.Acting(request.device, request.scenario.id, input.attempt))
        val screen = devices.driver(request.device).perform(request.scenario.action)
        events.emit(Event.Acted(request.device, request.scenario.id, screen.text))
        return Executed(input, screen)
    }
}
