package app.services

interface CipherService {
    fun decrypt(key: String, initializationVector: String, encrypted: String): String
}