package harness.spec

import harness.platform.UsageError
import harness.platform.sha256
import org.springframework.stereotype.Component

/** Enough of the digest to notice a changed specification, short enough to read in a scenario file. */
private const val HASH_LENGTH = 16

private val CRITERION = Regex("""^[-*]\s+([A-Za-z0-9_-]+)\s*:\s*(.+)$""")

/**
 * Reads a specification from Markdown:
 *
 *     # Title
 *     ## Goal
 *     One paragraph.
 *     ## Criteria
 *     - criterion-id: A sentence a judge can rule on.
 *
 * Anything it cannot read is a usage error, so a broken specification is never run and never gets a verdict.
 */
@Component
class MarkdownSpecificationParser {

    fun parse(id: String, text: String, sourcePath: String): Specification {
        val sections = sections(text)
        val goal = sections["goal"]?.joinToString(" ")?.trim().orEmpty()
        if (goal.isEmpty()) {
            throw UsageError("specification $id has no '## Goal' section")
        }
        val criteria = sections["criteria"].orEmpty().map { line ->
            val match = CRITERION.matchEntire(line) ?: throw UsageError("specification $id: '$line' is not '- id: text'")
            Criterion(match.groupValues[1], match.groupValues[2].trim())
        }
        if (criteria.isEmpty()) {
            throw UsageError("specification $id has no criteria under '## Criteria'")
        }
        val duplicates = criteria.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw UsageError("specification $id repeats criterion ids $duplicates")
        }
        val title = text.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: id
        return Specification(id, title, goal, criteria, text.trim().sha256().take(HASH_LENGTH), sourcePath)
    }

    /** Non-blank lines grouped under their lower-cased `## heading`. */
    private fun sections(text: String): Map<String, List<String>> {
        val sections = linkedMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in text.lines().map { it.trim() }) {
            when {
                line.startsWith("## ") -> current = sections.getOrPut(line.removePrefix("## ").trim().lowercase()) { mutableListOf() }
                line.isNotEmpty() && !line.startsWith("# ") -> current?.add(line)
            }
        }
        return sections
    }
}
