package app.batch

import app.configuration.HttpClientProvider
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
@Profile("httpWriter")
class HttpWriter(private val httpClientProvider: HttpClientProvider): ItemWriter<InputStream> {

    override fun write(items: MutableList<out InputStream>) {
        items.forEach { item ->
            httpClientProvider.client().use {

                val post = HttpPost(nifiUrl).apply {
                    entity = InputStreamEntity(item, -1, ContentType.DEFAULT_BINARY)
                }

                it.execute(post).use {
                    logger.info("status '${it.statusLine.statusCode}'.")
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