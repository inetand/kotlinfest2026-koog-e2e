package harness.cli

import harness.HarnessProperties
import harness.Scenario
import harness.device.DeviceRegistry
import harness.graph.Outcome
import harness.graph.TestGraph
import harness.graph.TestRunner
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Component
@Command(name = "doctor", mixinStandardHelpOptions = true, description = ["Check that the environment and the wiring are sound. Exits 0 when every check passes."])
class DoctorCommand(
    private val devices: DeviceRegistry,
    private val graph: TestGraph,
    private val runner: TestRunner,
    private val props: HarnessProperties,
) : Callable<Int> {

    override fun call(): Int {
        val checks = listOf(
            check("JDK 17 or newer (found ${Runtime.version().feature()})") { Runtime.version().feature() >= 17 },
            check("configuration: ${props.devices} device(s), ${props.verifierPermits} verifier permit(s), ${props.maxAttempts} attempt(s)") { true },
            check("the test graph builds") { graph.strategy().name == TestGraph.NAME },
        ) + devices.names.map { name ->
            check("device $name answers") { runBlocking { devices.driver(name).isReady() } }
        } + check("a probe test passes end to end on ${devices.names.first()}") {
            val probe = Scenario("doctor-probe", "ping", "done: ping")
            runBlocking { runner.run(probe, devices.names.first(), maxAttempts = 1) }.outcome is Outcome.Passed
        }
        println(if (checks.all { it }) "\nAll checks passed." else "\nSome checks failed.")
        return if (checks.all { it }) 0 else 1
    }

    /** Runs [probe] and prints its line; a probe that throws is a failed check, not a crash. */
    private fun check(label: String, probe: () -> Boolean): Boolean {
        val failure = try {
            if (probe()) null else "check returned false"
        } catch (e: Exception) {
            e.message ?: e::class.simpleName
        }
        println(if (failure == null) "  [ok]   $label" else "  [FAIL] $label: $failure")
        return failure == null
    }
}
