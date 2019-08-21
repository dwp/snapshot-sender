package app.batch

import app.domain.EncryptedStream
import app.services.KeyService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component

@Component
class DataKeyProcessor(val keyService: KeyService): ItemProcessor<EncryptedStream, EncryptedStream> {
    override fun process(item: EncryptedStream): EncryptedStream? {
        val encryptionMetadata = item.encryptionMetadata
        val plaintextKey = keyService.decryptKey(encryptionMetadata.datakeyEncryptionKeyId, encryptionMetadata.cipherText)
        encryptionMetadata.plaintext = plaintextKey
        return item
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DataKeyProcessor::class.toString())
    }

}