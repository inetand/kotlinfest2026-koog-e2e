package harness

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

/** The composition root. Spring Boot only assembles the object graph and binds configuration; it runs no web server. */
@SpringBootApplication
@ConfigurationPropertiesScan
class HarnessApplication

fun main(args: Array<String>) {
    KotlinLoggingConfiguration.logStartupMessage = false
    exitProcess(SpringApplication.exit(runApplication<HarnessApplication>(*args)))
}
