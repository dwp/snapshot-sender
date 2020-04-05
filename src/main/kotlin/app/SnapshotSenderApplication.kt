package app

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import uk.gov.dwp.dataworks.logging.LogFields
import kotlin.system.exitProcess

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
@EnableRetry
class SnapshotSenderApplication

fun main(args: Array<String>) {
    LogFields.put("SQS_MESSAGE_ID", "sqs_message_id", "NOT_SET")
    LogFields.put("TOPIC_NAME", "topic_name", "NOT_SET")
    exitProcess(SpringApplication.exit(runApplication<SnapshotSenderApplication>(*args)))
}
