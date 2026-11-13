package harness.graph

import harness.Event
import harness.EventSink
import harness.config.HarnessProperties
import harness.device.DeviceSlot
import harness.platform.runCatchingCancellable
import harness.run.RunOutcome
import harness.run.RunRecord
import harness.run.RunRecordRepository
import harness.run.TestPaths
import harness.spec.Specification
import harness.spec.SpecificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs one specification through the test graph on one device, as one independent graph execution. The
 * specification is read first, so an unreadable one is a usage error and never a run. A fault inside the graph
 * still ends with a written record, as INCONCLUSIVE, and does not reach the other tests of the run. Cancellation is
 * the one thing passed on untouched.
 */
@Component
class SpecificationRunner(
    private val specs: SpecificationRepository,
    private val agents: AgentFactory,
    private val records: RunRecordRepository,
    private val events: EventSink,
    private val props: HarnessProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(SpecificationRunner::class.java)

    suspend fun run(runId: String, testId: String, device: DeviceSlot): Reported {
        val spec = specs.find(testId)
        val startedAt = clock.instant()
        return try {
            val agent = agents.agent(id = "$runId/$testId")
            try {
                agent.run(RunRequest(runId, testId, device), null)
            } finally {
                agent.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.debug("harness error in {}", testId, e)
            harnessFailure(runId, spec, device, startedAt.toString(), e)
        }
    }

    /** The record of a test the harness could not finish: what failed, and no verdict. */
    private suspend fun harnessFailure(runId: String, spec: Specification, device: DeviceSlot, startedAt: String, error: Exception): Reported {
        val paths = TestPaths(Path.of(props.root), runId, spec.id)
        val record = RunRecord(
            runId = runId,
            specId = spec.id,
            title = spec.title,
            goal = spec.goal,
            criteria = spec.criteria,
            backend = props.backend.name.lowercase(),
            appId = props.appId,
            device = device.name,
            startedAt = startedAt,
            finishedAt = clock.instant().toString(),
            scenario = "unknown",
            attempts = emptyList(),
            warnings = emptyList(),
            outcome = RunOutcome.Inconclusive("harness_error: ${error.message ?: error::class.simpleName}"),
        )
        runCatchingCancellable { records.write(record, paths) }
            .onFailure { log.error("[{}] the record of the unfinished test could not be written: {}", spec.id, it.message) }
        events.emit(Event.Finished(device.name, spec.id, record.outcome.status, 0))
        return Reported(record, paths.recordFile.toString(), paths.reportFile.toString())
    }
}
