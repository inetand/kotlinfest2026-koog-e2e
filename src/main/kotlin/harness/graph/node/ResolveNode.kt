package harness.graph.node

import harness.config.HarnessProperties
import harness.graph.AttemptOutcome
import harness.graph.AttemptState
import harness.graph.GraphNode
import harness.graph.Resolved
import harness.graph.RunContext
import harness.graph.RunRequest
import harness.run.RunBudget
import harness.run.RunOutcome
import harness.run.TestPaths
import harness.scenario.ScenarioCheck
import harness.scenario.ScenarioRepository
import harness.spec.SpecificationRepository
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Clock

/**
 * Decides whether a test may touch the device. It reads the specification, fixes the run's context and budget, and
 * checks the stored scenario: a fresh one starts the attempt loop; a missing, stale or unreadable one ends the test
 * here with a typed outcome, and no device time is spent on it.
 */
@Component
class ResolveNode(
    private val specs: SpecificationRepository,
    private val scenarios: ScenarioRepository,
    private val props: HarnessProperties,
    private val clock: Clock,
) : GraphNode<RunRequest, Resolved> {

    override val name = "resolve"

    override suspend fun run(input: RunRequest): Resolved {
        val spec = specs.find(input.testId)
        val check = scenarios.check(spec)
        val now = clock.instant()
        val context = RunContext(
            runId = input.runId,
            testId = input.testId,
            spec = spec,
            device = input.device,
            backend = props.backend,
            appId = props.appId,
            paths = TestPaths(Path.of(props.root), input.runId, input.testId),
            startedAt = now,
            budget = RunBudget(props.maxAttempts, props.attemptTimeout),
            scenarioStatus = check.status,
        )
        return when (check) {
            is ScenarioCheck.Fresh -> Resolved.Ready(
                AttemptState(
                    context = context,
                    scenario = check.scenario,
                    attemptNumber = 1,
                    retriesRemaining = context.budget.maxAttempts - 1,
                    deadline = now.plus(context.budget.attemptTimeout),
                ),
            )
            ScenarioCheck.Missing -> blocked(context, RunOutcome.NotRun("no scenario is stored for this specification"))
            is ScenarioCheck.Stale -> blocked(context, RunOutcome.ReviewRequired("stale_scenario", check.why))
            is ScenarioCheck.Unreadable -> blocked(context, RunOutcome.ReviewRequired("unreadable_scenario", check.why))
        }
    }

    private fun blocked(context: RunContext, outcome: RunOutcome): Resolved = Resolved.Blocked(AttemptOutcome(context, outcome))
}
