package app.services.impl

import app.configuration.*
import app.domain.*
import app.exceptions.*
import app.services.*
import com.google.gson.*
import org.apache.http.client.methods.*
import org.apache.http.entity.*
import org.apache.http.util.*
import org.slf4j.*
import org.springframework.beans.factory.annotation.*
import org.springframework.retry.annotation.*
import org.springframework.stereotype.*
import java.io.*
import java.net.*

@Service
class HttpKeyService(private val httpClientProvider: HttpClientProvider) : KeyService {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpKeyService::class.toString())
        const val maxAttempts = 10
        const val initialBackoffMillis = 1000L
        const val backoffMultiplier = 2.0
    }

    @Override
    @Retryable(value = [DataKeyServiceUnavailableException::class],
        maxAttempts = maxAttempts,
        backoff = Backoff(delay = initialBackoffMillis, multiplier = backoffMultiplier))
    override fun decryptKey(encryptionKeyId: String, encryptedKey: String): String {
        logger.info("Decrypting encryptedKey: '$encryptedKey', keyEncryptionKeyId: '$encryptionKeyId'.")
        val cacheKey = "$encryptedKey/$encryptionKeyId"
        try {
            return if (decryptedKeyCache.containsKey(cacheKey)) {
                decryptedKeyCache[cacheKey]!!
            } else {
                httpClientProvider.client().use { client ->
                    val url = """$dataKeyServiceUrl/datakey/actions/decrypt?keyId=${URLEncoder.encode(encryptionKeyId, "US-ASCII")}"""
                    logger.info("url: '$url'.")
                    val httpPost = HttpPost(url)
                    httpPost.entity = StringEntity(encryptedKey, ContentType.TEXT_PLAIN)
                    client.execute(httpPost).use { response ->
                        return when {
                            response.statusLine.statusCode == 200 -> {
                                val entity = response.entity
                                val text = BufferedReader(InputStreamReader(response.entity.content)).use(BufferedReader::readText)
                                EntityUtils.consume(entity)
                                val dataKeyResult = Gson().fromJson(text, DataKeyResult::class.java)
                                decryptedKeyCache[cacheKey] = dataKeyResult.plaintextDataKey
                                dataKeyResult.plaintextDataKey
                            }
                            response.statusLine.statusCode == 400 ->
                                throw DataKeyDecryptionException(
                                    "Decrypting encryptedKey: '$encryptedKey' with keyEncryptionKeyId: '$encryptionKeyId' data key service returned status code '${response.statusLine.statusCode}'".trimMargin())
                            else ->
                                throw DataKeyServiceUnavailableException(
                                    "Decrypting encryptedKey: '$encryptedKey' with keyEncryptionKeyId: '$encryptionKeyId' data key service returned status code '${response.statusLine.statusCode}'".trimMargin())
                        }

                    }

                }
            }
        } catch (ex: DataKeyDecryptionException) {
            throw ex
        } catch (ex: DataKeyServiceUnavailableException) {
            throw ex
        } catch (ex: Exception) {
            throw DataKeyServiceUnavailableException("Error contacting data key service: ${ex.javaClass.name}: $ex.message")
        }
    }

    fun clearCache() {
        this.decryptedKeyCache = mutableMapOf()
    }

    private var decryptedKeyCache = mutableMapOf<String, String>()

    @Value("\${data.key.service.url}")
    private lateinit var dataKeyServiceUrl: String
}
