package app.batch

import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.MetadataException
import app.exceptions.WriterException
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("httpWriter")
class HttpWriter(private val httpClientProvider: HttpClientProvider) : ItemWriter<DecryptedStream> {

//    @Autowired
//    lateinit var s3StatusFileWriter: S3StatusFileWriter

    val filenameRe = Regex("""^\w+\.(?:\w|-)+\.((?:\w|-)+)""")

    override fun write(items: MutableList<out DecryptedStream>) {
        logger.info("Writing: '${items.size}' items")
        items.forEach { item ->
            logger.info("Checking: '${item.filename}'")
            val match = filenameRe.find(item.filename)
            if (match == null) {
                val errorMessage = "Rejecting: '${item.filename}' as name does not match '$filenameRe'"
                logger.error(errorMessage)
                throw MetadataException(errorMessage)
            }

            logger.info("Writing: '$item'")
            val lastDashIndex = item.filename.lastIndexOf("-")
            val fullCollection = item.filename.substring(0 until (lastDashIndex))
            logger.info("Found collection: '${fullCollection}' from filename '${item.filename}'")
            httpClientProvider.client().use {
                val post = HttpPost(nifiUrl).apply {
                    entity = InputStreamEntity(item.inputStream, -1, ContentType.DEFAULT_BINARY)
                    setHeader("filename", item.filename)
                    setHeader("collection", fullCollection)
                }

                it.execute(post).use { response ->
                    when (response.statusLine.statusCode) {
                        200 -> {
                            logger.info("Successfully posted '${item.filename}': response '${response.statusLine.statusCode}'")
                            //s3StatusFileWriter.writeStatus(item.filename)
                        }
                        else -> {
                            val message = "Failed to write '${item.filename}': post returned status code ${response.statusLine.statusCode}"
                            logger.error(message)
                            throw WriterException(message)
                        }
                    }
                }
            }
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpWriter::class.toString())
    }

    @Value("\${nifi.url}")
    private lateinit var nifiUrl: String
}