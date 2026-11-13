package harness.cli

import harness.HarnessProperties
import harness.ScenarioCatalog
import harness.device.DeviceRegistry
import harness.graph.Outcome
import harness.graph.TestResult
import harness.pool.TestPool
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Spec
import java.util.concurrent.Callable

@Component
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = ["Run scenarios across the fake devices. Exits 0 when every test passed, 1 otherwise."],
)
class RunCommand(
    private val pool: TestPool,
    private val catalog: ScenarioCatalog,
    private val devices: DeviceRegistry,
    private val props: HarnessProperties,
) : Callable<Int> {

    @Spec
    lateinit var spec: CommandSpec

    @Option(names = ["-s", "--scenario"], paramLabel = "ID", description = ["Scenario to run; repeat for several. Default: all (see `list`)."])
    var scenarioIds: List<String> = emptyList()

    @Option(names = ["--max-attempts"], paramLabel = "N", description = ["Attempts per test, the first included. Default: harness.max-attempts."])
    var maxAttempts: Int? = null

    override fun call(): Int {
        val scenarios = try {
            catalog.select(scenarioIds)
        } catch (e: IllegalArgumentException) {
            throw ParameterException(spec.commandLine(), e.message)
        }
        val attempts = maxAttempts ?: props.maxAttempts
        if (attempts < 1) {
            throw ParameterException(spec.commandLine(), "--max-attempts must be at least 1")
        }

        println("run: ${scenarios.size} test(s), ${devices.names.size} device(s), ${props.verifierPermits} verifier permit(s), up to $attempts attempt(s)\n")
        // runBlocking is the bridge from the blocking CLI into coroutines; every worker is a child of this scope.
        val results = runBlocking { pool.run(scenarios, attempts).toList() }
        summarize(results)
        return if (results.all { it.outcome is Outcome.Passed }) 0 else 1
    }

    private fun summarize(results: List<TestResult>) {
        println("\n%-16s %-9s %-10s %s".format("SCENARIO", "DEVICE", "OUTCOME", "ATTEMPTS"))
        results.sortedBy { it.scenarioId }.forEach {
            println("%-16s %-9s %-10s %d".format(it.scenarioId, it.device, it.outcome.label, it.attempts))
        }
    }
}
