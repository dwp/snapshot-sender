package app.services.impl

import app.services.SendingCompletionStatus
import app.services.CollectionStatus
import app.services.ExportStatusService
import app.utils.PropertyUtility.correlationId
import app.utils.PropertyUtility.topicName
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.logging.DataworksLogger
import io.prometheus.client.Counter
import io.prometheus.client.spring.web.PrometheusTimeMethod

@Service
class DynamoDBExportStatusService(
    private val dynamoDB: AmazonDynamoDB,
    private val successfulCollectionCounter: Counter,
    private val sentNonEmptyCollectionCounter: Counter,
    private val sentEmptyCollectionCounter: Counter,
    private val filesSentIncrementedCounter: Counter,
    private val successfulFullRunCounter: Counter,
    private val failedFullRunCounter: Counter): ExportStatusService {

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${dynamodb.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${dynamodb.retry.delay:1000}",
            multiplierExpression = "\${dynamodb.retry.multiplier:2}"))
    @PrometheusTimeMethod(name = "snapshot_sender_increment_sent_count_duration", help = "Duration of incrementing sent count")
    override fun incrementSentCount(fileSent: String) {
        val result = dynamoDB.updateItem(incrementFilesSentRequest())
        filesSentIncrementedCounter.inc(1.toDouble())
        logger.info("Incremented files sent",
                "file_sent" to fileSent,
                "files_sent" to "${result.attributes["FilesSent"]?.n}")
    }

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${dynamodb.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${dynamodb.retry.delay:1000}",
            multiplierExpression = "\${dynamodb.retry.multiplier:2}"))
    @PrometheusTimeMethod(name = "snapshot_sender_set_success_status_duration", help = "Duration of setting success status")
    override fun setSuccessStatus() {
        dynamoDB.updateItem(setStatusSuccessRequest())
        successfulCollectionCounter.inc(1.toDouble())
    }

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${dynamodb.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${dynamodb.retry.delay:1000}",
            multiplierExpression = "\${dynamodb.retry.multiplier:2}"))
    @PrometheusTimeMethod(name = "snapshot_sender_set_collection_status_duration", help = "Duration of setting collection status")
    override fun setCollectionStatus(): CollectionStatus =
            when (collectionStatus()) {
                CollectionStatus.SENT -> {
                    val result = dynamoDB.updateItem(setStatusSentRequest())
                    sentNonEmptyCollectionCounter.inc(1.toDouble())
                    logger.info("Collection status after update",
                        "collection_status" to "${result.attributes["CollectionStatus"]?.s}")
                    CollectionStatus.SENT
                }

                CollectionStatus.NO_FILES_EXPORTED -> {
                    val result = dynamoDB.updateItem(setStatusReceivedRequest())
                    sentEmptyCollectionCounter.inc(1.toDouble())
                    logger.info("Collection status after update",
                        "collection_status" to "${result.attributes["CollectionStatus"]?.s}")
                    CollectionStatus.NO_FILES_EXPORTED
                }

                else -> {
                    CollectionStatus.IN_PROGRESS
                }
            }

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${dynamodb.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${dynamodb.retry.delay:1000}",
            multiplierExpression = "\${dynamodb.retry.multiplier:2}"))
        @PrometheusTimeMethod(name = "snapshot_sender_get_completion_status_duration", help = "Duration of getting completion status")
        override fun sendingCompletionStatus(): SendingCompletionStatus =
            exportStatusTable().query(statusQuerySpec()).map(::collectionStatus).run {
                when {
                    all(::completedSuccessfully) -> {
                        successfulFullRunCounter.inc(1.toDouble())
                        SendingCompletionStatus.COMPLETED_SUCCESSFULLY
                    }
                    any(::completedUnsuccessfully) -> {
                        failedFullRunCounter.inc(1.toDouble())
                        SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY
                    }
                    else -> {
                        SendingCompletionStatus.NOT_COMPLETED
                    }
                }
            }

    private fun collectionStatus(item: Item): String = item[COLLECTION_STATUS_ATTRIBUTE_NAME] as String

    private fun completedSuccessfully(status: Any): Boolean =
        successfulCompletionStatuses.contains(status)

    private fun completedUnsuccessfully(status: Any): Boolean =
        unsuccessfulCompletionStatuses.contains(status)

    private fun exportStatusTable(): Table = DynamoDB(dynamoDB).getTable(statusTableName)

    private fun statusQuerySpec(): QuerySpec =
        QuerySpec().apply {
            withKeyConditionExpression("#cId = :s")
            withNameMap(mapOf("#cId" to "CorrelationId"))
            withValueMap(mapOf(":s" to correlationId()))
        }

    private fun collectionStatus(): CollectionStatus {
        val (currentStatus, filesExported, filesSent) = currentStatusAndCounts()

        logger.info("Collection status",
            "current_status" to currentStatus,
            "files_exported" to "$filesExported",
            "files_sent" to "$filesSent")

        return when {
            currentStatus == "Exported" && filesExported == filesSent && filesExported > 0 -> {
                CollectionStatus.SENT
            }
            currentStatus == "Exported" && filesExported == filesSent && filesExported == 0 -> {
                CollectionStatus.NO_FILES_EXPORTED
            }
            else -> {
                CollectionStatus.IN_PROGRESS
            }
        }
    }

    private fun currentStatusAndCounts(): Triple<String, Int, Int> {
        val result = dynamoDB.getItem(getItemRequest())
        val item = result.item
        val status = item["CollectionStatus"]
        val filesExported = item["FilesExported"]
        val filesSent = item["FilesSent"]
        return Triple(status?.s ?: "", (filesExported?.n ?: "0").toInt(), (filesSent?.n ?: "0").toInt())
    }

    private fun incrementFilesSentRequest() =
            UpdateItemRequest().apply {
                tableName = statusTableName
                key = primaryKey
                updateExpression = "SET FilesSent = FilesSent + :x"
                expressionAttributeValues = mapOf(":x" to AttributeValue().apply { n = "1" })
                returnValues = "ALL_NEW"
            }


    private fun setStatusSentRequest() = setStatusRequest("Sent")
    private fun setStatusSuccessRequest() = setStatusRequest("Success")
    private fun setStatusReceivedRequest() = setStatusRequest("Received")

    private fun setStatusRequest(status: String) =
            UpdateItemRequest().apply {
                tableName = statusTableName
                key = primaryKey
                updateExpression = "SET CollectionStatus = :x"
                expressionAttributeValues = mapOf(":x" to AttributeValue().apply { s = status })
                returnValues = "ALL_NEW"
            }

    private fun getItemRequest() =
        GetItemRequest().apply {
            tableName = statusTableName
            key = primaryKey
            consistentRead = true
        }

    private val primaryKey by lazy {
        mapOf("CorrelationId" to stringAttribute(correlationId()),
                "CollectionName" to stringAttribute(topicName()))
    }

    private fun stringAttribute(value: String) = AttributeValue().apply { s = value }

    @Value("\${dynamodb.status.table.name:UCExportToCrownStatus}")
    private lateinit var statusTableName: String

    companion object {
        val logger = DataworksLogger.getLogger(DynamoDBExportStatusService::class.toString())
        private const val COLLECTION_STATUS_ATTRIBUTE_NAME = "CollectionStatus"
        private val successfulCompletionStatuses = listOf("Sent", "Received", "Success", "Table_Unavailable", "Blocked_Topic")
        private val unsuccessfulCompletionStatuses = listOf("Export_Failed")
    }
}
