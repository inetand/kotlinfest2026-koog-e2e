package harness.cli

import harness.config.HarnessProperties
import harness.platform.UsageError
import harness.scenario.ScenarioRepository
import harness.spec.SpecificationRepository
import org.springframework.stereotype.Component
import picocli.CommandLine.Command

@Component
@Command(name = "list", mixinStandardHelpOptions = true, description = ["List the specifications, their content hash, and whether each has a scenario that may be replayed."])
class ListCommand(
    private val specs: SpecificationRepository,
    private val scenarios: ScenarioRepository,
    private val props: HarnessProperties,
) : Runnable {

    override fun run() {
        println("%-16s %-18s %-12s %s".format("TEST", "SPEC HASH", "SCENARIO", "TITLE"))
        for (id in specs.ids()) {
            try {
                val spec = specs.find(id)
                println("%-16s %-18s %-12s %s".format(id, spec.contentHash, scenarios.check(spec).status.substringBefore(' '), spec.title))
            } catch (e: UsageError) {
                println("%-16s %-18s %-12s %s".format(id, "-", "invalid", e.message))
            }
        }
        println("\nDEVICES (${props.backend.name.lowercase()}): ${props.devices.joinToString()}")
    }
}
