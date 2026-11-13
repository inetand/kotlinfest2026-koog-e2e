package harness

import harness.device.maestro.MaestroStepWriter
import harness.device.maestro.ProcessRunner
import harness.scenario.FlowStep
import harness.scenario.Selector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The parts of the Maestro backend that need no device: the flow it writes, and how it runs an external process. */
class MaestroTest {

    @Test
    fun `steps are written as a Maestro flow`() {
        val flow = MaestroStepWriter().write(
            "com.example.notes",
            listOf(
                FlowStep.Launch(clearState = true),
                FlowStep.Tap(Selector(id = "fab_add_note")),
                FlowStep.InputText("Groceries"),
                FlowStep.Tap(Selector(text = "Save (1)", index = 0)),
                FlowStep.Back,
                FlowStep.Screenshot("01_saved"),
            ),
        )

        assertEquals(
            """
            appId: com.example.notes
            ---
            - launchApp:
                clearState: true
            - tapOn:
                id: fab_add_note
            - inputText: Groceries
            - tapOn:
                text: Save \(1\)
                index: 0
            - back
            - takeScreenshot: 01_saved
            """.trimIndent() + "\n",
            flow,
        )
    }

    @Test
    fun `a process that finishes reports its exit code and output`() {
        val result = runBlocking { ProcessRunner(Dispatchers.IO).run(listOf("sh", "-c", "echo hello; exit 3"), 10.seconds) }

        assertEquals(3, result.exitCode)
        assertEquals("hello", result.output.trim())
        assertEquals(false, result.environmentFailure)
    }

    @Test
    fun `a process that runs too long is cut off and counts as an environment failure`() {
        var result: harness.device.maestro.ProcessResult? = null
        val elapsed = measureTimeMillis {
            result = runBlocking { ProcessRunner(Dispatchers.IO).run(listOf("sleep", "30"), 200.milliseconds) }
        }

        assertTrue(result!!.timedOut && result!!.environmentFailure)
        assertTrue(elapsed < 5_000, "the wait was interrupted, not sat out ($elapsed ms)")
    }

    @Test
    fun `a tool that is not installed is an environment failure, not a crash`() {
        val result = runBlocking { ProcessRunner(Dispatchers.IO).run(listOf("no-such-tool-here"), 10.seconds) }

        assertTrue(result.environmentFailure)
    }

    @Test
    fun `cancelling the caller interrupts the wait`() {
        val elapsed = measureTimeMillis {
            runBlocking {
                val job = launch { ProcessRunner(Dispatchers.IO).run(listOf("sleep", "30"), 60.seconds) }
                delay(200.milliseconds)
                job.cancelAndJoin()
            }
        }

        assertTrue(elapsed < 5_000, "cancellation reached the blocked wait ($elapsed ms)")
    }
}
