package app.batch

import app.domain.EncryptedStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component
import java.io.InputStream
import java.security.Key
import java.security.Security
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class DecryptionProcessor: ItemProcessor<EncryptedStream, InputStream> {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    override fun process(item: EncryptedStream): InputStream? {
        val dataKey = item.encryptionMetadata.plaintext
        val iv = item.encryptionMetadata.initializationVector
        val inputStream = item.inputStream
        val keySpec: Key = SecretKeySpec(dataKey.toByteArray(), "AES")
        val cipher = Cipher.getInstance(cipherAlgorithm, "BC").apply {
            init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(Base64.getDecoder().decode(iv)))
        }
        return CipherInputStream(Base64.getDecoder().wrap(inputStream), cipher)
    }

    private val cipherAlgorithm = "AES/CTR/NoPadding"

}