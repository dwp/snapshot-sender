package app.domain

import java.io.InputStream

data class EncryptionMetadata(val initializationVector: String,
                              val datakeyEncryptionKeyId: String,
                              val cipherText: String,
                              var plaintext: String)

data class EncryptedStream(var contents: ByteArray, val fileName: String, val fullPath: String, val encryptionMetadata: EncryptionMetadata) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedStream

        if (!contents.contentEquals(other.contents)) return false
        if (fileName != other.fileName) return false
        if (fullPath != other.fullPath) return false
        if (encryptionMetadata != other.encryptionMetadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = contents.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fullPath.hashCode()
        result = 31 * result + encryptionMetadata.hashCode()
        return result
    }
}

data class DataKeyResult(val dataKeyEncryptionKeyId: String, val plaintextDataKey: String, val ciphertextDataKey: String)
data class DecryptedStream(val inputStream: InputStream, val fileName: String, val fullPath: String)

data class NifiHeaders(val filename: String, val database: String, val collection: String)
