package harness.config

import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import harness.EventSink
import harness.device.DeviceDriver
import harness.device.DeviceRegistry
import harness.device.DeviceSlot
import harness.device.EvidenceFiles
import harness.device.fake.FakeApp
import harness.device.fake.FakeDeviceDriver
import harness.device.fake.FakeScript
import harness.device.maestro.MaestroDriver
import harness.device.maestro.MaestroStepWriter
import harness.device.maestro.ProcessRunner
import harness.graph.NoLlmPromptExecutor
import harness.judge.CriterionJudge
import harness.judge.LlmJudge
import harness.judge.ModelPermits
import harness.judge.RuleJudge
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path
import java.time.Clock
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.time.toKotlinDuration

/**
 * The beans that configuration chooses between: which device backend, which judge, which executor. Everything
 * else is a plain `@Component` class. The graph and its nodes depend only on the interfaces these return.
 */
@Configuration
class HarnessConfiguration {

    @Bean
    fun json(): Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun evidenceFiles(clock: Clock): EvidenceFiles = EvidenceFiles(Dispatchers.IO, clock)

    /** One instance for the whole application: this is the shared resource every device's graph run competes for. */
    @Bean
    fun modelPermits(props: HarnessProperties): ModelPermits = ModelPermits(props.modelPermits)

    /** The fake application's scripts, `fake-device/<specId>.json`; only the fake backend reads them. */
    @Bean
    fun fakeApp(props: HarnessProperties, json: Json): FakeApp {
        val directory = Path.of(props.root, "fake-device")
        val scripts = if (directory.isDirectory()) {
            directory.listDirectoryEntries("*.json").associate { it.nameWithoutExtension to json.decodeFromString<FakeScript>(it.readText()) }
        } else {
            emptyMap()
        }
        return FakeApp(scripts)
    }

    @Bean
    fun deviceRegistry(props: HarnessProperties, fakeApp: FakeApp, files: EvidenceFiles): DeviceRegistry {
        val process = ProcessRunner(Dispatchers.IO)
        val writer = MaestroStepWriter()
        val drivers = props.devices.map(::DeviceSlot).associateWith { slot ->
            when (props.backend) {
                Backend.FAKE -> FakeDeviceDriver(slot, fakeApp, files, props.fake.stepLatency.toKotlinDuration())
                Backend.MAESTRO -> MaestroDriver(props.appId, slot, props.maestro.bin, props.maestro.timeout.toKotlinDuration(), process, writer, files)
            } as DeviceDriver
        }
        return DeviceRegistry(drivers)
    }

    /**
     * The transport to the model. With the LLM judge it is Koog's own OpenAI client pointed at any OpenAI-compatible
     * server; with the rule judge nothing calls a model and the executor is a placeholder.
     */
    @Bean
    fun promptExecutor(props: HarnessProperties): PromptExecutor =
        when (props.judge) {
            JudgeKind.RULE -> NoLlmPromptExecutor()
            JudgeKind.LLM -> MultiLLMPromptExecutor(
                OpenAILLMClient(
                    apiKey = props.llm.apiKey,
                    settings = OpenAIClientSettings(
                        baseUrl = props.llm.baseUrl.trimEnd('/').removeSuffix("/v1"),
                        timeoutConfig = ConnectionTimeoutConfig(
                            requestTimeoutMillis = props.llm.timeout.toMillis(),
                            socketTimeoutMillis = props.llm.timeout.toMillis(),
                        ),
                    ),
                ),
            )
        }

    @Bean
    fun judgeModel(props: HarnessProperties): LLModel =
        LLModel(
            LLMProvider.OpenAI,
            props.llm.model,
            listOfNotNull(
                LLMCapability.Completion,
                LLMCapability.OpenAIEndpoint.Completions,
                LLMCapability.Schema.JSON.Standard.takeIf { props.llm.jsonSchema },
            ),
        )

    @Bean
    fun criterionJudge(props: HarnessProperties, permits: ModelPermits, executor: PromptExecutor, model: LLModel, events: EventSink): CriterionJudge =
        when (props.judge) {
            JudgeKind.RULE -> RuleJudge(permits, props.fake.judgeLatency.toKotlinDuration(), events)
            JudgeKind.LLM -> LlmJudge(executor, model, permits, props.llm.fixRetries, events)
        }
}
