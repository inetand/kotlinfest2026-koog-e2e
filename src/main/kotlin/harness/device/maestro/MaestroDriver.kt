package harness.device.maestro

import harness.config.Backend
import harness.device.CheckpointEvidence
import harness.device.DeviceDriver
import harness.device.DeviceSlot
import harness.device.EvidenceFiles
import harness.device.ExecutionFailure
import harness.device.ExecutionListener
import harness.device.ExecutionResult
import harness.device.Readiness
import harness.scenario.FlowStep
import harness.scenario.Scenario
import harness.scenario.target
import java.nio.file.Path
import kotlin.time.Duration

private val NOT_FOUND_SIGNS = listOf("Element not found", "No element found", "could not find")

/**
 * Maestro on Android or iOS, driving the one device of its slot. Every call starts a new `maestro` process, so a
 * replay runs one `maestro test` per checkpoint segment (the steps up to and including a screenshot) and dumps the
 * hierarchy after each, which gives every checkpoint a screenshot and a hierarchy.
 *
 * This is a minimal driver: it has unit tests for the flow it writes, but it has not been exercised against a
 * device as part of this sample.
 */
class MaestroDriver(
    private val appId: String,
    private val slot: DeviceSlot,
    private val bin: String,
    private val timeout: Duration,
    private val process: ProcessRunner,
    private val writer: MaestroStepWriter,
    private val files: EvidenceFiles,
) : DeviceDriver {

    override val backend: Backend = Backend.MAESTRO

    override suspend fun observe(): Readiness {
        val result = process.run(baseCommand() + "hierarchy", timeout)
        return if (result.ok && result.output.contains('{')) Readiness.Ready else Readiness.NotReady("maestro hierarchy failed (exit ${result.exitCode}): ${result.tail()}")
    }

    override suspend fun execute(scenario: Scenario, attemptDir: Path, listener: ExecutionListener): ExecutionResult {
        val started = System.nanoTime()
        files.writeText(attemptDir.resolve("flow.yaml"), writer.write(appId, scenario.steps))
        val logPath = attemptDir.resolve("maestro.log")
        val checkpoints = mutableListOf<CheckpointEvidence>()
        var failure: ExecutionFailure? = null

        for ((number, segment) in segments(scenario.steps).withIndex()) {
            val directory = files.directory(attemptDir.resolve("seg-%02d".format(number + 1)))
            segment.steps.forEachIndexed { offset, step -> listener.stepStarted(segment.firstStepIndex + offset, step) }
            files.writeText(directory.resolve("segment.yaml"), writer.write(appId, segment.steps))
            val result = process.run(baseCommand() + listOf("test", "segment.yaml"), timeout, directory)
            files.appendText(logPath, "=== segment ${number + 1} exit ${result.exitCode} ===\n${result.output}\n")
            if (!result.ok) {
                failure = failureOf(result, segment)
                break
            }
            val checkpoint = (segment.steps.last() as? FlowStep.Screenshot)?.name ?: continue
            val evidence = capture(checkpoint, directory)
            checkpoints += evidence
            listener.checkpointCaptured(evidence)
        }
        return ExecutionResult(failure, logPath.toString(), checkpoints, (System.nanoTime() - started) / 1_000_000)
    }

    /** The screenshot Maestro took, then a fresh hierarchy dump. */
    private suspend fun capture(checkpoint: String, directory: Path): CheckpointEvidence {
        val screenshot = files.existing(directory.resolve("$checkpoint.png"))
        val dump = process.run(baseCommand() + "hierarchy", timeout)
        val hierarchy = if (dump.ok) files.store(directory.resolve("$checkpoint.json"), dump.output) else null
        return CheckpointEvidence(checkpoint, screenshot, hierarchy)
    }

    /** Names the step that failed: a "not found" message that mentions a step's target is typed as such. */
    private fun failureOf(result: ProcessResult, segment: Segment): ExecutionFailure {
        if (result.environmentFailure) {
            return ExecutionFailure.Environment("maestro exit ${result.exitCode}: ${result.tail()}")
        }
        if (NOT_FOUND_SIGNS.any { result.output.contains(it, ignoreCase = true) }) {
            segment.steps.forEachIndexed { offset, step ->
                val target = step.target()
                if (target != null && result.output.contains(target.id ?: target.text!!, ignoreCase = true)) {
                    return ExecutionFailure.ElementNotFound(segment.firstStepIndex + offset, step, target, result.tail(200))
                }
            }
        }
        // Maestro marks each command it completed; the first one without a mark is the one that failed.
        val completed = result.output.lines().count { it.contains("COMPLETED") || it.contains("✅") }
        val offset = completed.coerceIn(0, segment.steps.size - 1)
        return ExecutionFailure.StepFailed(segment.firstStepIndex + offset, segment.steps[offset], result.tail())
    }

    private fun baseCommand(): List<String> = listOf(bin, "--device", slot.name)

    private class Segment(val firstStepIndex: Int, val steps: List<FlowStep>)

    /** Cuts the steps after every screenshot, so each segment ends at a checkpoint (the last may not). */
    private fun segments(steps: List<FlowStep>): List<Segment> {
        val segments = mutableListOf<Segment>()
        var first = 0
        steps.forEachIndexed { index, step ->
            if (step is FlowStep.Screenshot || index == steps.lastIndex) {
                segments += Segment(first, steps.subList(first, index + 1))
                first = index + 1
            }
        }
        return segments
    }
}
