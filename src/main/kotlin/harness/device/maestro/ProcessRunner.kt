package harness.device.maestro

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.nio.file.Path
import kotlin.time.Duration

/** What an external process left: its exit code and everything it printed. */
data class ProcessResult(val exitCode: Int, val output: String, val timedOut: Boolean = false, val started: Boolean = true) {
    val ok: Boolean get() = started && !timedOut && exitCode == 0

    /** The tool could not run at all, as opposed to running and reporting a failed step. */
    val environmentFailure: Boolean get() = !started || timedOut

    fun tail(chars: Int = 600): String = output.takeLast(chars).trim()
}

/**
 * Runs an external tool without blocking the caller's thread and without outliving the caller. The wait for the
 * process runs under `runInterruptible`, so a timeout or a cancelled parent interrupts it, and the process is
 * destroyed on the way out whatever the reason. Its output is read by a sibling coroutine: a blocking read cannot be
 * interrupted, but it ends by itself once the process is gone.
 */
class ProcessRunner(private val io: CoroutineDispatcher) {

    suspend fun run(command: List<String>, timeout: Duration, directory: Path? = null): ProcessResult =
        withContext(io) {
            val process = try {
                ProcessBuilder(command).directory(directory?.toFile()).redirectErrorStream(true).start()
            } catch (e: IOException) {
                return@withContext ProcessResult(-1, "${command.first()} could not be started: ${e.message}", started = false)
            }
            val output = async { process.inputStream.bufferedReader().readText() }
            val exitCode = try {
                withTimeoutOrNull(timeout) { runInterruptible { process.waitFor() } }
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
            if (exitCode == null) {
                ProcessResult(-1, "${command.first()} did not finish within $timeout\n${output.await()}", timedOut = true)
            } else {
                ProcessResult(exitCode, output.await())
            }
        }
}
