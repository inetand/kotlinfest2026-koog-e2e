package harness.spec

import harness.config.HarnessProperties
import harness.platform.UsageError
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/** The specifications of the workspace: `specs/<id>.md`. */
@Component
class SpecificationRepository(
    private val props: HarnessProperties,
    private val parser: MarkdownSpecificationParser,
) {
    private val directory: Path
        get() = Path.of(props.root, "specs")

    fun ids(): List<String> =
        if (directory.isDirectory()) directory.listDirectoryEntries("*.md").map { it.nameWithoutExtension }.sorted() else emptyList()

    fun find(id: String): Specification {
        val file = directory.resolve("$id.md")
        if (!file.normalize().startsWith(directory.normalize()) || !file.exists() || Files.isDirectory(file)) {
            throw UsageError("no specification '$id' in $directory")
        }
        return parser.parse(id, file.readText(), file.toString())
    }
}
