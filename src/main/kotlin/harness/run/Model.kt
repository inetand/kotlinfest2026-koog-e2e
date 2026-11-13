package harness.run

import harness.device.ExecutionResult
import harness.spec.Criterion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.time.Duration

enum class Ruling { PASS, FAIL, INCONCLUSIVE }

/** The judgement of one criterion, with the evidence it rests on. */
@Serializable
data class CriterionResult(
    val criterionId: String,
    val ruling: Ruling,
    val reason: String = "",
    /** References such as `hierarchy:01_saved` or `screenshot:01_saved`. A PASS or FAIL must cite a captured checkpoint. */
    val evidence: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
)

/** The judgement of one attempt. */
@Serializable
data class AttemptVerdict(
    val overall: Ruling,
    val criteria: List<CriterionResult>,
    val note: String? = null,
    /** Set when the harness, not the app, prevented a judgement (evidence integrity, malformed judge answer). */
    val harnessFault: String? = null,
)

/** One attempt: where its evidence is, what the replay produced, and the verdict. */
@Serializable
data class AttemptRecord(
    val number: Int,
    val directory: String,
    val execution: ExecutionResult,
    val verdict: AttemptVerdict,
    val startedAt: String,
    val finishedAt: String,
)

/** The typed terminal outcome of a test. */
@Serializable
sealed interface RunOutcome {
    val status: String

    @Serializable
    @SerialName("PASS")
    data object Pass : RunOutcome {
        override val status: String get() = "PASS"
    }

    /** Every criterion passed, but not on the first attempt. */
    @Serializable
    @SerialName("PASS_WITH_WARNINGS")
    data class PassWithWarnings(val warnings: List<String>) : RunOutcome {
        override val status: String get() = "PASS_WITH_WARNINGS"
    }

    /** Evidence contradicts a criterion. */
    @Serializable
    @SerialName("FAIL")
    data class Fail(val cause: String) : RunOutcome {
        override val status: String get() = "FAIL"
    }

    /** No verdict could be reached: the replay kept failing, evidence was missing, or the harness failed. */
    @Serializable
    @SerialName("INCONCLUSIVE")
    data class Inconclusive(val why: String) : RunOutcome {
        override val status: String get() = "INCONCLUSIVE"
    }

    /** A human must look before this test can run again, e.g. the scenario is stale. */
    @Serializable
    @SerialName("REVIEW_REQUIRED")
    data class ReviewRequired(val reason: String, val detail: String? = null) : RunOutcome {
        override val status: String get() = "REVIEW_REQUIRED"
    }

    @Serializable
    @SerialName("NOT_RUN")
    data class NotRun(val why: String) : RunOutcome {
        override val status: String get() = "NOT_RUN"
    }
}

/** One line of why, for result lines and reports. */
fun RunOutcome.reason(): String? =
    when (this) {
        RunOutcome.Pass -> null
        is RunOutcome.PassWithWarnings -> warnings.joinToString("; ")
        is RunOutcome.Fail -> cause
        is RunOutcome.Inconclusive -> why
        is RunOutcome.ReviewRequired -> listOfNotNull(reason, detail).joinToString(": ")
        is RunOutcome.NotRun -> why
    }

/** The immutable bounds of one test: fixed by the resolve node, never changed by the attempt loop. */
data class RunBudget(val maxAttempts: Int, val attemptTimeout: Duration)

/** What follows a validated attempt. */
sealed interface NextAttempt {
    /** Replay the unchanged scenario once more from a clean state. */
    data class Retry(val reason: String) : NextAttempt
    data class Finish(val outcome: RunOutcome) : NextAttempt
}

/** Where one test of one run keeps its files. */
data class TestPaths(val root: Path, val runId: String, val testId: String) {
    val directory: Path get() = root.resolve("results").resolve(runId).resolve(testId)
    val recordFile: Path get() = directory.resolve("record.json")
    val reportFile: Path get() = directory.resolve("report.md")
    fun attemptDirectory(number: Int): Path = directory.resolve("attempt-%02d".format(number))
}

/** The immutable record of one test. Evidence stays on disk and is referenced by path and digest. */
@Serializable
data class RunRecord(
    val schemaVersion: Int = 1,
    val runId: String,
    val specId: String,
    val title: String,
    val goal: String,
    val criteria: List<Criterion>,
    val backend: String,
    val appId: String,
    val device: String,
    val startedAt: String,
    val finishedAt: String,
    /** `fresh`, `stale (<why>)`, `missing` or `unreadable`: how the scenario was resolved. */
    val scenario: String,
    /** Checkpoint name → criterion ids, so the report can say what each screen proves. */
    val checkpoints: Map<String, List<String>> = emptyMap(),
    val attempts: List<AttemptRecord>,
    val warnings: List<String>,
    val outcome: RunOutcome,
)
