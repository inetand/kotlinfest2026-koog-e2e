package harness

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Typed configuration, bound from `application.yml` and `HARNESS_*` environment variables. */
@ConfigurationProperties("harness")
data class HarnessProperties(
    /** Fake devices; each one gets exactly one worker. */
    val devices: Int,
    /** How many shared verifications may be in flight at once, across every device. */
    val verifierPermits: Int,
    /** Attempts per test, the first one included. */
    val maxAttempts: Int,
    val deviceLatency: Duration,
    val verifierLatency: Duration,
) {
    init {
        require(devices >= 1) { "harness.devices must be at least 1, was $devices" }
        require(verifierPermits >= 1) { "harness.verifier-permits must be at least 1, was $verifierPermits" }
        require(maxAttempts >= 1) { "harness.max-attempts must be at least 1, was $maxAttempts" }
    }
}
