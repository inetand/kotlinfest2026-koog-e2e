package harness.run

import harness.device.EvidenceFiles
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.nio.file.Path

/** Writes what a test leaves behind: its attempt directories, its record and its report. */
@Component
class RunRecordRepository(
    private val files: EvidenceFiles,
    private val json: Json,
    private val renderer: ReportRenderer,
) {

    suspend fun createAttemptDirectory(paths: TestPaths, number: Int): Path = files.directory(paths.attemptDirectory(number))

    /** A log for an attempt that produced none of its own, e.g. one cut off by the deadline. */
    suspend fun writeAttemptNote(paths: TestPaths, number: Int, name: String, text: String): Path {
        val file = paths.attemptDirectory(number).resolve(name)
        files.writeText(file, text)
        return file
    }

    suspend fun write(record: RunRecord, paths: TestPaths) {
        files.writeText(paths.recordFile, json.encodeToString(RunRecord.serializer(), record))
        files.writeText(paths.reportFile, renderer.render(record))
    }
}
