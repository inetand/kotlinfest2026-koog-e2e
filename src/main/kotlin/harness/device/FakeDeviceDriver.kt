package harness.device

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/** How a fake device answers one action. Each behavior is deterministic, so every run and every test is repeatable. */
sealed interface FakeBehavior {
    data object Succeeds : FakeBehavior
    data object FailsOnce : FakeBehavior
    data object AlwaysFails : FakeBehavior
}

/** Which behavior each action gets; an action that is not listed succeeds. */
class FakeScript(private val behaviors: Map<String, FakeBehavior> = emptyMap()) {
    fun behaviorOf(action: String): FakeBehavior = behaviors[action] ?: FakeBehavior.Succeeds
}

/**
 * A device that exists only in memory. It waits [latency] with `delay`, so it never blocks a thread, and answers by
 * the [script]. The call counts are the device's own state, as a real device has its own; nothing else shares them.
 */
class FakeDeviceDriver(
    override val name: String,
    private val script: FakeScript = FakeScript(),
    private val latency: Duration = Duration.ZERO,
) : DeviceDriver {

    private val calls = ConcurrentHashMap<String, AtomicInteger>()

    override suspend fun isReady(): Boolean = true

    override suspend fun perform(action: String): Screen {
        delay(latency)
        val call = calls.computeIfAbsent(action) { AtomicInteger() }.incrementAndGet()
        val succeeded = when (script.behaviorOf(action)) {
            FakeBehavior.Succeeds -> true
            FakeBehavior.FailsOnce -> call > 1
            FakeBehavior.AlwaysFails -> false
        }
        return Screen(if (succeeded) "done: $action" else "busy")
    }

    /** How many times [action] was performed on this device. */
    fun callsOf(action: String): Int = calls[action]?.get() ?: 0
}
