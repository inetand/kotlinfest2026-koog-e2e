package harness

import org.springframework.stereotype.Component

/** One test: an action to perform on a device and the screen that must follow it. */
data class Scenario(val id: String, val action: String, val expectedScreen: String)

/** The built-in tests. They are fictional on purpose: the sample is about orchestration, not about an application. */
@Component
class ScenarioCatalog {

    val all: List<Scenario> = listOf(
        scenario("open-settings", "open settings"),
        scenario("search-catalog", "search catalog"),
        scenario("sync-inbox", "sync inbox"),
        scenario("upload-photo", "upload photo"),
        scenario("export-report", "export report"),
        scenario("print-receipt", "print receipt"),
    )

    /** The scenarios named by [ids], in catalog order; all of them when [ids] is empty. */
    fun select(ids: List<String>): List<Scenario> {
        val unknown = ids - all.map { it.id }.toSet()
        require(unknown.isEmpty()) { "unknown scenario: ${unknown.joinToString()} (see `harness list`)" }
        return if (ids.isEmpty()) all else all.filter { it.id in ids }
    }

    private fun scenario(id: String, action: String) = Scenario(id, action, expectedScreen = "done: $action")
}
