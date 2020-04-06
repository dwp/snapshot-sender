package app.batch

import app.domain.EncryptedStream
import app.services.KeyService
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Component
@Qualifier("buffer")
class BufferingProcessor() : ItemProcessor<EncryptedStream, EncryptedStream> {

    override fun process(item: EncryptedStream): EncryptedStream? {
        val outputStream = ByteArrayOutputStream()
        item.inputStream.copyTo(outputStream)
        item.inputStream.close()
        item.inputStream = ByteArrayInputStream(outputStream.toByteArray())
        return item
    }

    companion object {
        val logger = DataworksLogger.getLogger(BufferingProcessor::class.toString())
    }
}
