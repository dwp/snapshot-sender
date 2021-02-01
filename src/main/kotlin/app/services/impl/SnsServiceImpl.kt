package app.services.impl

import app.services.SendingCompletionStatus
import app.services.SnsService
import app.utils.PropertyUtility
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
class SnsServiceImpl(private val amazonSns: AmazonSNS): SnsService {

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${sns.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${sns.retry.delay:1000}",
            multiplierExpression = "\${sns.retry.multiplier:2}"))
    override fun sendMonitoringMessage(completionStatus: SendingCompletionStatus) =
        sendMessage(monitoringTopicArn, monitoringPayload(completionStatus))

    private fun sendMessage(topicArn: String, payload: String) {
        topicArn.takeIf(String::isNotBlank)?.let { arn ->
            logger.info("Publishing message to topic", "arn" to arn)
            val result = amazonSns.publish(request(arn, payload))
            logger.info("Published message to topic", "arn" to arn,
                "message_id" to result.messageId, "snapshot_type" to snapshotType)
        } ?: run {
            logger.info("Not publishing message to topic", "reason" to "No arn configured")
        }
    }

    private fun monitoringPayload(sendingCompletionStatus: SendingCompletionStatus) =
            """{
                "severity": "Critical",
                "notification_type": "Information",
                "slack_username": "Crown Export Poller",
                "title_text": "${snapshotType.capitalize()} - All files sent - ${sendingCompletionStatus.description}",
                "custom_elements": [
                    { "key": "Export date", "value": "$exportDate" },
                    { "key": "Correlation Id", "value": "${PropertyUtility.correlationId()}" }
                ]
            }"""

    private fun request(arn: String, payload: String) =
        PublishRequest().apply {
            topicArn = arn
            message = payload
        }

    @Value("\${topic.arn.monitoring:}")
    private lateinit var monitoringTopicArn: String

    @Value("\${snapshot.type}")
    private lateinit var snapshotType: String

    @Value("\${s3.prefix.folder}")
    private lateinit var s3prefix: String

    @Value("\${export.date}")
    private lateinit var exportDate: String

    companion object {
        private val logger = DataworksLogger.getLogger(SnsServiceImpl::class)
    }
}