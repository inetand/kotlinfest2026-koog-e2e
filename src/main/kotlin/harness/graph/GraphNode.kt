package harness.graph

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node

/**
 * A node body as an ordinary class that knows nothing of Koog. Spring injects its collaborators through the
 * constructor, and it can be unit tested by calling [run]. The graph classes only wire nodes together.
 */
interface GraphNode<In, Out> {
    val name: String

    suspend fun run(input: In): Out
}

/** Adapts a [GraphNode] onto Koog's typed node delegate; the one place a node body meets Koog. */
inline fun <reified In, reified Out> GraphNode<In, Out>.asNode(): AIAgentNodeDelegate<In, Out> =
    node(name) { input -> run(input) }
