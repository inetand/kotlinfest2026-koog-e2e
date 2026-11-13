package harness.graph.node

import harness.graph.Executed
import harness.graph.GraphNode
import harness.graph.Verified
import harness.verify.SharedVerifier
import org.springframework.stereotype.Component

/** Asks the shared verifier whether the screen is the expected one. This is where a run may wait for a permit. */
@Component
class VerifyNode(private val verifier: SharedVerifier) : GraphNode<Executed, Verified> {

    override val name = "verify"

    override suspend fun run(input: Executed): Verified {
        val request = input.state.request
        return Verified(input.state, verifier.verify(request.device, request.scenario, input.screen))
    }
}
