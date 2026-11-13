package harness.device

/** What a device shows after an action. */
data class Screen(val text: String)

/**
 * The only thing the graph knows about a device. A real harness would implement this with a device automation
 * backend; this sample has one implementation, [FakeDeviceDriver].
 */
interface DeviceDriver {
    val name: String

    suspend fun isReady(): Boolean

    /** Performs [action] and returns the screen that follows. Suspends for as long as the device takes. */
    suspend fun perform(action: String): Screen
}
