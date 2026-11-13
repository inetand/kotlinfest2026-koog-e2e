package harness

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import harness.config.HarnessProperties
import harness.device.DeviceRegistry
import harness.device.DeviceSlot
import harness.device.EvidenceFiles
import harness.device.fake.FakeApp
import harness.device.fake.FakeBehavior
import harness.device.fake.FakeDeviceDriver
import harness.device.fake.FakeScript
import harness.graph.AgentFactory
import harness.graph.AttemptSubgraph
import harness.graph.NoLlmPromptExecutor
import harness.graph.Reported
import harness.graph.SpecificationRunner
import harness.graph.TestGraph
import harness.graph.node.ExecuteNode
import harness.graph.node.ReportNode
import harness.graph.node.ResolveNode
import harness.graph.node.RetryNode
import harness.graph.node.ValidateNode
import harness.judge.CriterionJudge
import harness.judge.ModelPermits
import harness.judge.RuleJudge
import harness.pool.TestPool
import harness.run.AttemptPolicy
import harness.run.AttemptValidator
import harness.run.OutcomeRules
import harness.run.ReportRenderer
import harness.run.RunRecordRepository
import harness.scenario.Checkpoint
import harness.scenario.FlowStep
import harness.scenario.Scenario
import harness.scenario.ScenarioRepository
import harness.scenario.Selector
import harness.spec.MarkdownSpecificationParser
import harness.spec.SpecificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.Collections
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.toKotlinDuration

/** Records events in arrival order. */
class RecordingEventSink : EventSink {
    private val recorded = Collections.synchronizedList(mutableListOf<Event>())

    val events: List<Event>
        get() = synchronized(recorded) { recorded.toList() }

    override fun emit(event: Event) {
        recorded += event
    }
}

val testJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

/** A workspace in a temporary directory, filled by the test that uses it. */
class TestWorkspace {
    val root: Path = Files.createTempDirectory("harness-test")
    private val parser = MarkdownSpecificationParser()

    /**
     * Writes the specification [id], whose one criterion expects [phrase] on screen, and a scenario that proves it
     * at the checkpoint `01_done`. [stale] stores the scenario with another specification's hash.
     */
    fun test(id: String, phrase: String = "Done", withScenario: Boolean = true, stale: Boolean = false) {
        val text = "# $id\n\n## Goal\nThe user does $id.\n\n## Criteria\n- done: The screen shows \"$phrase\".\n"
        root.resolve("specs").createDirectories().resolve("$id.md").writeText(text)
        if (withScenario) {
            val scenario = Scenario(
                specId = id,
                specHash = if (stale) "an-older-hash" else parser.parse(id, text, "").contentHash,
                appId = "com.example.notes",
                steps = listOf(FlowStep.Launch(clearState = true), FlowStep.Tap(Selector(id = "button")), FlowStep.Screenshot("01_done")),
                checkpoints = listOf(Checkpoint("01_done", listOf("done"))),
            )
            root.resolve("flows").createDirectories().resolve("$id.json").writeText(testJson.encodeToString(Scenario.serializer(), scenario))
        }
    }
}

/** A fake script whose checkpoint `01_done` shows [screen]. */
fun script(behavior: FakeBehavior = FakeBehavior.SUCCEEDS, screen: String = "Done") = FakeScript(behavior, mapOf("01_done" to screen))

/**
 * The production classes, wired by hand with plain constructors around fakes, on a test workspace. Because every
 * collaborator arrives through a constructor, a test builds the same object graph Spring builds, without Spring.
 */
class Wiring(
    val workspace: TestWorkspace = TestWorkspace(),
    scripts: Map<String, FakeScript> = emptyMap(),
    deviceNames: List<String> = listOf("kiosk-1"),
    maxAttempts: Int = 3,
    attemptTimeout: Duration = Duration.ofMinutes(1),
    modelPermits: Int = 2,
    stepLatency: Duration = Duration.ZERO,
    judgeLatency: Duration = Duration.ZERO,
    judge: ((ModelPermits, EventSink) -> CriterionJudge)? = null,
) {
    val events = RecordingEventSink()

    val props = HarnessProperties(root = workspace.root.toString(), devices = deviceNames, maxAttempts = maxAttempts, attemptTimeout = attemptTimeout, modelPermits = modelPermits)

    private val clock: Clock = Clock.systemUTC()

    val files = EvidenceFiles(Dispatchers.IO, clock)

    val drivers: Map<DeviceSlot, FakeDeviceDriver> =
        deviceNames.map(::DeviceSlot).associateWith { FakeDeviceDriver(it, FakeApp(scripts), files, stepLatency.toKotlinDuration()) }

    private val registry = DeviceRegistry(drivers)

    private val permits = ModelPermits(modelPermits)

    private val specs = SpecificationRepository(props, MarkdownSpecificationParser())

    private val records = RunRecordRepository(files, testJson, ReportRenderer())

    val validator = AttemptValidator(files, judge?.invoke(permits, events) ?: RuleJudge(permits, judgeLatency.toKotlinDuration(), events), OutcomeRules(), events)

    val graph = TestGraph(
        ResolveNode(specs, ScenarioRepository(props, testJson), props, clock),
        AttemptSubgraph(ExecuteNode(registry, records, events, clock), ValidateNode(validator, clock), RetryNode(AttemptPolicy(OutcomeRules()), events, clock)),
        ReportNode(records, events, clock),
    )

    val agents = AgentFactory(graph, NoLlmPromptExecutor(), LLModel(LLMProvider.OpenAI, "unused", listOf(LLMCapability.Completion)))

    val runner = SpecificationRunner(specs, agents, records, events, props, clock)

    val pool = TestPool(registry, runner)

    val device: FakeDeviceDriver
        get() = drivers.values.first()

    fun run(testId: String): Reported = runBlocking { runner.run("run-1", testId, drivers.keys.first()) }
}
