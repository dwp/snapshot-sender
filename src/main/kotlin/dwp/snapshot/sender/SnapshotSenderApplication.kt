package dwp.snapshot.sender

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication (exclude = [DataSourceAutoConfiguration::class])
class SnapshotSenderApplication

fun main(args: Array<String>) {
	exitProcess(SpringApplication.exit(runApplication<SnapshotSenderApplication>(*args)))
}
