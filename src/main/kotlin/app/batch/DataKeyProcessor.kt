package app.batch

import app.domain.EncryptedStream
import app.services.KeyService
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
@Qualifier("datakey")
class DataKeyProcessor(val keyService: KeyService) : ItemProcessor<EncryptedStream, EncryptedStream> {

    override fun process(item: EncryptedStream): EncryptedStream? {
        logger.info("Performing DataKey processing", "file_name" to item.fullPath)
        val encryptionMetadata = item.encryptionMetadata
        val plaintextKey = keyService.decryptKey(encryptionMetadata.datakeyEncryptionKeyId, encryptionMetadata.cipherText)
        encryptionMetadata.plaintext = plaintextKey
        return item
    }

    companion object {
        val logger = DataworksLogger.getLogger(DataKeyProcessor::class.toString())
    }
}
