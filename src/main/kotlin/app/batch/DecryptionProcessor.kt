package app.batch

import app.domain.*
import org.bouncycastle.jce.provider.*
import org.slf4j.*
import org.springframework.batch.item.*
import org.springframework.stereotype.*
import java.security.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.*

@Component
class DecryptionProcessor : ItemProcessor<EncryptedStream, DecryptedStream> {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private val decryptionRegEx = Regex("""\.enc$""")
    private val cipherAlgorithm = "AES/CTR/NoPadding"

    override fun process(item: EncryptedStream): DecryptedStream? {
        logger.info("Processing '${item.fullPath}'")
        val dataKey = item.encryptionMetadata.plaintext
        val iv = item.encryptionMetadata.initializationVector
        val inputStream = item.inputStream
        val keySpec: Key = SecretKeySpec(dataKey.toByteArray(), "AES")
        val cipher = Cipher.getInstance(cipherAlgorithm, "BC").apply {
            init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(Base64.getDecoder().decode(iv)))
        }

        return DecryptedStream(CipherInputStream(Base64.getDecoder().wrap(inputStream), cipher),
            item.fileName.replace(decryptionRegEx, ""), item.fullPath)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DecryptionProcessor::class.toString())
    }

}