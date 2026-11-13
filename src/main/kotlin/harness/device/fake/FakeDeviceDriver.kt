package harness.device.fake

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
import harness.scenario.describe
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/** How a fake device replays one scenario. Each behavior is deterministic, so every run and every test is repeatable. */
enum class FakeBehavior { SUCCEEDS, FAILS_ONCE, ALWAYS_FAILS }

/**
 * The fake application for one specification: how its replay behaves, and what each checkpoint's screen shows.
 * The screens are the app's "truth", kept apart from the scenario, so a judge has something independent to rule on.
 */
@Serializable
data class FakeScript(
    val behavior: FakeBehavior = FakeBehavior.SUCCEEDS,
    /** Checkpoint name → the text of the screen at that checkpoint. */
    val screens: Map<String, String> = emptyMap(),
)

/** The fake scripts by specification id; a specification without one succeeds and shows empty screens. */
class FakeApp(private val scripts: Map<String, FakeScript> = emptyMap()) {
    fun scriptOf(specId: String): FakeScript = scripts[specId] ?: FakeScript()
}

/**
 * A device that exists only in memory. It walks the scenario's steps, waiting [stepLatency] before each with `delay`
 * so it never blocks a thread, and at every screenshot step writes the scripted screen as a hierarchy file: real
 * evidence on disk, as a real backend leaves. The replay counts are the device's own state, as a real device has
 * its own; nothing else shares them.
 */
class FakeDeviceDriver(
    private val slot: DeviceSlot,
    private val app: FakeApp,
    private val files: EvidenceFiles,
    private val stepLatency: Duration = Duration.ZERO,
) : DeviceDriver {

    override val backend: Backend = Backend.FAKE

    private val replays = ConcurrentHashMap<String, AtomicInteger>()

    /** How many times the scenario of [specId] was replayed on this device. */
    fun replaysOf(specId: String): Int = replays[specId]?.get() ?: 0

    override suspend fun observe(): Readiness = Readiness.Ready

    override suspend fun execute(scenario: Scenario, attemptDir: Path, listener: ExecutionListener): ExecutionResult {
        val started = System.nanoTime()
        val script = app.scriptOf(scenario.specId)
        val replay = replays.computeIfAbsent(scenario.specId) { AtomicInteger() }.incrementAndGet()
        val fails = when (script.behavior) {
            FakeBehavior.SUCCEEDS -> false
            FakeBehavior.FAILS_ONCE -> replay == 1
            FakeBehavior.ALWAYS_FAILS -> true
        }
        // A failing replay stops at its first step that touches the app, as a busy device would.
        val failAt = if (fails) scenario.steps.indexOfFirst { it !is FlowStep.Launch }.takeIf { it >= 0 } else null
        val logPath = attemptDir.resolve("device.log")
        val checkpoints = mutableListOf<CheckpointEvidence>()
        var failure: ExecutionFailure? = null

        for ((index, step) in scenario.steps.withIndex()) {
            listener.stepStarted(index, step)
            delay(stepLatency)
            if (index == failAt) {
                failure = ExecutionFailure.StepFailed(index, step, "the device was busy")
                files.appendText(logPath, "${slot.name} step ${index + 1} FAILED ${step.describe()}: the device was busy\n")
                break
            }
            files.appendText(logPath, "${slot.name} step ${index + 1} ok ${step.describe()}\n")
            if (step is FlowStep.Screenshot) {
                val screen = script.screens[step.name] ?: "(empty screen)"
                val evidence = CheckpointEvidence(step.name, screenshot = null, hierarchy = files.store(attemptDir.resolve("${step.name}.txt"), screen))
                checkpoints += evidence
                listener.checkpointCaptured(evidence)
            }
        }
        return ExecutionResult(failure, logPath.toString(), checkpoints, (System.nanoTime() - started) / 1_000_000)
    }
}
