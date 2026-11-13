package harness.device.maestro

import harness.scenario.FlowStep
import harness.scenario.Selector
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

private val REGEX_META = Regex("""[.*+?^${'$'}{}()|\[\]\\]""")

/**
 * Renders generic steps as a Maestro flow: a header document, `---`, then a list of commands. This is a fixed
 * translation, so no model is involved. The `when` has no `else`: a step type added to [FlowStep] and forgotten
 * here does not compile.
 */
class MaestroStepWriter {

    private val yaml = Yaml(
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
        },
    )

    fun write(appId: String, steps: List<FlowStep>): String =
        yaml.dump(mapOf("appId" to appId)).trimEnd() + "\n---\n" + yaml.dump(steps.map { command(it) })

    fun command(step: FlowStep): Any =
        when (step) {
            is FlowStep.Launch -> mapOf("launchApp" to mapOf("clearState" to step.clearState))
            is FlowStep.Tap -> mapOf("tapOn" to selector(step.target))
            is FlowStep.InputText -> mapOf("inputText" to step.text)
            is FlowStep.AssertVisible -> mapOf("assertVisible" to selector(step.target))
            is FlowStep.Swipe -> mapOf("swipe" to mapOf("direction" to step.direction))
            is FlowStep.Screenshot -> mapOf("takeScreenshot" to step.name)
            FlowStep.Back -> "back"
            FlowStep.WaitForAnimation -> "waitForAnimationToEnd"
        }

    /** Maestro treats `id` and `text` as regular expressions; labels are escaped so they match literally. */
    private fun selector(selector: Selector): Map<String, Any> {
        val map = linkedMapOf<String, Any>()
        selector.id?.let { map["id"] = it }
        selector.text?.let { map["text"] = it.replace(REGEX_META) { meta -> "\\" + meta.value } }
        selector.index?.let { map["index"] = it }
        return map
    }
}
