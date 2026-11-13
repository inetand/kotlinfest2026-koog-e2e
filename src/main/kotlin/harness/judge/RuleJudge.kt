package harness.judge

import harness.Event
import harness.EventSink
import harness.run.CriterionResult
import harness.run.Ruling
import kotlinx.coroutines.delay
import kotlin.time.Duration

private val QUOTED = Regex("\"([^\"]+)\"")

/**
 * A judge with no model: a criterion passes when every phrase it puts in double quotes appears in the hierarchy of
 * a checkpoint that traces it, and fails when a checkpoint was captured but lacks one. It stands in for the shared
 * LLM call: it holds a model permit and takes [latency], so the concurrency of a run looks the same with either judge.
 */
class RuleJudge(
    private val permits: ModelPermits,
    private val latency: Duration,
    private val events: EventSink,
) : CriterionJudge {

    override suspend fun judge(input: JudgeInput): List<CriterionResult> =
        permits.holding {
            events.emit(Event.JudgeStarted(input.device, input.spec.id, permits.inUse, permits.limit))
            delay(latency)
            input.spec.criteria.map { criterion -> rule(criterion.id, criterion.text, input.checkpoints.filter { criterion.id in it.criterionIds }) }
        }

    private fun rule(id: String, text: String, checkpoints: List<JudgeCheckpoint>): CriterionResult {
        val phrases = QUOTED.findAll(text).map { it.groupValues[1] }.toList()
        val readable = checkpoints.filter { it.hierarchy != null }
        if (phrases.isEmpty()) {
            return CriterionResult(id, Ruling.INCONCLUSIVE, missing = listOf("the rule judge needs a quoted phrase to look for"))
        }
        if (readable.isEmpty()) {
            return CriterionResult(id, Ruling.INCONCLUSIVE, missing = listOf("no captured checkpoint traces this criterion"))
        }
        val proof = readable.firstOrNull { checkpoint -> phrases.all { checkpoint.hierarchy!!.contains(it) } }
        if (proof != null) {
            return CriterionResult(id, Ruling.PASS, "the screen shows ${phrases.joinToString { "\"$it\"" }}", listOf("hierarchy:${proof.name}"))
        }
        val last = readable.last()
        val absent = phrases.filterNot { last.hierarchy!!.contains(it) }
        return CriterionResult(id, Ruling.FAIL, "the screen does not show ${absent.joinToString { "\"$it\"" }}", listOf("hierarchy:${last.name}"))
    }
}
