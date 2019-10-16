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
            logger.info("Checking: '${item.fullPath}'")
            val match = filenameRe.find(item.fileName)
            if (match == null) {
                val errorMessage = "Rejecting: '${item.fullPath}' as fileName does not match '$filenameRe'"
                logger.error(errorMessage)
                throw MetadataException(errorMessage)
            }

            logger.info("Posting: '${item.fullPath}'")
            val lastDashIndex = item.fileName.lastIndexOf("-")
            val fullCollection = item.fileName.substring(0 until (lastDashIndex))
            logger.info("Found collection: '${fullCollection}' from fileName of '${item.fullPath}'")
            httpClientProvider.client().use {
                val post = HttpPost(nifiUrl).apply {
                    entity = InputStreamEntity(item.inputStream, -1, ContentType.DEFAULT_BINARY)
                    setHeader("filename", item.fileName)
                    setHeader("collection", fullCollection)
                }

                it.execute(post).use { response ->
                    when (response.statusLine.statusCode) {
                        200 -> {
                            logger.info("Successfully posted '${item.fullPath}': response '${response.statusLine.statusCode}'")
                            //s3StatusFileWriter.writeStatus(item.filename)
                        }
                        else -> {
                            val message = "Failed to post '${item.fullPath}': post returned status code ${response.statusLine.statusCode}"
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