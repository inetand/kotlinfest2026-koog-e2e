package harness

import ai.koog.agents.testing.feature.testGraph
import ai.koog.agents.testing.feature.withTesting
import harness.graph.AttemptState
import harness.graph.Decision
import harness.graph.Executed
import harness.graph.Outcome
import harness.graph.RunRequest
import harness.graph.TestGraph
import harness.graph.TestResult
import harness.graph.Verified
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Structure: Koog's testing feature checks which nodes exist, how they are connected, and where each decision
 * leads. No scenario is played to learn this; the assertions are evaluated against the graph when the agent starts.
 */
class GraphStructureTest {

    private val request = RunRequest(scenario("tap button"), "kiosk-1", maxAttempts = 3)
    private val first = AttemptState.first(request)
    private val passed = TestResult(request.scenario.id, request.device, Outcome.Passed, attempts = 1)

    @Test
    fun `the test graph is start, attempt, finish, and the retry loop is inside attempt`() {
        val agent = Wiring().agents.agent("structure") {
            withTesting()
            testGraph<RunRequest, TestResult>(TestGraph.NAME) {
                val attempt = assertSubgraphByName<AttemptState, TestResult>("attempt")

                assertEdges {
                    startNode() alwaysGoesTo attempt
                    attempt alwaysGoesTo finishNode()
                }

                verifySubgraph(attempt) {
                    val execute = assertNodeByName<AttemptState, Executed>("execute")
                    val verify = assertNodeByName<Executed, Verified>("verify")
                    val decide = assertNodeByName<Verified, Decision>("decide")

                    assertReachable(startNode(), execute)
                    assertReachable(execute, verify)
                    assertReachable(verify, decide)
                    assertReachable(decide, finishNode())

                    // Where each decision leads: the edge conditions are evaluated against a sample output, and no
                    // node body runs. What decide answers for a given input is DecideNodeTest's subject.
                    assertEdges {
                        startNode() alwaysGoesTo execute
                        execute alwaysGoesTo verify
                        verify alwaysGoesTo decide
                        decide withOutput Decision.Retry(first.next()) goesTo execute
                        decide withOutput Decision.Finish(passed) goesTo finishNode()
                    }
                }
            }
        }

        // The assertions run when the agent starts; a mismatch fails here.
        runBlocking { agent.run(request, null) }
    }
}
