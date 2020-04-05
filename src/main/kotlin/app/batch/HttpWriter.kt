package app.batch

import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.DataKeyServiceUnavailableException
import app.exceptions.MetadataException
import app.exceptions.WriterException
import app.services.impl.HttpKeyService
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.text.SimpleDateFormat
import java.util.*

@Component
@Profile("httpWriter")
class HttpWriter(private val httpClientProvider: HttpClientProvider) : ItemWriter<DecryptedStream> {



    @Autowired
    lateinit var s3StatusFileWriter: S3StatusFileWriter

    val filenameRe = Regex("""^\w+\.([\w-]+)\.([\w-]+)""")

    override fun write(items: MutableList<out DecryptedStream>) {
        logger.info("Writing items to S3", "number_of_items" to items.size.toString())
        items.forEach { item ->
            postItem(item)
        }
    }

    @Retryable(value = [Exception::class],
            maxAttempts = maxAttempts,
            backoff = Backoff(delay = initialBackoffMillis, multiplier = backoffMultiplier))
    private fun postItem(item: DecryptedStream) {
        logger.info("Checking item to  write", "file_name" to item.fileName, "full_path" to item.fullPath)
        val match = filenameRe.find(item.fileName)
        if (match == null) {
            val errorMessage = "Rejecting: '${item.fullPath}' as fileName does not match '$filenameRe'"
            val exception = MetadataException(errorMessage)
            logger.error("Rejecting item to write", exception, "file_name" to item.fullPath, "expected_file_name" to filenameRe.toString())
            throw exception
        }

        val lastDashIndex = item.fileName.lastIndexOf("-")
        if (lastDashIndex < 0) {
            val errorMessage = "Rejecting: '${item.fullPath}' as fileName does not contain '-' to find number"
            val exception = MetadataException(errorMessage)
            logger.error("Rejecting item to write", exception, "file_name" to item.fullPath)
            throw exception
        }
        val database = match.groupValues[1]
        val collection = match.groupValues[2].replace(Regex("""(-\d{3}-\d{3})?-\d+$"""), "")
        val topic = "db.$database.$collection"
        val filenameHeader = item.fileName.replace(Regex("""\.txt\.gz$"""), ".json.gz")
        logger.info("Posting file name to collection",
                "database" to database,
                "collection" to collection,
                "topic" to topic,
                "file_name" to item.fileName,
                "full_path" to item.fullPath,
                "nifi_url" to nifiUrl,
                "filename_header" to filenameHeader)
        httpClientProvider.client().use {
            val post = HttpPost(nifiUrl).apply {
                entity = InputStreamEntity(item.inputStream, -1, ContentType.DEFAULT_BINARY)
                setHeader("filename", filenameHeader)
                setHeader("environment", "aws/${System.getProperty("environment")}")
                setHeader("date", SimpleDateFormat("yyyy-MM-dd").format(Date()))
                setHeader("database", database)
                setHeader("collection", collection)
                setHeader("topic", topic)
            }

            it.execute(post).use { response ->
                when (response.statusLine.statusCode) {
                    200 -> {
                        logger.info("Successfully posted file",
                                "file_name" to item.fullPath,
                                "response" to response.statusLine.statusCode.toString(),
                                "nifi_url" to nifiUrl)
                        s3StatusFileWriter.writeStatus(item.fullPath)
                    }
                    else -> {
                        val message = "Failed to post '${item.fullPath}': post returned status code ${response.statusLine.statusCode}"
                        val exception = WriterException(message)
                        logger.error("Failed to post the provided item", exception,
                                "file_name" to item.fullPath,
                                "response" to response.statusLine.statusCode.toString(),
                                "nifi_url" to nifiUrl)
                        throw exception
                    }
                }
            }
        }
    }

    @Value("\${nifi.url}")
    private lateinit var nifiUrl: String

    companion object {
        val logger = DataworksLogger.getLogger(HttpWriter::class.toString())
        // Will retry at 1s, 2s, 4s, 8s, 16s ...
        const val maxAttempts = 10
        const val initialBackoffMillis = 1000L
        const val backoffMultiplier = 2.0
    }

}
