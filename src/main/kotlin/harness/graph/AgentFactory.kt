package harness.graph

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import org.springframework.stereotype.Component

/** Koog's own cap on node executions; the attempt budget ends the loop long before it. */
private const val MAX_NODE_EXECUTIONS = 1_000

/**
 * Builds a fresh Koog agent around the test graph. One agent runs one test, so two runs share no agent state. The
 * agent's own session is never used to call the model: the judge calls the executor it was given. [install] lets a
 * test add Koog features such as graph assertions.
 */
@Component
class AgentFactory(
    private val graph: TestGraph,
    private val executor: PromptExecutor,
    private val model: LLModel,
) {

    fun agent(id: String, install: GraphAIAgent.FeatureContext.() -> Unit = {}): GraphAIAgent<RunRequest, Reported> =
        AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = graph.strategy(),
            systemPrompt = "the graph does not use the agent's own session",
            id = id,
            maxIterations = MAX_NODE_EXECUTIONS,
        ) { install() }
}
