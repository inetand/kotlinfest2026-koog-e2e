package harness.judge

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLModel
import harness.Event
import harness.EventSink
import harness.platform.HarnessError
import harness.run.CriterionResult
import harness.run.Ruling
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The shape the model must answer in. Koog derives the JSON schema from these classes and their descriptions. */
@Serializable
@SerialName("JudgedRun")
@LLMDescription("The ruling for every acceptance criterion of one test run")
data class JudgedRun(
    @property:LLMDescription("One result per criterion id given in the request, in any order")
    val criteria: List<JudgedCriterion>,
)

@Serializable
@SerialName("JudgedCriterion")
@LLMDescription("The ruling for one acceptance criterion")
data class JudgedCriterion(
    @property:LLMDescription("Exactly one of the criterion ids listed in the request; each id at most once")
    val criterionId: String,
    @property:LLMDescription("PASS, FAIL or INCONCLUSIVE")
    val ruling: Ruling,
    @property:LLMDescription("One or two sentences: what the evidence shows that supports the ruling")
    val reason: String = "",
    @property:LLMDescription("Evidence references such as hierarchy:01_saved. A PASS or FAIL must cite at least one")
    val evidence: List<String> = emptyList(),
    @property:LLMDescription("Evidence that would have been needed but is absent")
    val missing: List<String> = emptyList(),
)

private val JUDGE_RULES = """
    You judge whether a mobile app run satisfied each acceptance criterion, using only the evidence given.
    Rules:
    - A replay that ran every step never proves a criterion by itself; cite evidence for every ruling.
    - Cite evidence as hierarchy:<checkpoint name>, using only checkpoint names given below.
    - Judge by intent, not by exact wording.
    - FAIL only when a captured screen shows the misbehaviour.
    - A criterion the evidence cannot show is INCONCLUSIVE, with what is missing listed.
    - Return exactly one result per criterion id given, and no other ids.
""".trimIndent()

/**
 * The judge that asks a model, through Koog's [PromptExecutor] and nothing more specific, so the model is swapped
 * by configuration. The answer is requested as a typed structure; a malformed one is sent back to be fixed a bounded
 * number of times and then becomes a harness error, never a verdict. Every call holds a model permit.
 */
class LlmJudge(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val permits: ModelPermits,
    private val fixRetries: Int,
    private val events: EventSink,
) : CriterionJudge {

    override suspend fun judge(input: JudgeInput): List<CriterionResult> {
        val judged = permits.holding {
            events.emit(Event.JudgeStarted(input.device, input.spec.id, permits.inUse, permits.limit))
            executor.executeStructured<JudgedRun>(prompt(input), model, fixingParser = StructureFixingParser(model, fixRetries))
                .getOrElse { failure -> throw HarnessError("judge: the model answer could not be parsed after $fixRetries fixing attempts: ${failure.message}", failure) }
                .data
        }
        return judged.criteria.map { CriterionResult(it.criterionId, it.ruling, it.reason.trim(), it.evidence, it.missing) }
    }

    fun prompt(input: JudgeInput): Prompt =
        prompt("judge-${input.spec.id}") {
            system(JUDGE_RULES)
            user(userText(input))
        }

    private fun userText(input: JudgeInput): String = buildString {
        appendLine("Specification '${input.spec.id}'. Goal: ${input.spec.goal}")
        appendLine("Acceptance criteria (return exactly one result per id):")
        input.spec.criteria.forEach { appendLine("- ${it.id}: ${it.text}") }
        appendLine()
        appendLine("How the replay ended: ${input.executionSummary}")
        input.checkpoints.forEach { checkpoint ->
            appendLine()
            appendLine("## checkpoint ${checkpoint.name} (traces criteria: ${checkpoint.criterionIds.ifEmpty { listOf("none") }.joinToString()})")
            appendLine(checkpoint.hierarchy ?: "hierarchy not available")
        }
        if (input.missingCheckpoints.isNotEmpty()) {
            appendLine()
            appendLine("Checkpoints never reached: ${input.missingCheckpoints.joinToString()}")
        }
    }
}
