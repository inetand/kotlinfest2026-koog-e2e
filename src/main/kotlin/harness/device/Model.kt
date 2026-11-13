package harness.device

import harness.scenario.FlowStep
import harness.scenario.Selector
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One device of a run: a fake's name, or the serial a real backend addresses it by. */
@Serializable
data class DeviceSlot(val name: String)

/** A file of evidence, referenced by path and digest so a later reader can tell it is the file that was captured. */
@Serializable
data class Artifact(val path: String, val sha256: String, val capturedAt: String)

/** What was captured at one checkpoint. Either artifact may be absent when the backend could not produce it. */
@Serializable
data class CheckpointEvidence(val name: String, val screenshot: Artifact?, val hierarchy: Artifact?)

/** Why a replay stopped before its last step, typed so the attempt loop can route on it. */
@Serializable
sealed interface ExecutionFailure {
    val summary: String

    @Serializable
    @SerialName("stepFailed")
    data class StepFailed(val stepIndex: Int, val step: FlowStep, val message: String) : ExecutionFailure {
        override val summary: String
            get() = "step ${stepIndex + 1} failed: $message"
    }

    @Serializable
    @SerialName("elementNotFound")
    data class ElementNotFound(val stepIndex: Int, val step: FlowStep, val target: Selector, val message: String) : ExecutionFailure {
        override val summary: String
            get() = "step ${stepIndex + 1}: element $target not found"
    }

    @Serializable
    @SerialName("environment")
    data class Environment(val message: String) : ExecutionFailure {
        override val summary: String
            get() = "the device backend failed: $message"
    }
}

/** Everything one replay of a scenario produced. */
@Serializable
data class ExecutionResult(
    /** Null when every step ran; otherwise where and why the replay stopped. */
    val failure: ExecutionFailure?,
    val logPath: String,
    val checkpoints: List<CheckpointEvidence>,
    val durationMs: Long,
)

/** Whether a device can be driven right now. */
sealed interface Readiness {
    data object Ready : Readiness
    data class NotReady(val why: String) : Readiness
}

/** Told about a replay as it happens, for progress output. */
interface ExecutionListener {
    fun stepStarted(index: Int, step: FlowStep) {}
    fun checkpointCaptured(evidence: CheckpointEvidence) {}
}
