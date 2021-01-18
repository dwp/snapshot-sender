package app.services.impl

import app.configuration.HttpClientProvider
import app.exceptions.SuccessException
import app.services.SuccessService
import org.apache.commons.lang3.StringUtils
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

@Component
class SuccessServiceImpl(private val httpClientProvider: HttpClientProvider): SuccessService {

    @Retryable(value = [Exception::class],
            maxAttempts = maxAttempts,
            backoff = Backoff(delay = initialBackoffMillis, multiplier = backoffMultiplier))
    override fun postSuccessIndicator() {
        val topic = System.getProperty("topic_name")
        if (StringUtils.isNotBlank(topic)) {
            val topicRegex = Regex("""^(?:\w+\.)?(?<database>[\w-]+)\.(?<collection>[\w-]+)""")
            val match = topicRegex.find(topic)
            if (match != null) {
                val database = match.groups["database"]?.value ?: ""
                val collection = match.groups["collection"]?.value ?: ""
                val fileName = "_${database}_${collection}_successful.gz"
                logger.info("Writing success indicator to crown", "file_name" to fileName)
                val inputStream = ByteArrayInputStream(zeroBytesCompressed())
                httpClientProvider.client().use {
                    val post = HttpPost(nifiUrl).apply {
                        entity = InputStreamEntity(inputStream, -1, ContentType.DEFAULT_BINARY)
                        setHeader("filename", fileName)
                        setHeader("environment", "aws/${System.getProperty("environment")}")
                        setHeader("export_date", exportDate)
                        setHeader("database", database)
                        setHeader("collection", collection)
                        setHeader("snapshot_type", snapshotType)
                        setHeader("topic", topic)
                    }

                    it.execute(post).use { response ->
                        when (response.statusLine.statusCode) {
                            200 -> {
                                logger.info("Successfully posted success indicator",
                                        "file_name" to fileName,
                                        "response" to response.statusLine.statusCode.toString(),
                                        "nifi_url" to nifiUrl,
                                        "database" to database,
                                        "collection" to collection,
                                        "topic" to topic,
                                        "export_date" to exportDate,
                                        "snapshot_type" to snapshotType)
                            }
                            else -> {
                                logger.warn("Failed to post success indicator",
                                        "file_name" to fileName,
                                        "response" to response.statusLine.statusCode.toString(),
                                        "nifi_url" to nifiUrl,
                                        "database" to database,
                                        "collection" to collection,
                                        "topic" to topic,
                                        "export_date" to exportDate,
                                        "snapshot_type" to snapshotType)
                                throw SuccessException("Failed to post success indicator $fileName, response: ${response.statusLine.statusCode}")
                            }
                        }
                    }
                }
            }
        }
    }

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

    companion object {
        val logger = DataworksLogger.getLogger(SuccessServiceImpl::class.toString())
        const val maxAttempts = 10
        const val initialBackoffMillis = 1000L
        const val backoffMultiplier = 2.0
    }
}
