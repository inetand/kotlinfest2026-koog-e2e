package harness

import harness.device.Artifact
import harness.device.CheckpointEvidence
import harness.device.ExecutionFailure
import harness.device.ExecutionResult
import harness.judge.CriterionJudge
import harness.judge.JudgeInput
import harness.run.AttemptVerdict
import harness.run.CriterionResult
import harness.run.Ruling
import harness.scenario.Checkpoint
import harness.scenario.FlowStep
import harness.scenario.Scenario
import harness.spec.Criterion
import harness.spec.Specification
import kotlinx.coroutines.runBlocking
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The boundary between what a judge says and what counts. The judge here is a stub that answers whatever the test
 * tells it to, as a model might; the validator decides what of that is accepted.
 */
class AttemptValidatorTest {

    private val spec = Specification("save", "Save", "goal", listOf(Criterion("done", "The screen shows \"Done\"."), Criterion("title", "The title is kept.")), "hash", "save.md")
    private val scenario = Scenario("save", "hash", "com.example.notes", listOf(FlowStep.Launch()), listOf(Checkpoint("01_done", listOf("done", "title"))))

    private fun validate(vararg answer: CriterionResult, tamper: Boolean = false, captured: Boolean = true): AttemptVerdict {
        var asked = false
        val wiring = Wiring(judge = { _, _ ->
            object : CriterionJudge {
                override suspend fun judge(input: JudgeInput): List<CriterionResult> {
                    asked = true
                    return answer.toList()
                }
            }
        })
        return runBlocking {
            val artifact = wiring.files.store(wiring.workspace.root.resolve("01_done.txt"), "Done")
            if (tamper) {
                wiring.workspace.root.resolve("01_done.txt").writeText("edited after capture")
            }
            val execution = if (captured) {
                ExecutionResult(null, "device.log", listOf(CheckpointEvidence("01_done", null, artifact)), 10)
            } else {
                ExecutionResult(ExecutionFailure.Environment("no device"), "device.log", emptyList(), 10)
            }
            wiring.validator.validate(spec, scenario, execution, "kiosk-1").also { if (!captured || tamper) assertEquals(false, asked, "the judge is not asked without usable evidence") }
        }
    }

    private fun pass(id: String, vararg evidence: String) = CriterionResult(id, Ruling.PASS, "looks right", evidence.toList())

    @Test
    fun `rulings that cite a captured checkpoint are accepted`() {
        val verdict = validate(pass("done", "hierarchy:01_done"), pass("title", "hierarchy:01_done"))

        assertEquals(Ruling.PASS, verdict.overall)
    }

    @Test
    fun `a pass that cites nothing is not accepted`() {
        val verdict = validate(pass("done", "hierarchy:01_done"), pass("title"))

        assertEquals(Ruling.INCONCLUSIVE, verdict.overall)
        assertContains(verdict.criteria.single { it.criterionId == "title" }.missing, "PASS claimed without citing a captured checkpoint")
    }

    @Test
    fun `a pass that cites a checkpoint that was never captured is not accepted`() {
        val verdict = validate(pass("done", "hierarchy:99_imagined"), pass("title", "hierarchy:01_done"))

        assertEquals(Ruling.INCONCLUSIVE, verdict.criteria.single { it.criterionId == "done" }.ruling)
    }

    @Test
    fun `a criterion the judge skipped is not verified`() {
        val verdict = validate(pass("done", "hierarchy:01_done"))

        assertEquals(Ruling.INCONCLUSIVE, verdict.overall)
        assertNull(verdict.harnessFault)
    }

    @Test
    fun `an answer about a criterion the specification does not have is a harness fault, not a verdict`() {
        val verdict = validate(pass("done", "hierarchy:01_done"), pass("title", "hierarchy:01_done"), pass("invented", "hierarchy:01_done"))

        assertEquals(Ruling.INCONCLUSIVE, verdict.overall)
        assertEquals("malformed_judgement", verdict.harnessFault)
    }

    @Test
    fun `evidence changed after capture is a harness fault and the judge is not asked`() {
        val verdict = validate(tamper = true)

        assertEquals("evidence_integrity", verdict.harnessFault)
    }

    @Test
    fun `a replay that captured nothing is inconclusive and the judge is not asked`() {
        val verdict = validate(captured = false)

        assertEquals(Ruling.INCONCLUSIVE, verdict.overall)
        assertContains(verdict.note!!, "no checkpoint evidence was captured")
    }
}
