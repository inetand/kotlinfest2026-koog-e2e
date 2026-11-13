package harness.scenario

import kotlinx.serialization.Serializable

/** A screenshot step's name and the criteria its evidence is meant to prove. */
@Serializable
data class Checkpoint(val name: String, val criterionIds: List<String>)

/**
 * A stored scenario: exactly how a specification is proven, as generic steps a backend replays without any model,
 * and the checkpoints that give evidence for each criterion. It is a plain JSON file, so it is reviewed and
 * versioned like code. [specHash] ties it to the specification text it was written for.
 */
@Serializable
data class Scenario(
    val specId: String,
    val specHash: String,
    val appId: String,
    val steps: List<FlowStep>,
    val checkpoints: List<Checkpoint>,
) {
    fun criteriaOf(checkpoint: String): List<String> =
        checkpoints.firstOrNull { it.name == checkpoint }?.criterionIds ?: emptyList()
}

/** Whether the stored scenario of a specification may be replayed. */
sealed interface ScenarioCheck {
    data class Fresh(val scenario: Scenario) : ScenarioCheck
    data class Stale(val why: String) : ScenarioCheck
    data object Missing : ScenarioCheck
    data class Unreadable(val why: String) : ScenarioCheck

    val status: String
        get() = when (this) {
            is Fresh -> "fresh"
            is Stale -> "stale ($why)"
            Missing -> "missing"
            is Unreadable -> "unreadable"
        }
}
