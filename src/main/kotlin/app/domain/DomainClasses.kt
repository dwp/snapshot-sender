package app.domain

import java.io.InputStream

data class EncryptionMetadata (val initializationVector: String,
                               val datakeyEncryptionKeyId: String,
                               val cipherText: String,
                               var plaintext: String)

data class EncryptedStream(val inputStream: InputStream, val encryptionMetadata: EncryptionMetadata)
data class DataKeyResult(val dataKeyEncryptionKeyId: String, val plaintextDataKey: String, val ciphertextDataKey: String)