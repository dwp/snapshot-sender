package app.domain

import java.io.InputStream

data class EncryptionMetadata(val initializationVector: String,
                              val datakeyEncryptionKeyId: String,
                              val cipherText: String,
                              var plaintext: String)

data class EncryptedStream(var inputStream: InputStream, val fileName: String, val fullPath: String, val encryptionMetadata: EncryptionMetadata)
data class DataKeyResult(val dataKeyEncryptionKeyId: String, val plaintextDataKey: String, val ciphertextDataKey: String)
data class DecryptedStream(val inputStream: InputStream, val fileName: String, val fullPath: String)
