package app.services.impl

import app.services.ExportStatusService
import app.utils.PropertyUtility.correlationId
import app.utils.PropertyUtility.topicName
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class DynamoDBExportStatusService(private val dynamoDB: AmazonDynamoDB): ExportStatusService {


    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${dynamodb.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${dynamodb.retry.delay:1000}",
            multiplierExpression = "\${dynamodb.retry.multiplier:2}"))
    override fun incrementSentCount(fileSent: String) {
        val result = dynamoDB.updateItem(incrementFilesSentRequest())
        logger.info("Incremented files sent",
                "file_sent" to fileSent,
                "files_sent" to "${result.attributes["FilesSent"]?.n}")
    }

    @Retryable(value = [Exception::class],
        maxAttemptsExpression = "\${dynamodb.retry.maxAttempts:5}",
        backoff = Backoff(delayExpression = "\${dynamodb.retry.delay:1000}",
            multiplierExpression = "\${dynamodb.retry.multiplier:2}"))
    override fun setSentStatus(): Boolean =
            if (collectionIsComplete()) {
                val result = dynamoDB.updateItem(setStatusSentRequest())
                logger.info("Collection status after update",
                        "collection_status" to "${result.attributes["CollectionStatus"]?.s}")
                true
            }
            else {
                false
            }

    private fun collectionIsComplete(): Boolean {
        val (currentStatus, filesExported, filesSent) = currentStatusAndCounts()
        val isComplete = currentStatus == "Exported" && filesExported == filesSent //&& filesExported > 0
        logger.info("Collection status", "current_status" to currentStatus,
                "files_exported" to "$filesExported",
                "files_sent" to "$filesSent",
                "is_complete" to "$isComplete")
        return isComplete
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


    private fun setStatusSentRequest() =
            UpdateItemRequest().apply {
                tableName = statusTableName
                key = primaryKey
                updateExpression = "SET CollectionStatus = :x"
                expressionAttributeValues = mapOf(":x" to AttributeValue().apply { s = "Sent" })
                returnValues = "ALL_NEW"
            }

    private fun getItemRequest() =
        GetItemRequest().apply {
            tableName = statusTableName
            key = primaryKey
            consistentRead = true
        }

    private val primaryKey by lazy {
        mapOf("CorrelationId" to stringAttribute(correlationId),
                "CollectionName" to stringAttribute(topicName))
    }

    private fun stringAttribute(value: String) = AttributeValue().apply { s = value }

    @Value("\${dynamodb.status.table.name:UCExportToCrownStatus}")
    private lateinit var statusTableName: String

    companion object {
        val logger = DataworksLogger.getLogger(DynamoDBExportStatusService::class.toString())
    }
}
