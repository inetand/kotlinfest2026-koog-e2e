package harness.graph

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import harness.graph.node.ReportNode
import harness.graph.node.ResolveNode
import org.springframework.stereotype.Component

/**
 * The graph of one test, as structure only:
 *
 *     resolve ─(Ready)──▶ attempt ─▶ report
 *        └─────(Blocked)────────────▶ report
 *
 * A resolve that blocks the test (missing or stale scenario) skips the attempt and goes straight to the report.
 * Everything about retrying lives inside the attempt subgraph, so this level stays readable as the test's outline.
 */
@Component
class TestGraph(
    private val resolveNode: ResolveNode,
    private val attemptSubgraph: AttemptSubgraph,
    private val reportNode: ReportNode,
) {

    fun strategy(): AIAgentGraphStrategy<RunRequest, Reported> =
        strategy<RunRequest, Reported>(NAME) {
            val resolve by resolveNode.asNode()
            val attempt by attemptSubgraph.delegate()
            val report by reportNode.asNode()

            edge(nodeStart forwardTo resolve)
            edge(resolve forwardTo attempt onCondition { it is Resolved.Ready } transformed { (it as Resolved.Ready).attempt })
            edge(resolve forwardTo report onCondition { it is Resolved.Blocked } transformed { (it as Resolved.Blocked).outcome })
            edge(attempt forwardTo report)
            edge(report forwardTo nodeFinish)
        }

    companion object {
        const val NAME = "test-run"
    }
}
