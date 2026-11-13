package harness.cli

import harness.ScenarioCatalog
import harness.device.DeviceRegistry
import harness.device.FakeBehavior
import harness.device.FakeScript
import org.springframework.stereotype.Component
import picocli.CommandLine.Command

@Component
@Command(name = "list", mixinStandardHelpOptions = true, description = ["List the scenarios and the fake devices."])
class ListCommand(
    private val catalog: ScenarioCatalog,
    private val script: FakeScript,
    private val devices: DeviceRegistry,
) : Runnable {

    override fun run() {
        println("%-16s %-16s %s".format("SCENARIO", "ACTION", "FAKE DEVICE BEHAVIOR"))
        catalog.all.forEach {
            val behavior = when (script.behaviorOf(it.action)) {
                FakeBehavior.Succeeds -> "succeeds"
                FakeBehavior.FailsOnce -> "fails once, then succeeds"
                FakeBehavior.AlwaysFails -> "always fails"
            }
            println("%-16s %-16s %s".format(it.id, it.action, behavior))
        }
        println("\nDEVICES: ${devices.names.joinToString()}")
    }
}
