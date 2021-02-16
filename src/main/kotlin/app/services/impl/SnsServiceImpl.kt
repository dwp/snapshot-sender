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
import io.prometheus.client.Counter
import io.prometheus.client.spring.web.PrometheusTimeMethod

@Component
class SnsServiceImpl(private val amazonSns: AmazonSNS,
    private val monitoringMessagesSentCounter: Counter): SnsService {

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${sns.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${sns.retry.delay:1000}",
            multiplierExpression = "\${sns.retry.multiplier:2}"))
    @PrometheusTimeMethod(name = "snapshot_sender_monitoring_message_send_duration", help = "Duration of sending a monitoring message")
    override fun sendMonitoringMessage(completionStatus: SendingCompletionStatus) =
        sendMessage(monitoringTopicArn, monitoringPayload(completionStatus), completionStatus)

    private fun sendMessage(topicArn: String, payload: String, completionStatus: SendingCompletionStatus) {
        topicArn.takeIf(String::isNotBlank)?.let { arn ->
            logger.info("Publishing message to topic", "arn" to arn)
            val result = amazonSns.publish(request(arn, payload))
            logger.info("Published message to topic", "arn" to arn,
                "message_id" to result.messageId, "snapshot_type" to snapshotType)
            monitoringMessagesSentCounter.labels(severity(completionStatus), 
                notificationType(completionStatus)).inc()
        } ?: run {
            logger.info("Not publishing message to topic", "reason" to "No arn configured")
        }
    }

    private fun monitoringPayload(sendingCompletionStatus: SendingCompletionStatus) =
            """{
                "severity": "${severity(sendingCompletionStatus)}",
                "notification_type": "${notificationType(sendingCompletionStatus)}",
                "slack_username": "Crown Export Poller",
                "title_text": "${snapshotType.capitalize()} - All files sent - ${sendingCompletionStatus.description}",
                "custom_elements": [
                    { "key": "Export date", "value": "$exportDate" },
                    { "key": "Correlation Id", "value": "${PropertyUtility.correlationId()}" }
                ]
            }"""
    
    private fun severity(sendingCompletionStatus: SendingCompletionStatus): String =
        when (sendingCompletionStatus) {
            SendingCompletionStatus.COMPLETED_SUCCESSFULLY -> {
                "Critical"
            }
            else -> {
                "High"
            }
        }

    private fun notificationType(sendingCompletionStatus: SendingCompletionStatus): String =
        when (sendingCompletionStatus) {
            SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY -> {
                "Error"
            }
            else -> {
                "Information"
            }
        }

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