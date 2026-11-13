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
 * A Koog agent requires a prompt executor even when its graph has no LLM node. This sample never calls a model, so
 * the executor only fails loudly if something ever does. It is a placeholder, not an LLM integration.
 */
class NoLlmPromptExecutor : PromptExecutor() {

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
        error("this sample never calls an LLM")

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        flow { error("this sample never calls an LLM") }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        error("this sample never calls an LLM")

    override fun close() = Unit
}
