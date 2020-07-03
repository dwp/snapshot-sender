package app.services.impl

import app.services.ExportStatusService
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
            maxAttempts = maxAttempts,
            backoff = Backoff(delay = initialBackoffMillis, multiplier = backoffMultiplier))
    override fun incrementSentCount() {
        val result = dynamoDB.updateItem(incrementFilesSentRequest())
        logger.info("Update FilesSent: ${result.attributes["FilesSent"]}")
    }

    @Retryable(value = [Exception::class],
            maxAttempts = maxAttempts,
            backoff = Backoff(delay = initialBackoffMillis, multiplier = backoffMultiplier))
    override fun setSentStatus(): Boolean =
        if (collectionIsComplete()) {
            val result = dynamoDB.updateItem(setStatusSentRequest())
            logger.info("Update CollectionStatus: ${result.attributes["CollectionStatus"]}")
            true
        }
        else {
            false
        }

    private fun collectionIsComplete(): Boolean {
        val (currentStatus, filesExported, filesSent) = currentStatusAndCounts()
        return currentStatus == "Exported" && filesExported == filesSent && filesExported > 0
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
                updateExpression = "ADD FilesSent :x"
                expressionAttributeValues = mapOf(":x" to AttributeValue().apply { n = "1" })
                returnValues = "ALL_NEW"
            }


    private fun setStatusSentRequest() =
            UpdateItemRequest().apply {
                tableName = statusTableName
                key = primaryKey
                updateExpression = "SET CollectionStatus :x"
                expressionAttributeValues = mapOf(":x" to AttributeValue().apply { s = "Sent" })
                returnValues = "ALL_NEW"
            }

    private fun getItemRequest() =
        GetItemRequest().apply {
            tableName = statusTableName
            key = primaryKey
        }

    private val primaryKey by lazy {
        val correlationIdAttributeValue = AttributeValue().apply {
            s = correlationId
        }

        val collectionNameAttributeValue = AttributeValue().apply {
            s = topicName
        }

        mapOf("CorrelationId" to correlationIdAttributeValue,
              "CollectionName" to collectionNameAttributeValue)
    }

    @Value("\${dynamodb.status.table.name:UCExportToCrownStatus}")
    private lateinit var statusTableName: String

    private val correlationId by lazy {
        System.getProperty("correlation_id")
    }

    private val topicName by lazy {
        System.getProperty("topic_name")
    }

    companion object {
        val logger = DataworksLogger.getLogger(DynamoDBExportStatusService::class.toString())
        // Will retry at 1s, 2s, 4s, 8s, 16s then give up (after a total of 31 secs)
        const val maxAttempts = 5
        const val initialBackoffMillis = 1000L
        const val backoffMultiplier = 2.0
    }
}
