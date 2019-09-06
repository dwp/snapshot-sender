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
class HttpWriter(private val httpClientProvider: HttpClientProvider): ItemWriter<DecryptedStream> {

    val filenameRe = Regex("""^(\w+)\.(?:\w|-)+\.((?:\w|-)+)""")

    override fun write(items: MutableList<out DecryptedStream>) {
        logger.info("Writing: '${items.size}' items.")
        items.forEach { item ->
            logger.info("Checking: '$item'.")
            val match = filenameRe.find(item.filename)
            if (match != null) {
                logger.info("Writing: '$item'.")
                //Convert filename "db.core.addressDeclaration-000001.txt.bz2" to "db.core.addressDeclaration"
                val partialCollection = match.groupValues[2].replace(Regex("""-\\d+$"""), "")
                val fullCollection = match.groupValues[0] + "." + match.groupValues[1] + "." + partialCollection
                logger.info("Found collection: '${fullCollection}' from filename '${item.filename}'..")
                httpClientProvider.client().use {
                    val post = HttpPost(nifiUrl).apply {
                        entity = InputStreamEntity(item.inputStream, -1, ContentType.DEFAULT_BINARY)
                        setHeader("filename", item.filename)
                        setHeader("collection", fullCollection)
                    }

                    it.execute(post).use {response ->
                        when (response.statusLine.statusCode) {
                            200 -> {
                                logger.info("Successfully posted '${item.filename}', response '${response.statusLine.statusCode}'.")
                            }
                            else -> {
                                logger.error("""Failed to process '${item.filename}',
                                    |response '${response.statusLine.statusCode}'.""".trimMargin())
                                throw WriterException("""
                                Failed to write '${item.filename}', post returned status code ${response.statusLine.statusCode}.
                            """.trimIndent())
                            }
                        }
                    }
                }
            }
            else {
                logger.error("Rejecting: '$item'.")
                throw MetadataException("""Filename not in expected format, 
                    |cannot parse collection name: 
                    |'${item}' does not match '$filenameRe'.""".trimMargin())
            }
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpWriter::class.toString())
    }

    @Value("\${nifi.url}")
    private lateinit var nifiUrl: String
}