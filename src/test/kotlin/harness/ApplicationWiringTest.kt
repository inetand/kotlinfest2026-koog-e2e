package harness

import harness.device.DeviceRegistry
import harness.pool.TestPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Spring's part: the context starts, every constructor is satisfied, and configuration sizes the fakes. */
@SpringBootTest(properties = ["harness.devices=2", "harness.device-latency=0ms", "harness.verifier-latency=0ms"])
class ApplicationWiringTest {

    @Autowired
    lateinit var pool: TestPool

    @Autowired
    lateinit var devices: DeviceRegistry

    @Test
    fun `the context wires the pool and builds as many fake devices as configured`() {
        assertNotNull(pool)
        assertEquals(listOf("kiosk-1", "kiosk-2"), devices.names)
    }
}
