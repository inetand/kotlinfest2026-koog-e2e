package harness.graph.node

import harness.Event
import harness.EventSink
import harness.graph.AttemptOutcome
import harness.graph.GraphNode
import harness.graph.Reported
import harness.run.RunRecord
import harness.run.RunRecordRepository
import org.springframework.stereotype.Component
import java.time.Clock

/** Writes the record and the report of a test, whether it was replayed or blocked before the device. */
@Component
class ReportNode(
    private val records: RunRecordRepository,
    private val events: EventSink,
    private val clock: Clock,
) : GraphNode<AttemptOutcome, Reported> {

    override val name = "report"

    override suspend fun run(input: AttemptOutcome): Reported {
        val context = input.context
        val record = RunRecord(
            runId = context.runId,
            specId = context.testId,
            title = context.spec.title,
            goal = context.spec.goal,
            criteria = context.spec.criteria,
            backend = context.backend.name.lowercase(),
            appId = context.appId,
            device = context.device.name,
            startedAt = context.startedAt.toString(),
            finishedAt = clock.instant().toString(),
            scenario = context.scenarioStatus,
            checkpoints = input.scenario?.checkpoints?.associate { it.name to it.criterionIds } ?: emptyMap(),
            attempts = input.attempts,
            warnings = input.warnings,
            outcome = input.outcome,
        )
        records.write(record, context.paths)
        events.emit(Event.Finished(context.device.name, context.testId, record.outcome.status, record.attempts.size))
        return Reported(record, context.paths.recordFile.toString(), context.paths.reportFile.toString())
    }
}
