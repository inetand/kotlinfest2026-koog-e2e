package harness

import harness.judge.CriterionJudge
import harness.judge.JudgeInput
import harness.judge.RuleJudge
import harness.platform.HarnessError
import harness.pool.EXIT_USAGE
import harness.run.CriterionResult
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Concurrency: what overlaps, what is limited, what one failure reaches, and what happens when the caller goes away. */
class TestPoolTest {

    private val ids = listOf("one", "two", "three", "four", "five", "six")

    private fun wiring(permits: Int, judge: ((harness.judge.ModelPermits, EventSink) -> CriterionJudge)? = null) =
        Wiring(
            scripts = ids.associateWith { script() },
            deviceNames = listOf("kiosk-1", "kiosk-2", "kiosk-3"),
            modelPermits = permits,
            stepLatency = Duration.ofMillis(10),
            judgeLatency = Duration.ofMillis(60),
            judge = judge,
        ).also { wiring -> ids.forEach { wiring.workspace.test(it) } }

    @Test
    fun `every test runs exactly once and its result line is collected`() {
        val wiring = wiring(permits = 2)

        val lines = runBlocking { wiring.pool.results("run-1", ids + "one").toList() }

        assertEquals(ids.sorted(), lines.map { it.testId }.sorted(), "a test named twice runs once")
        assertTrue(lines.all { it.outcome == "PASS" })
    }

    @Test
    fun `devices replay at the same time while judge calls stay within the permits`() {
        val wiring = wiring(permits = 2)

        runBlocking { wiring.pool.results("run-1", ids).toList() }

        assertEquals(3, peak(wiring.events.events, { it is Event.AttemptStarted }, { it is Event.ReplayEnded }), "all three devices replay at once")
        assertEquals(2, peak(wiring.events.events, { it is Event.JudgeStarted }, { it is Event.JudgeFinished }), "never more than two judge calls in flight")
    }

    @Test
    fun `one permit serializes the judge but not the devices`() {
        val wiring = wiring(permits = 1)

        runBlocking { wiring.pool.results("run-1", ids).toList() }

        assertEquals(3, peak(wiring.events.events, { it is Event.AttemptStarted }, { it is Event.ReplayEnded }))
        assertEquals(1, peak(wiring.events.events, { it is Event.JudgeStarted }, { it is Event.JudgeFinished }))
    }

    @Test
    fun `an unknown test is reported as invalid and the others still run`() {
        val wiring = wiring(permits = 2)

        val lines = runBlocking { wiring.pool.results("run-1", listOf("one", "no-such-test", "two")).toList() }

        assertEquals(EXIT_USAGE, lines.single { it.testId == "no-such-test" }.exitCode)
        assertEquals(listOf("PASS", "PASS"), lines.filter { it.testId != "no-such-test" }.map { it.outcome })
    }

    @Test
    fun `a harness fault in one test is recorded for that test and does not reach the others`() {
        val wiring = wiring(permits = 2) { permits, events ->
            val rules = RuleJudge(permits, 0.milliseconds, events)
            object : CriterionJudge {
                override suspend fun judge(input: JudgeInput): List<CriterionResult> =
                    if (input.spec.id == "three") throw HarnessError("the model answer could not be parsed") else rules.judge(input)
            }
        }

        val lines = runBlocking { wiring.pool.results("run-1", ids).toList() }

        val broken = lines.single { it.testId == "three" }
        assertEquals("INCONCLUSIVE", broken.outcome)
        assertContains(broken.reason!!, "harness_error")
        assertEquals(5, lines.count { it.outcome == "PASS" })
    }

    @Test
    fun `cancelling the collector stops every worker`() {
        val wiring = Wiring(
            scripts = ids.associateWith { script(harness.device.fake.FakeBehavior.ALWAYS_FAILS) },
            deviceNames = listOf("kiosk-1", "kiosk-2", "kiosk-3"),
            maxAttempts = 1_000,
            stepLatency = Duration.ofMillis(10),
        ).also { wiring -> ids.forEach { wiring.workspace.test(it) } }

        val (atCancel, later) = runBlocking {
            val job = launch { wiring.pool.results("run-1", ids).toList() }
            delay(300.milliseconds)
            job.cancelAndJoin()
            val atCancel = wiring.events.events.size
            delay(300.milliseconds)
            atCancel to wiring.events.events.size
        }

        assertTrue(atCancel > 0, "the workers were running")
        assertEquals(atCancel, later, "no worker outlives its collector")
    }

    /** The most intervals open at once, where [start] opens one and [end] closes one. */
    private fun peak(events: List<Event>, start: (Event) -> Boolean, end: (Event) -> Boolean): Int {
        var open = 0
        var peak = 0
        for (event in events) {
            if (start(event)) open++
            if (end(event)) open--
            peak = maxOf(peak, open)
        }
        return peak
    }
}
