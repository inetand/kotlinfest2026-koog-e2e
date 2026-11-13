package harness.cli

import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.HelpCommand
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/** The root command. Without a subcommand it prints the help. */
@Component
@Command(
    name = "harness",
    mixinStandardHelpOptions = true,
    version = ["harness 0.2.0"],
    description = ["A simplified, educational E2E test harness: stored scenarios replayed through a Koog graph, concurrently."],
    subcommands = [RunCommand::class, ListCommand::class, GraphCommand::class, DoctorCommand::class, HelpCommand::class],
    footer = [
        "",
        "Environment: HARNESS_ROOT, HARNESS_APP_ID, HARNESS_BACKEND (fake|maestro), HARNESS_DEVICES (a,b,c),",
        "             HARNESS_MAX_ATTEMPTS, HARNESS_ATTEMPT_TIMEOUT, HARNESS_MODEL_PERMITS, HARNESS_JUDGE (rule|llm),",
        "             HARNESS_LLM_BASE_URL, HARNESS_LLM_MODEL, HARNESS_LLM_API_KEY",
    ],
)
class HarnessCommand : Runnable {

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
