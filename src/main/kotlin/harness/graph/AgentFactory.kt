package harness.graph

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaLLMProvider
import org.springframework.stereotype.Component

/** Koog's own cap on node executions; the attempt budget ends the loop long before it. */
private const val MAX_NODE_EXECUTIONS = 1_000

/** A model descriptor Koog requires; no request is ever sent to it. */
private val UNUSED_MODEL = LLModel(OllamaLLMProvider, "unused", listOf(LLMCapability.Completion))

/**
 * Builds a fresh Koog agent around the test graph. One agent runs one test, so two runs share no agent state;
 * [install] lets a test add Koog features such as graph assertions.
 */
@Component
class AgentFactory(
    private val graph: TestGraph,
    private val executor: PromptExecutor,
) {

    fun agent(id: String, install: GraphAIAgent.FeatureContext.() -> Unit = {}): GraphAIAgent<RunRequest, TestResult> =
        AIAgent(
            promptExecutor = executor,
            llmModel = UNUSED_MODEL,
            strategy = graph.strategy(),
            systemPrompt = "no LLM is called",
            id = id,
            maxIterations = MAX_NODE_EXECUTIONS,
        ) { install() }
}
