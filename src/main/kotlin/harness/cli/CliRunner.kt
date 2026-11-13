package harness.cli

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.stereotype.Component
import picocli.CommandLine
import picocli.CommandLine.IFactory

/** Hands the arguments to picocli once the context is up. The factory makes picocli take its commands from Spring. */
@Component
class CliRunner(
    private val root: HarnessCommand,
    private val factory: IFactory,
) : CommandLineRunner, ExitCodeGenerator {

    private var exitCode = 0

    override fun run(vararg args: String) {
        exitCode = CommandLine(root, factory).execute(*args)
    }

    override fun getExitCode(): Int = exitCode
}
