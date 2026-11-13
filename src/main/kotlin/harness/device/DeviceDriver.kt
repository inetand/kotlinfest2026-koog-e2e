package harness.device

import harness.config.Backend
import harness.scenario.Scenario
import java.nio.file.Path

/**
 * The device backend boundary, and the only thing the graph knows about a device. [harness.device.maestro.MaestroDriver]
 * implements it for real devices and [harness.device.fake.FakeDeviceDriver] in memory; the graph cannot tell which.
 */
interface DeviceDriver {
    val backend: Backend

    suspend fun observe(): Readiness

    /**
     * Replays a whole scenario from its first step (which restores the app's initial state), capturing evidence at
     * every screenshot step into [attemptDir]. A step that does not succeed ends the replay and is reported in the
     * result, never thrown: what the device did is data for the attempt loop.
     */
    suspend fun execute(scenario: Scenario, attemptDir: Path, listener: ExecutionListener): ExecutionResult
}

/** The devices of a run. Graph inputs carry a [DeviceSlot], not a driver, so they stay plain data. */
class DeviceRegistry(private val drivers: Map<DeviceSlot, DeviceDriver>) {

    val slots: List<DeviceSlot> = drivers.keys.toList()

    fun driver(slot: DeviceSlot): DeviceDriver = drivers[slot] ?: error("no device named ${slot.name}")
}
