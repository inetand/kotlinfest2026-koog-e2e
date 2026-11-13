package harness.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

enum class Backend { FAKE, MAESTRO }

enum class JudgeKind { RULE, LLM }

/** Typed configuration, bound from `application.yml` and `HARNESS_*` environment variables. */
@ConfigurationProperties("harness")
data class HarnessProperties(
    /** The workspace: `specs/`, `flows/`, `fake-device/` are read from it and `results/` is written into it. */
    val root: String = "workspace",
    /** The application under test, as the device backend names it. */
    val appId: String = "com.example.notes",
    val backend: Backend = Backend.FAKE,
    /** One worker per entry: any names for the fake backend, device serials for Maestro. */
    val devices: List<String> = listOf("kiosk-1", "kiosk-2", "kiosk-3"),
    /** Attempts per test, the first one included. */
    val maxAttempts: Int = 3,
    /** How long all attempts of one test may take together. */
    val attemptTimeout: Duration = Duration.ofMinutes(5),
    /** How many judge calls may be in flight at once, across every device. */
    val modelPermits: Int = 2,
    val judge: JudgeKind = JudgeKind.RULE,
    val llm: Llm = Llm(),
    val fake: Fake = Fake(),
    val maestro: Maestro = Maestro(),
) {
    init {
        require(devices.isNotEmpty() && devices.toSet().size == devices.size) { "harness.devices must name at least one device, each once" }
        require(maxAttempts >= 1) { "harness.max-attempts must be at least 1, was $maxAttempts" }
        require(modelPermits >= 1) { "harness.model-permits must be at least 1, was $modelPermits" }
    }

    /** An OpenAI-compatible server (Ollama, LM Studio, llama.cpp, vLLM, or OpenAI itself). Used only when `judge` is LLM. */
    data class Llm(
        /** The server's root, without `/v1`. */
        val baseUrl: String = "http://localhost:11434",
        val apiKey: String = "",
        val model: String = "gemma3",
        /** Whether the server honours `response_format: json_schema`; false puts the schema into the prompt instead. */
        val jsonSchema: Boolean = true,
        /** How often a malformed structured answer is sent back to be fixed before it becomes a harness error. */
        val fixRetries: Int = 2,
        val timeout: Duration = Duration.ofMinutes(5),
    )

    /** How slow the fakes are, so overlap is visible on the console. */
    data class Fake(
        val stepLatency: Duration = Duration.ofMillis(120),
        val judgeLatency: Duration = Duration.ofMillis(600),
    )

    data class Maestro(
        val bin: String = "maestro",
        /** How long one `maestro` process may run. */
        val timeout: Duration = Duration.ofMinutes(3),
    )
}
