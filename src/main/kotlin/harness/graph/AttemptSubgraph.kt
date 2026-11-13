package harness.graph

import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.subgraph
import harness.graph.node.ExecuteNode
import harness.graph.node.RetryNode
import harness.graph.node.ValidateNode
import org.springframework.stereotype.Component

/**
 * The attempt subgraph, as structure only. It owns the retry loop; from outside it is one node that turns a ready
 * attempt into a final outcome:
 *
 *     execute → validate → retry ─(Retry)──▶ execute
 *                            └────(Finish)─▶ finish
 *
 * The subgraph holds no state of its own. Where the loop stands is the [AttemptState] on its edges; the `Retry`
 * edge hands the next state back to `execute`.
 */
@Component
class AttemptSubgraph(
    private val executeNode: ExecuteNode,
    private val validateNode: ValidateNode,
    private val retryNode: RetryNode,
) {

    fun delegate(): AIAgentSubgraphDelegate<AttemptState, AttemptOutcome> =
        subgraph<AttemptState, AttemptOutcome>(NAME) {
            val execute by executeNode.asNode()
            val validate by validateNode.asNode()
            val retry by retryNode.asNode()

            edge(nodeStart forwardTo execute)
            edge(execute forwardTo validate)
            edge(validate forwardTo retry)
            edge(retry forwardTo execute onCondition { it is AttemptDecision.Retry } transformed { (it as AttemptDecision.Retry).next })
            edge(retry forwardTo nodeFinish onCondition { it is AttemptDecision.Finish } transformed { (it as AttemptDecision.Finish).outcome })
        }

    companion object {
        const val NAME = "attempt"
    }
}
