package harness.cli

import harness.config.HarnessProperties
import harness.config.JudgeKind
import harness.device.DeviceRegistry
import harness.device.Readiness
import harness.graph.TestGraph
import harness.scenario.ScenarioRepository
import harness.spec.SpecificationRepository
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import kotlin.io.path.isDirectory

@Component
@Command(name = "doctor", mixinStandardHelpOptions = true, description = ["Check that the environment, the workspace, the devices and the judge are usable. Exits 0 when every check passes."])
class DoctorCommand(
    private val specs: SpecificationRepository,
    private val scenarios: ScenarioRepository,
    private val devices: DeviceRegistry,
    private val graph: TestGraph,
    private val props: HarnessProperties,
) : Callable<Int> {

    override fun call(): Int {
        val checks = mutableListOf<Boolean>()
        checks += check("JDK 17 or newer (found ${Runtime.version().feature()})") { require(Runtime.version().feature() >= 17) }
        checks += check("workspace ${Path.of(props.root).toAbsolutePath().normalize()} has specs/") { require(Path.of(props.root, "specs").isDirectory()) }
        checks += check("every specification parses (${specs.ids().size} found)") { specs.ids().forEach { specs.find(it) } }
        checks += check("the test graph builds") { require(graph.strategy().name == TestGraph.NAME) }
        for (slot in devices.slots) {
            checks += check("device ${slot.name} is ready (${props.backend.name.lowercase()})") {
                val readiness = runBlocking { devices.driver(slot).observe() }
                require(readiness is Readiness.Ready) { (readiness as Readiness.NotReady).why }
            }
        }
        checks += when (props.judge) {
            JudgeKind.RULE -> check("judge: rule (no model is called)") {}
            JudgeKind.LLM -> check("judge: llm, ${props.llm.model} at ${props.llm.baseUrl} answers") { probeModelServer() }
        }
        val replayable = specs.ids().count { id -> runCatching { scenarios.check(specs.find(id)).status == "fresh" }.getOrDefault(false) }
        println("\n$replayable of ${specs.ids().size} specification(s) have a scenario that may be replayed (see `list`).")
        println(if (checks.all { it }) "All checks passed." else "Some checks failed.")
        return if (checks.all { it }) 0 else 1
    }

    private fun probeModelServer() {
        val request = HttpRequest.newBuilder(URI.create(props.llm.baseUrl.trimEnd('/').removeSuffix("/v1") + "/v1/models"))
            .timeout(Duration.ofSeconds(5))
            .apply { if (props.llm.apiKey.isNotBlank()) header("Authorization", "Bearer ${props.llm.apiKey}") }
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        require(response.body().contains(props.llm.model)) { "the server does not list model ${props.llm.model}" }
    }

    /** Runs [probe] and prints its line; a probe that throws is a failed check, not a crash. */
    private fun check(label: String, probe: () -> Unit): Boolean {
        val failure = try {
            probe()
            null
        } catch (e: Exception) {
            e.message ?: e::class.simpleName
        }
        println(if (failure == null) "  [ok]   $label" else "  [FAIL] $label: $failure")
        return failure == null
    }
}
