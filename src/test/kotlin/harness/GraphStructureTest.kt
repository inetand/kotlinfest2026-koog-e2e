package harness

import ai.koog.agents.testing.feature.testGraph
import ai.koog.agents.testing.feature.withTesting
import harness.device.DeviceSlot
import harness.graph.AttemptDecision
import harness.graph.AttemptOutcome
import harness.graph.AttemptState
import harness.graph.AttemptSubgraph
import harness.graph.Reported
import harness.graph.Resolved
import harness.graph.RunContext
import harness.graph.RunRequest
import harness.graph.TestGraph
import harness.graph.Validated
import harness.run.RunBudget
import harness.run.RunOutcome
import harness.run.TestPaths
import harness.scenario.Scenario
import harness.spec.Specification
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.Test

/**
 * Structure: Koog's testing feature checks which nodes exist, with which input and output types, how they are
 * connected, and where each branch leads. No scenario is replayed to learn this; the assertions are evaluated
 * against the graph when the agent starts, and no node body runs for them.
 */
class GraphStructureTest {

    private val context = RunContext(
        runId = "run-1",
        testId = "save",
        spec = Specification("save", "save", "goal", emptyList(), "hash", "save.md"),
        device = DeviceSlot("kiosk-1"),
        backend = harness.config.Backend.FAKE,
        appId = "com.example.notes",
        paths = TestPaths(Path.of("unused"), "run-1", "save"),
        startedAt = Instant.EPOCH,
        budget = RunBudget(3, Duration.ofMinutes(1)),
        scenarioStatus = "fresh",
    )
    private val state = AttemptState(context, Scenario("save", "hash", "com.example.notes", emptyList(), emptyList()), attemptNumber = 1, retriesRemaining = 2, deadline = Instant.MAX)
    private val outcome = AttemptOutcome(context, RunOutcome.Pass)

    @Test
    fun `the test graph is resolve, attempt, report, and the retry loop is inside attempt`() {
        val wiring = Wiring()
        wiring.workspace.test("save", withScenario = false)
        val agent = wiring.agents.agent("structure") {
            withTesting()
            testGraph<RunRequest, Reported>(TestGraph.NAME) {
                val resolve = assertNodeByName<RunRequest, Resolved>("resolve")
                val attempt = assertSubgraphByName<AttemptState, AttemptOutcome>(AttemptSubgraph.NAME)
                val report = assertNodeByName<AttemptOutcome, Reported>("report")

                assertEdges {
                    startNode() alwaysGoesTo resolve
                    resolve withOutput Resolved.Ready(state) goesTo attempt
                    resolve withOutput Resolved.Blocked(outcome) goesTo report
                    attempt alwaysGoesTo report
                    report alwaysGoesTo finishNode()
                }

                verifySubgraph(attempt) {
                    val execute = assertNodeByName<AttemptState, AttemptState>("execute")
                    val validate = assertNodeByName<AttemptState, Validated>("validate")
                    val retry = assertNodeByName<Validated, AttemptDecision>("retry")

                    assertReachable(startNode(), retry)
                    assertReachable(retry, execute)

                    assertEdges {
                        startNode() alwaysGoesTo execute
                        execute alwaysGoesTo validate
                        validate alwaysGoesTo retry
                        retry withOutput AttemptDecision.Retry(state) goesTo execute
                        retry withOutput AttemptDecision.Finish(outcome) goesTo finishNode()
                    }
                }
            }
        }

        // The assertions run when the agent starts; a mismatch fails here. The run itself ends at resolve (no scenario).
        runBlocking { agent.run(RunRequest("run-1", "save", DeviceSlot("kiosk-1")), null) }
    }
}
