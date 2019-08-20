package app.services.impl

import app.configuration.HttpClientProvider
import app.domain.DataKeyResult
import app.exceptions.DataKeyDecryptionException
import app.exceptions.DataKeyServiceUnavailableException
import app.services.KeyService
import com.google.gson.Gson
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLEncoder

@Service
class HttpKeyService(private val httpClientProvider: HttpClientProvider) : KeyService {

    override fun decryptKey(encryptionKeyId: String, encryptedKey: String): String {
        logger.info("Decrypting encryptedKey: '$encryptedKey', keyEncryptionKeyId: '$encryptionKeyId'.")
        val cacheKey = "$encryptedKey/$encryptionKeyId"
        return if (decryptedKeyCache.containsKey(cacheKey)) {
            decryptedKeyCache[cacheKey]!!
        }
        else {
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
                                    """Decrypting encryptedKey: '$encryptedKey' with 
                            |keyEncryptionKeyId: '$encryptionKeyId'
                            |data key service returned status code '${response.statusLine.statusCode}'""".trimMargin())
                        else ->
                            throw DataKeyServiceUnavailableException(
                                    """Decrypting encryptedKey: '$encryptedKey' with 
                            |keyEncryptionKeyId: '$encryptionKeyId'
                            |data key service returned status code '${response.statusLine.statusCode}'""".trimMargin())
                    }

                }

            }
        }
    }

    fun clearCache() {
        this.decryptedKeyCache = mutableMapOf()
    }

    private var decryptedKeyCache = mutableMapOf<String, String>()

    @Value("\${data.key.service.url}")
    private lateinit var dataKeyServiceUrl: String

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpKeyService::class.toString())
    }

}
