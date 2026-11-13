package harness

import harness.platform.UsageError
import harness.spec.Criterion
import harness.spec.MarkdownSpecificationParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SpecificationParserTest {

    private val parser = MarkdownSpecificationParser()
    private val text = "# Save a note\n\n## Goal\nThe user saves\na note.\n\n## Criteria\n- saved: The screen shows \"Saved\".\n- title-kept: The title is kept.\n"

    @Test
    fun `title, goal and criteria are read from their sections`() {
        val spec = parser.parse("save", text, "save.md")

        assertEquals("Save a note", spec.title)
        assertEquals("The user saves a note.", spec.goal)
        assertEquals(listOf(Criterion("saved", "The screen shows \"Saved\"."), Criterion("title-kept", "The title is kept.")), spec.criteria)
    }

    @Test
    fun `the content hash changes when the text changes, which is what makes a stored scenario stale`() {
        assertNotEquals(parser.parse("save", text, "").contentHash, parser.parse("save", text.replace("Saved", "Stored"), "").contentHash)
    }

    @Test
    fun `a specification without criteria is a usage error`() {
        assertFailsWith<UsageError> { parser.parse("save", "# Save\n\n## Goal\nSave.\n", "") }
    }

    @Test
    fun `a criterion line that is not id and text is a usage error`() {
        assertFailsWith<UsageError> { parser.parse("save", "## Goal\nSave.\n\n## Criteria\n- just a sentence\n", "") }
    }
}
