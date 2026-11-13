package harness.graph

import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.subgraph
import harness.graph.node.DecideNode
import harness.graph.node.ExecuteNode
import harness.graph.node.VerifyNode
import org.springframework.stereotype.Component

/**
 * The attempt subgraph, as structure only. It owns the retry loop; from outside it is one node that turns the first
 * attempt's state into a final result:
 *
 *     execute → verify → decide ─(Retry)──▶ execute
 *                           └────(Finish)─▶ finish
 *
 * The subgraph holds no counter of its own. The attempt number and the remaining budget travel on the edges, inside
 * [AttemptState]; the `Retry` edge hands the next state back to `execute`.
 */
@Component
class AttemptSubgraph(
    private val executeNode: ExecuteNode,
    private val verifyNode: VerifyNode,
    private val decideNode: DecideNode,
) {

    fun delegate(): AIAgentSubgraphDelegate<AttemptState, TestResult> =
        subgraph<AttemptState, TestResult>("attempt") {
            val execute by executeNode.asNode()
            val verify by verifyNode.asNode()
            val decide by decideNode.asNode()

            edge(nodeStart forwardTo execute)
            edge(execute forwardTo verify)
            edge(verify forwardTo decide)
            edge(decide forwardTo execute onCondition { it is Decision.Retry } transformed { (it as Decision.Retry).next })
            edge(decide forwardTo nodeFinish onCondition { it is Decision.Finish } transformed { (it as Decision.Finish).result })
        }
}
