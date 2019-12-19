package app.batch

import app.domain.DecryptedStream
import app.domain.EncryptedStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component
import java.security.Key
import java.security.Security
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
        val keySpec: Key = SecretKeySpec(Base64.getDecoder().decode(dataKey), "AES")
        val cipher = Cipher.getInstance(cipherAlgorithm, "BC").apply {
            init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(Base64.getDecoder().decode(iv)))
        }

        return DecryptedStream(CipherInputStream(inputStream, cipher),
            item.fileName.replace(decryptionRegEx, ""), item.fullPath)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DecryptionProcessor::class.toString())
    }

}
