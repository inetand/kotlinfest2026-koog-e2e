package harness.device

import harness.platform.sha256
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/** Writes and reads evidence files on the IO dispatcher, so file access never blocks the thread the workers share. */
class EvidenceFiles(
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) {

    suspend fun directory(path: Path): Path = withContext(io) { Files.createDirectories(path) }

    suspend fun writeText(path: Path, text: String): Unit = withContext(io) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }

    suspend fun appendText(path: Path, text: String): Unit = withContext(io) {
        Files.createDirectories(path.parent)
        Files.writeString(path, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    /** Stores [text] and returns it as an artifact with its digest. */
    suspend fun store(path: Path, text: String): Artifact {
        writeText(path, text)
        return Artifact(path.toString(), text.sha256(), clock.instant().toString())
    }

    /** An artifact for a file some tool already wrote; null when it is not there. */
    suspend fun existing(path: Path): Artifact? = withContext(io) {
        if (path.exists()) Artifact(path.toString(), path.readBytes().sha256(), clock.instant().toString()) else null
    }

    suspend fun readBytes(path: String): ByteArray = withContext(io) { Path.of(path).readBytes() }
}
