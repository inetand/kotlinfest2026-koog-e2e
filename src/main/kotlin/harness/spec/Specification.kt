package harness.spec

import kotlinx.serialization.Serializable

/** One acceptance criterion: an id the scenario's checkpoints refer to, and the sentence a judge rules on. */
@Serializable
data class Criterion(val id: String, val text: String)

/** A test as a human wrote it: intent and acceptance criteria, never executable steps. */
data class Specification(
    val id: String,
    val title: String,
    val goal: String,
    val criteria: List<Criterion>,
    /** Digest of the source text. A stored scenario remembers it, so a changed specification makes the scenario stale. */
    val contentHash: String,
    val sourcePath: String,
)
