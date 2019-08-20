package app.services

import app.exceptions.DataKeyDecryptionException
import app.exceptions.DataKeyServiceUnavailableException

interface KeyService {

    @Throws(DataKeyServiceUnavailableException::class, DataKeyDecryptionException::class)
    fun decryptKey(encryptionKeyId: String, encryptedKey: String): String
}