package harness.run

import org.springframework.stereotype.Component

/** Renders a record as Markdown a person can read: the outcome first, then each criterion with its evidence. */
@Component
class ReportRenderer {

    fun render(record: RunRecord): String = buildString {
        appendLine("# ${record.title}: ${record.outcome.status}")
        appendLine()
        record.outcome.reason()?.let { appendLine("> $it").also { appendLine() } }
        appendLine("- Specification: `${record.specId}`")
        appendLine("- Run: `${record.runId}` on `${record.device}` (${record.backend}), app `${record.appId}`")
        appendLine("- Scenario: ${record.scenario}")
        appendLine("- Attempts: ${record.attempts.size}")
        appendLine()
        appendLine("## Goal")
        appendLine(record.goal)
        val last = record.attempts.lastOrNull()
        if (last != null) {
            appendLine()
            appendLine("## Criteria")
            appendLine("| Criterion | Ruling | Reason | Evidence |")
            appendLine("|---|---|---|---|")
            for (criterion in record.criteria) {
                val result = last.verdict.criteria.firstOrNull { it.criterionId == criterion.id }
                val reason = result?.let { (listOf(it.reason) + it.missing).filter(String::isNotBlank).joinToString("; ") }.orEmpty()
                appendLine("| ${criterion.text} | ${result?.ruling ?: "-"} | ${reason.cell()} | ${result?.evidence?.joinToString().orEmpty().cell()} |")
            }
            appendLine()
            appendLine("## Attempts")
            for (attempt in record.attempts) {
                val ended = attempt.execution.failure?.summary ?: "every step ran"
                appendLine("- Attempt ${attempt.number}: ${attempt.verdict.overall}, $ended (${attempt.execution.durationMs} ms), evidence in `${attempt.directory}`")
            }
        }
        if (record.warnings.isNotEmpty()) {
            appendLine()
            appendLine("## Warnings")
            record.warnings.forEach { appendLine("- $it") }
        }
    }

    private fun String.cell(): String = replace("|", "\\|").replace("\n", " ")
}
