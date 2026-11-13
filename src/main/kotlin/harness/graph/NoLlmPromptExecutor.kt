package harness.graph

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A Koog agent requires a prompt executor even when nothing calls a model. With the rule judge nothing does, so
 * this executor only fails loudly if something ever tries. It is a placeholder, not an LLM integration.
 */
class NoLlmPromptExecutor : PromptExecutor() {

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
        error("no LLM is configured (harness.judge=rule)")

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        flow { error("no LLM is configured (harness.judge=rule)") }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        error("no LLM is configured (harness.judge=rule)")

    override fun close() = Unit
}
