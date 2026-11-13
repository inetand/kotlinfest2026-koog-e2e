package harness.platform

import kotlin.coroutines.cancellation.CancellationException

/** The caller asked for something that cannot be run: an unknown test, a malformed specification. Never a verdict. */
class UsageError(message: String) : RuntimeException(message)

/** The harness itself failed (a model answer that cannot be parsed, a tool that cannot start). Never a verdict. */
class HarnessError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** `runCatching` that lets cancellation through, so a cancelled coroutine is never mistaken for a failed call. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
