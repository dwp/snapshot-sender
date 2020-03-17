package app.batch

import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.MetadataException
import app.exceptions.WriterException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
@Profile("httpWriter")
class HttpWriter(private val httpClientProvider: HttpClientProvider) : ItemWriter<DecryptedStream> {

    @Autowired
    lateinit var s3StatusFileWriter: S3StatusFileWriter

    val filenameRe = Regex("""^\w+\.(?:\w|-)+\.((?:\w|-)+)""")

    override fun write(items: MutableList<out DecryptedStream>) {
        logger.info("Writing items to S3", "number_of_items" to items.size.toString())
        items.forEach { item ->
            logger.info("Checking item to  write", "file_name" to item.fullPath)
            val match = filenameRe.find(item.fileName)
            if (match == null) {
                logger.error("Rejecting item to write due to name not matching expected", "file_name" to item.fullPath, "expected_file_name" to filenameRe.toString())
                val errorMessage = "Rejecting: '${item.fullPath}' as fileName does not match '$filenameRe'"
                throw MetadataException(errorMessage)
            }

            val lastDashIndex = item.fileName.lastIndexOf("-")
            if (lastDashIndex < 0) {
                logger.error("Rejecting item to write due to name not containing '-' to find number", "file_name" to item.fullPath)
                val errorMessage = "Rejecting: '${item.fullPath}' as fileName does not contain '-' to find number"
                throw MetadataException(errorMessage)
            }
            val fullCollection = item.fileName.substring(0 until (lastDashIndex))
            logger.info("Found collection of file name", "collection" to fullCollection, "file_name" to item.fullPath)

            logger.info("Posting file name to collection", "collection" to fullCollection, "file_name" to item.fullPath)
            httpClientProvider.client().use {
                val post = HttpPost(nifiUrl).apply {
                    entity = InputStreamEntity(item.inputStream, -1, ContentType.DEFAULT_BINARY)
                    setHeader("filename", item.fileName)
                    setHeader("collection", fullCollection)
                }

                it.execute(post).use { response ->
                    when (response.statusLine.statusCode) {
                        200 -> {
                            logger.info("Successfully posted file", "file_name" to item.fullPath, "response" to response.statusLine.statusCode.toString())
                            s3StatusFileWriter.writeStatus(item.fullPath)
                        }
                        else -> {
                            logger.error("Failed to post the provided item", "file_name" to item.fullPath, "response" to response.statusLine.statusCode.toString())
                            val message = "Failed to post '${item.fullPath}': post returned status code ${response.statusLine.statusCode}"
                            throw WriterException(message)
                        }
                    }
                }
            }
        }
    }

    @Value("\${nifi.url}")
    private lateinit var nifiUrl: String

    companion object {
        val logger = DataworksLogger.getLogger(HttpWriter::class.toString())
    }
}
