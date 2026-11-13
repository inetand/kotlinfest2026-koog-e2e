package harness.scenario

import harness.config.HarnessProperties
import harness.spec.Specification
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/** The stored scenarios of the workspace, `flows/<specId>.json`, checked against the specification they prove. */
@Component
class ScenarioRepository(
    private val props: HarnessProperties,
    private val json: Json,
) {

    fun check(spec: Specification): ScenarioCheck {
        val file = Path.of(props.root, "flows", "${spec.id}.json")
        if (!file.exists()) {
            return ScenarioCheck.Missing
        }
        val scenario = try {
            json.decodeFromString<Scenario>(file.readText())
        } catch (e: SerializationException) {
            return ScenarioCheck.Unreadable(e.message?.lineSequence()?.first() ?: "not a scenario")
        } catch (e: IllegalArgumentException) {
            return ScenarioCheck.Unreadable(e.message ?: "not a scenario")
        }
        return when {
            scenario.specHash != spec.contentHash -> ScenarioCheck.Stale("the specification changed after the scenario was written")
            scenario.appId != props.appId -> ScenarioCheck.Stale("written for ${scenario.appId}, not ${props.appId}")
            scenario.steps.firstOrNull() !is FlowStep.Launch -> ScenarioCheck.Unreadable("a scenario must begin with a launch step")
            else -> ScenarioCheck.Fresh(scenario)
        }
    }
}
