package harness.run

import org.springframework.stereotype.Component

/**
 * The verdict rules, as code: a criterion contradicted by evidence fails the test; one that cannot be judged leaves
 * it inconclusive; all passing is a pass, with warnings when there are any. What a model says about its own
 * confidence never enters these rules.
 */
@Component
class OutcomeRules {

    fun overall(results: List<CriterionResult>): Ruling =
        when {
            results.any { it.ruling == Ruling.FAIL } -> Ruling.FAIL
            results.isEmpty() || results.any { it.ruling != Ruling.PASS } -> Ruling.INCONCLUSIVE
            else -> Ruling.PASS
        }

    fun outcomeOf(verdict: AttemptVerdict, warnings: List<String>): RunOutcome =
        when (verdict.overall) {
            Ruling.FAIL -> RunOutcome.Fail(
                verdict.criteria.filter { it.ruling == Ruling.FAIL }
                    .joinToString("; ") { "criterion ${it.criterionId}: ${it.reason.ifBlank { "contradicted by evidence" }}" },
            )
            Ruling.INCONCLUSIVE -> RunOutcome.Inconclusive(inconclusiveReason(verdict))
            Ruling.PASS -> if (warnings.isEmpty()) RunOutcome.Pass else RunOutcome.PassWithWarnings(warnings)
        }

    private fun inconclusiveReason(verdict: AttemptVerdict): String =
        verdict.note ?: verdict.criteria.filter { it.ruling != Ruling.PASS }
            .joinToString("; ") { "criterion ${it.criterionId}: ${(it.missing + it.reason).firstOrNull { text -> text.isNotBlank() } ?: "not judged"}" }
}
