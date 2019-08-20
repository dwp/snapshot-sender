package app.batch

import app.services.impl.HttpKeyService
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.HttpClients
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
@Profile("httpWriter")
class HttpWriter: ItemWriter<InputStream> {
    override fun write(items: MutableList<out InputStream>) {
        val url = """http://localhost:3000/"""
        HttpKeyService.logger.info("url: '$url'.")

        items.forEach { item ->
            HttpClients.createDefault().use {

                val post = HttpPost(url).apply {
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

}