package harness.scenario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Addresses one element by id or by label, with an index when that is not unique. A scenario never holds
 * coordinates, so it survives a layout change that keeps the element.
 */
@Serializable
data class Selector(val id: String? = null, val text: String? = null, val index: Int? = null) {
    init {
        require(id != null || text != null) { "a selector needs an id or a text" }
    }

    override fun toString(): String =
        (if (id != null) "id=$id" else "text=\"$text\"") + (index?.let { " #$it" } ?: "")
}

/**
 * The generic steps a scenario is made of. They know nothing about any app or any device tool: each backend turns
 * them into its own commands with a `when` over this sealed type, so a step added here that a backend forgets is a
 * compile error there.
 */
@Serializable
sealed interface FlowStep {

    /** Restarts the app so a replay always begins from the same state; the first step of every scenario. */
    @Serializable
    @SerialName("launch")
    data class Launch(val clearState: Boolean = false) : FlowStep

    @Serializable
    @SerialName("tap")
    data class Tap(val target: Selector) : FlowStep

    /** Types into whatever has focus. */
    @Serializable
    @SerialName("inputText")
    data class InputText(val text: String) : FlowStep

    @Serializable
    @SerialName("assertVisible")
    data class AssertVisible(val target: Selector) : FlowStep

    /** Moves the content in [direction]: UP reveals what is below. */
    @Serializable
    @SerialName("swipe")
    data class Swipe(val direction: String) : FlowStep

    /** Captures the evidence of a checkpoint: a screenshot and the screen's hierarchy. */
    @Serializable
    @SerialName("screenshot")
    data class Screenshot(val name: String) : FlowStep

    @Serializable
    @SerialName("back")
    data object Back : FlowStep

    @Serializable
    @SerialName("waitForAnimation")
    data object WaitForAnimation : FlowStep
}

/** The element a step acts on, when it acts on one. */
fun FlowStep.target(): Selector? =
    when (this) {
        is FlowStep.Tap -> target
        is FlowStep.AssertVisible -> target
        is FlowStep.Launch, is FlowStep.InputText, is FlowStep.Swipe, is FlowStep.Screenshot, FlowStep.Back, FlowStep.WaitForAnimation -> null
    }

/** One line for logs and reports. */
fun FlowStep.describe(): String =
    when (this) {
        is FlowStep.Launch -> "launch" + if (clearState) " (clear state)" else ""
        is FlowStep.Tap -> "tap $target"
        is FlowStep.InputText -> "input \"$text\""
        is FlowStep.AssertVisible -> "assert visible $target"
        is FlowStep.Swipe -> "swipe $direction"
        is FlowStep.Screenshot -> "screenshot $name"
        FlowStep.Back -> "back"
        FlowStep.WaitForAnimation -> "wait for animation"
    }
