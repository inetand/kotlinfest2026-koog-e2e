package harness.judge

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * How many judge calls may be in flight at once across every device of a run. The workers of different devices
 * share one model account, so a limit keeps its rate limit out of reach. Only this call is limited; device work is
 * never held back by it. A permit is released when the call ends, fails or is cancelled.
 */
class ModelPermits(val limit: Int) {

    private val permits = Semaphore(limit)

    val inUse: Int
        get() = limit - permits.availablePermits

    /** Runs [call] once a permit is free; suspends, without blocking a thread, until then. */
    suspend fun <T> holding(call: suspend () -> T): T = permits.withPermit { call() }
}
