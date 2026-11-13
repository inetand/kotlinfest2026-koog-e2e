package harness

import harness.device.DeviceRegistry
import harness.device.FakeDeviceDriver
import harness.device.FakeScript
import harness.graph.AgentFactory
import harness.graph.AttemptSubgraph
import harness.graph.NoLlmPromptExecutor
import harness.graph.TestGraph
import harness.graph.TestRunner
import harness.graph.node.DecideNode
import harness.graph.node.ExecuteNode
import harness.graph.node.VerifyNode
import harness.pool.TestPool
import harness.verify.SharedVerifier
import java.util.Collections
import kotlin.time.Duration

/** Records events in arrival order. */
class RecordingEventSink : EventSink {
    private val recorded = Collections.synchronizedList(mutableListOf<Event>())

    val events: List<Event>
        get() = synchronized(recorded) { recorded.toList() }

    override fun emit(event: Event) {
        recorded += event
    }
}

/**
 * The production classes, wired by hand with plain constructors. Because every collaborator arrives through a
 * constructor, a test builds the same object graph Spring builds, without Spring.
 */
class Wiring(
    script: FakeScript = FakeScript(),
    devices: Int = 1,
    verifierPermits: Int = 2,
    deviceLatency: Duration = Duration.ZERO,
    verifierLatency: Duration = Duration.ZERO,
) {
    val events = RecordingEventSink()

    val drivers = (1..devices).map { FakeDeviceDriver("kiosk-$it", script, deviceLatency) }

    private val registry = DeviceRegistry(drivers)

    private val verifier = SharedVerifier(verifierPermits, verifierLatency, events)

    val graph = TestGraph(AttemptSubgraph(ExecuteNode(registry, events), VerifyNode(verifier), DecideNode()))

    val agents = AgentFactory(graph, NoLlmPromptExecutor())

    val runner = TestRunner(agents, events)

    val pool = TestPool(registry, runner)
}

fun scenario(action: String) = Scenario(action.replace(' ', '-'), action, "done: $action")
