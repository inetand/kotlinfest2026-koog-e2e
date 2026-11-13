package harness.cli

import harness.config.HarnessProperties
import harness.pool.ResultLine
import harness.pool.TestPool
import harness.pool.worstExitCode
import harness.spec.SpecificationRepository
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable

private val RUN_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

@Component
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = [
        "Run tests across the devices. Progress goes to stderr; stdout carries one JSON line per test.",
        "Exit code: 0 all passed, 1 a test failed, 2 inconclusive, 3 review required, 4 not run, 64 unknown test.",
    ],
)
class RunCommand(
    private val pool: TestPool,
    private val specs: SpecificationRepository,
    private val props: HarnessProperties,
    private val clock: Clock,
    json: Json,
) : Callable<Int> {

    private val compactJson = Json(json) { prettyPrint = false }

    @Parameters(paramLabel = "TEST", description = ["Specification ids to run. Default: every specification in the workspace (see `list`)."])
    var testIds: List<String> = emptyList()

    override fun call(): Int {
        val ids = testIds.ifEmpty { specs.ids() }
        val runId = RUN_ID.format(clock.instant())
        System.err.println("run $runId: ${ids.size} test(s), ${props.devices.size} device(s) on ${props.backend.name.lowercase()}, judge ${props.judge.name.lowercase()} with ${props.modelPermits} permit(s)\n")
        // runBlocking is the bridge from the blocking CLI into coroutines; every worker is a child of this scope.
        val results = runBlocking {
            pool.results(runId, ids).onEach { println(compactJson.encodeToString(ResultLine.serializer(), it)) }.toList()
        }
        summarize(results)
        return worstExitCode(results.map { it.exitCode })
    }

    private fun summarize(results: List<ResultLine>) {
        System.err.println("\n%-16s %-10s %-20s %s".format("TEST", "DEVICE", "OUTCOME", "ATTEMPTS"))
        results.sortedBy { it.testId }.forEach {
            System.err.println("%-16s %-10s %-20s %d".format(it.testId, it.device ?: "-", it.outcome, it.attempts))
        }
        System.err.println("\nRecords and reports: ${props.root}/results/${results.firstOrNull()?.record?.substringAfter("/results/")?.substringBefore('/') ?: ""}")
    }
}
