package harness.judge

import harness.run.CriterionResult
import harness.spec.Specification

/** One captured checkpoint as a judge sees it. */
data class JudgeCheckpoint(
    val name: String,
    /** The criteria this checkpoint is meant to prove, from the scenario. */
    val criterionIds: List<String>,
    val screenshotPath: String?,
    /** The text of the captured hierarchy; null when none was captured. */
    val hierarchy: String?,
)

data class JudgeInput(
    val spec: Specification,
    val device: String,
    val checkpoints: List<JudgeCheckpoint>,
    /** Checkpoints the scenario names that the replay never reached. */
    val missingCheckpoints: List<String>,
    /** How the replay ended, in words: every step ran, or where it stopped. */
    val executionSummary: String,
)

/**
 * Rules on each criterion from stored evidence. The answer is a claim, not a verdict: [harness.run.AttemptValidator]
 * checks its shape and its citations before any of it counts.
 */
interface CriterionJudge {
    suspend fun judge(input: JudgeInput): List<CriterionResult>
}
