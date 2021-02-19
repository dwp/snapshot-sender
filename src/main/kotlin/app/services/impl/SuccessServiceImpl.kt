package app.services.impl

import app.configuration.HttpClientProvider
import app.domain.NifiHeaders
import app.exceptions.SuccessException
import app.services.SuccessService
import app.utils.NiFiUtility
import app.utils.PropertyUtility.correlationId
import app.utils.PropertyUtility.topicName
import io.prometheus.client.Counter
import io.prometheus.client.spring.web.PrometheusTimeMethod
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import io.prometheus.client.Counter
import io.prometheus.client.spring.web.PrometheusTimeMethod

@Component
class SuccessServiceImpl(private val httpClientProvider: HttpClientProvider, 
    private val nifiUtility: NiFiUtility,
    private val successFilesSentCounter: Counter,
    private val successFilesRetriedCounter: Counter): SuccessService {

    @Retryable(
        value = [Exception::class],
        maxAttemptsExpression = "\${success.retry.maxAttempts:5}",
        backoff = Backoff(
            delayExpression = "\${success.retry.delay:1000}",
            multiplierExpression = "\${success.retry.multiplier:2}"
        )
    )
    @PrometheusTimeMethod(name = "snapshot_sender_post_success_file_duration", help = "Duration of posting a success file")
    override fun postSuccessIndicator() {
        databaseAndCollectionMatchResult()?.let { match ->
            val (database, collection) = match.destructured
            val fileName = "_${database}_${collection}_successful.gz"
            logger.info("Writing success indicator to crown", "file_name" to fileName)
            val inputStream = ByteArrayInputStream(zeroBytesCompressed())
            httpClientProvider.client().use {
                val headers = NifiHeaders(
                    filename = fileName,
                    database = database,
                    collection = collection)

                val post = HttpPost(nifiUrl).apply {
                    entity = InputStreamEntity(inputStream, -1, ContentType.DEFAULT_BINARY)
                    nifiUtility.setNifiHeaders(this, headers)
                }

                it.execute(post).use { response ->
                    when (response.statusLine.statusCode) {
                        200 -> {
                            logger.info(
                                "Successfully posted success indicator",
                                "file_name" to fileName,
                                "response" to response.statusLine.statusCode.toString(),
                                "nifi_url" to nifiUrl,
                                "database" to database,
                                "collection" to collection,
                                "topic" to topicName(),
                                "export_date" to exportDate,
                                "snapshot_type" to snapshotType,
                                "status_table_name" to statusTableName,
                                "correlation_id" to correlationId()
                            )
                            successFilesSentCounter.inc()
                        }
                        else -> {
                            logger.warn(
                                "Failed to post success indicator",
                                "file_name" to fileName,
                                "response" to response.statusLine.statusCode.toString(),
                                "nifi_url" to nifiUrl,
                                "database" to database,
                                "collection" to collection,
                                "topic" to topicName(),
                                "export_date" to exportDate,
                                "snapshot_type" to snapshotType,
                                "status_table_name" to statusTableName,
                                "correlation_id" to correlationId()
                            )
                            successFilesRetriedCounter.inc()
                            throw SuccessException("Failed to post success indicator $fileName, response: ${response.statusLine.statusCode}")
                        }
                    }
                }
            }
        }
    }

    private fun databaseAndCollectionMatchResult(): MatchResult? =
        Regex("""^(?:\w+\.)?(?<database>[\w-]+)\.(?<collection>[\w-]+)""").find(topicName())

    private fun zeroBytesCompressed(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).close()
        return outputStream.toByteArray()
    }

    @Value("\${nifi.url}")
    private lateinit var nifiUrl: String

    @Value("\${export.date}")
    private lateinit var exportDate: String

    @Value("\${snapshot.type}")
    private lateinit var snapshotType: String

    @Value("\${dynamodb.status.table.name:UCExportToCrownStatus}")
    private lateinit var statusTableName: String

    companion object {
        val logger = DataworksLogger.getLogger(SuccessServiceImpl::class.toString())
    }
}
