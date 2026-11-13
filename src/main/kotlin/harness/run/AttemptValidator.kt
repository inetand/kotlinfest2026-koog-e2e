package harness.run

import harness.Event
import harness.EventSink
import harness.device.CheckpointEvidence
import harness.device.EvidenceFiles
import harness.device.ExecutionResult
import harness.judge.CriterionJudge
import harness.judge.JudgeCheckpoint
import harness.judge.JudgeInput
import harness.platform.runCatchingCancellable
import harness.platform.sha256
import harness.scenario.Scenario
import harness.spec.Specification
import org.springframework.stereotype.Component

/**
 * Judges an attempt from stored evidence only, in four phases: the evidence is checked for presence and integrity,
 * the judge is asked, the shape of its answer is checked, and the evidence rules are applied to each criterion
 * before the rulings are aggregated. It reads files, never the device, so it cannot be swayed by live state. The
 * judge's answer is only a claim; what counts is decided here, in code.
 */
@Component
class AttemptValidator(
    private val files: EvidenceFiles,
    private val judge: CriterionJudge,
    private val rules: OutcomeRules,
    private val events: EventSink,
) {

    suspend fun validate(spec: Specification, scenario: Scenario, execution: ExecutionResult, device: String): AttemptVerdict {
        val captured = execution.checkpoints
        checkEvidence(scenario, execution)?.let { return it.forEvery(spec) }

        val judged = judge.judge(
            JudgeInput(
                spec = spec,
                device = device,
                checkpoints = captured.map { JudgeCheckpoint(it.name, scenario.criteriaOf(it.name), it.screenshot?.path, it.hierarchy?.let { h -> files.readBytes(h.path).decodeToString() }) },
                missingCheckpoints = scenario.checkpoints.map { it.name }.filter { name -> captured.none { it.name == name } },
                executionSummary = execution.failure?.summary ?: "every step of the scenario ran",
            ),
        )
        checkShape(spec, judged)?.let { return it.forEvery(spec) }

        val byId = judged.associateBy { it.criterionId }
        val results = spec.criteria.map { enforceEvidenceRules(byId[it.id], it.id, captured) }
        val overall = rules.overall(results)
        events.emit(Event.JudgeFinished(device, spec.id, overall.name))
        val note = execution.failure?.takeIf { overall != Ruling.FAIL }?.let { "the replay stopped early: ${it.summary}" }
        return AttemptVerdict(overall, results, note)
    }

    /** Phase 1: something was captured, and every artifact still matches its digest. */
    private suspend fun checkEvidence(scenario: Scenario, execution: ExecutionResult): Unprovable? {
        if (execution.checkpoints.isEmpty()) {
            val why = "no checkpoint evidence was captured (${execution.failure?.summary ?: "the replay captured nothing"})"
            return Unprovable(why, missing = scenario.checkpoints.map { "checkpoint ${it.name}" })
        }
        for (artifact in execution.checkpoints.flatMap { listOfNotNull(it.screenshot, it.hierarchy) }) {
            val actual = runCatchingCancellable { files.readBytes(artifact.path).sha256() }.getOrNull()
            if (actual != artifact.sha256) {
                return Unprovable("evidence_integrity: ${artifact.path} is missing or does not match its digest", harnessFault = "evidence_integrity")
            }
        }
        return null
    }

    /** Phase 3: one result per criterion id at most, and only ids the specification knows. */
    private fun checkShape(spec: Specification, judged: List<CriterionResult>): Unprovable? {
        val known = spec.criteria.map { it.id }.toSet()
        val duplicates = judged.groupingBy { it.criterionId }.eachCount().filterValues { it > 1 }.keys
        val unknown = judged.map { it.criterionId }.filter { it !in known }
        return if (duplicates.isEmpty() && unknown.isEmpty()) {
            null
        } else {
            Unprovable("the judge returned duplicate $duplicates / unknown $unknown criterion ids", harnessFault = "malformed_judgement")
        }
    }

    /** Phase 4: a ruling without a citation of a captured checkpoint is not accepted, and a skipped criterion is not verified. */
    private fun enforceEvidenceRules(judged: CriterionResult?, criterionId: String, captured: List<CheckpointEvidence>): CriterionResult {
        if (judged == null) {
            return CriterionResult(criterionId, Ruling.INCONCLUSIVE, missing = listOf("the judge returned no result for this criterion"))
        }
        val cites = judged.evidence.any { reference -> captured.any { reference.substringAfter(':') == it.name } }
        return when {
            judged.ruling == Ruling.PASS && !cites ->
                judged.copy(ruling = Ruling.INCONCLUSIVE, missing = judged.missing + "PASS claimed without citing a captured checkpoint")
            judged.ruling == Ruling.FAIL && !cites ->
                judged.copy(ruling = Ruling.INCONCLUSIVE, missing = judged.missing + "FAIL claimed without citing a captured checkpoint")
            else -> judged
        }
    }

    private class Unprovable(val why: String, val missing: List<String> = emptyList(), val harnessFault: String? = null) {
        fun forEvery(spec: Specification): AttemptVerdict =
            AttemptVerdict(
                overall = Ruling.INCONCLUSIVE,
                criteria = spec.criteria.map { CriterionResult(it.id, Ruling.INCONCLUSIVE, missing = listOf(why) + missing) },
                note = why,
                harnessFault = harnessFault,
            )
    }
}
