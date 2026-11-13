package harness.graph

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import org.springframework.stereotype.Component

/**
 * The graph of one test, as structure only:
 *
 *     start → attempt → finish
 *
 * Everything about retrying lives inside the attempt subgraph, so this level stays readable as the test's outline.
 */
@Component
class TestGraph(private val attemptSubgraph: AttemptSubgraph) {

    fun strategy(): AIAgentGraphStrategy<RunRequest, TestResult> =
        strategy<RunRequest, TestResult>(NAME) {
            val attempt by attemptSubgraph.delegate()

            edge(nodeStart forwardTo attempt transformed { AttemptState.first(it) })
            edge(attempt forwardTo nodeFinish)
        }

    companion object {
        const val NAME = "test-run"
    }
}
