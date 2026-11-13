package harness.device

/** The devices of a run, by name. Graph inputs carry a device name, not a driver, so they stay plain data. */
class DeviceRegistry(drivers: List<DeviceDriver>) {

    private val byName: Map<String, DeviceDriver> = drivers.associateBy { it.name }

    init {
        require(byName.size == drivers.size) { "device names must be unique" }
    }

    val names: List<String> = drivers.map { it.name }

    fun driver(name: String): DeviceDriver = byName[name] ?: error("no device named $name")
}
