package harness

import harness.config.Backend
import harness.config.HarnessProperties
import harness.device.DeviceRegistry
import harness.judge.CriterionJudge
import harness.judge.RuleJudge
import harness.pool.TestPool
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Spring's part: the context starts, every constructor is satisfied, and configuration chooses the adapters. */
@SpringBootTest(properties = ["harness.devices=a,b", "harness.root=build/no-workspace"])
class ApplicationWiringTest {

    @Autowired
    lateinit var pool: TestPool

    @Autowired
    lateinit var devices: DeviceRegistry

    @Autowired
    lateinit var judge: CriterionJudge

    @Autowired
    lateinit var props: HarnessProperties

    @Test
    fun `the context wires the pool, builds one fake device per configured name and picks the rule judge`() {
        assertNotNull(pool)
        assertEquals(listOf("a", "b"), devices.slots.map { it.name })
        assertEquals(Backend.FAKE, devices.driver(devices.slots.first()).backend)
        assertIs<RuleJudge>(judge)
        assertEquals(3, props.maxAttempts)
    }
}
