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
    version = ["harness 0.1.0"],
    description = ["A simplified, educational test harness: Koog graphs on fake devices, run concurrently."],
    subcommands = [RunCommand::class, ListCommand::class, GraphCommand::class, DoctorCommand::class, HelpCommand::class],
    footer = [
        "",
        "Environment: HARNESS_DEVICES, HARNESS_VERIFIER_PERMITS, HARNESS_MAX_ATTEMPTS,",
        "             HARNESS_DEVICE_LATENCY, HARNESS_VERIFIER_LATENCY (e.g. 300ms)",
    ],
)
class HarnessCommand : Runnable {

    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
