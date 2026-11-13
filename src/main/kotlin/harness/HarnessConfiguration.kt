package harness

import ai.koog.prompt.executor.model.PromptExecutor
import harness.device.DeviceRegistry
import harness.device.FakeBehavior
import harness.device.FakeDeviceDriver
import harness.device.FakeScript
import harness.graph.NoLlmPromptExecutor
import harness.verify.SharedVerifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.toKotlinDuration

/** The beans that are not plain `@Component` classes: the fakes and the shared resource, sized by configuration. */
@Configuration
class HarnessConfiguration {

    /** Which built-in scenarios misbehave. Everything else succeeds on the first attempt. */
    @Bean
    fun fakeScript(): FakeScript = FakeScript(
        mapOf(
            "sync inbox" to FakeBehavior.FailsOnce,
            "upload photo" to FakeBehavior.FailsOnce,
            "print receipt" to FakeBehavior.AlwaysFails,
        ),
    )

    @Bean
    fun deviceRegistry(props: HarnessProperties, script: FakeScript): DeviceRegistry =
        DeviceRegistry((1..props.devices).map { FakeDeviceDriver("kiosk-$it", script, props.deviceLatency.toKotlinDuration()) })

    /** One instance for the whole application: this is the shared resource every device's graph run competes for. */
    @Bean
    fun sharedVerifier(props: HarnessProperties, events: EventSink): SharedVerifier =
        SharedVerifier(props.verifierPermits, props.verifierLatency.toKotlinDuration(), events)

    @Bean
    fun promptExecutor(): PromptExecutor = NoLlmPromptExecutor()
}
