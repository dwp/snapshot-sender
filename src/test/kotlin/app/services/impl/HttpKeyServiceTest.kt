package app.services.impl

import app.configuration.HttpClientProvider
import app.exceptions.DataKeyDecryptionException
import app.exceptions.DataKeyServiceUnavailableException
import org.apache.http.HttpEntity
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayInputStream

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "outputToConsole")
@SpringBootTest
@TestPropertySource(properties = [
    "data.key.service.url=dummy.com:8090"
])
class HttpKeyServiceTest {

    @Before
    fun init() {
        this.keyService.clearCache()
        reset(this.httpClientProvider)
    }

    @Test
    fun testDecryptKey_WillCallServer_AndDecrypt() {
        val responseBody = """
            |{
            |  "dataKeyEncryptionKeyId": "DATAKEY_ENCRYPTION_KEY_ID",
            |  "plaintextDataKey": "PLAINTEXT_DATAKEY"
            |}
        """.trimMargin()

        val byteArrayInputStream = ByteArrayInputStream(responseBody.toByteArray())
        val statusLine = mock(StatusLine::class.java)
        val entity = mock(HttpEntity::class.java)
        given(entity.content).willReturn(byteArrayInputStream)
        given(statusLine.statusCode).willReturn(200)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        given(httpResponse.entity).willReturn(entity)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)

        val dataKeyResult = keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
        assertEquals("PLAINTEXT_DATAKEY", dataKeyResult)
    }

    @Test
    fun testDecryptKey_WillCallServer_AndCacheResult() {
        val responseBody = """
            |{
            |  "dataKeyEncryptionKeyId": "DATAKEY_ENCRYPTION_KEY_ID",
            |  "plaintextDataKey": "PLAINTEXT_DATAKEY"
            |}
        """.trimMargin()

        val byteArrayInputStream = ByteArrayInputStream(responseBody.toByteArray())
        val statusLine = mock(StatusLine::class.java)
        val entity = mock(HttpEntity::class.java)
        given(entity.content).willReturn(byteArrayInputStream)
        given(statusLine.statusCode).willReturn(200)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        given(httpResponse.entity).willReturn(entity)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)

        val dataKeyResult = keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
        assertEquals("PLAINTEXT_DATAKEY", dataKeyResult)

        keyService.decryptKey("123", "ENCRYPTED_KEY_ID")

        verify(httpClient, times(1)).execute(ArgumentMatchers.any(HttpPost::class.java))
    }

    @Test
    fun testDecryptKey_WithBadKey_WillCallServerAndNotRetry() {
        val statusLine = mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(400)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)

        try {
            keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
            fail("Expected a DataKeyDecryptionException")
        }
        catch (ex: DataKeyDecryptionException) {
            assertEquals("Decrypting encryptedKey: 'ENCRYPTED_KEY_ID' with keyEncryptionKeyId: '123' data key service returned status code '400'", ex.message)
            verify(httpClient, times(1)).execute(ArgumentMatchers.any(HttpPost::class.java))
        }
    }

    @Test
    fun testDecryptKey_WithServerError_WillRetry_WithMaxCalls() {

        val statusLine = mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(503)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)

        try {
            keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
            fail("Expected a DataKeyServiceUnavailableException")
        }
        catch (ex: DataKeyServiceUnavailableException) {
            assertEquals("Decrypting encryptedKey: 'ENCRYPTED_KEY_ID' with keyEncryptionKeyId: '123' data key service returned status code '503'", ex.message)
            verify(httpClient, times(HttpKeyService.maxAttempts)).execute(ArgumentMatchers.any(HttpPost::class.java))
        }
    }

    @Test
    fun testDecryptKey_WithHttpError_WillRetry_WithMaxCalls() {
        val statusLine = mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willThrow(RuntimeException("Boom"))
        given(httpClientProvider.client()).willReturn(httpClient)

        try {
            keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
            fail("Expected a DataKeyServiceUnavailableException")
        }
        catch (ex: DataKeyServiceUnavailableException) {
            assertEquals("Error contacting data key service: java.lang.RuntimeException: Boom", ex.message)
            verify(httpClient, times(HttpKeyService.maxAttempts)).execute(ArgumentMatchers.any(HttpPost::class.java))
        }
    }

    @Test
    fun testDecryptKey_WillRetry_UntilSuccessfulBeforeMaxCalls() {
        val responseBody = """
            |{
            |  "dataKeyEncryptionKeyId": "DATAKEY_ENCRYPTION_KEY_ID",
            |  "plaintextDataKey": "PLAINTEXT_DATAKEY"
            |}
        """.trimMargin()

        val byteArrayInputStream = ByteArrayInputStream(responseBody.toByteArray())
        val statusLine = mock(StatusLine::class.java)
        val entity = mock(HttpEntity::class.java)
        given(entity.content).willReturn(byteArrayInputStream)
        given(statusLine.statusCode).willReturn(503, 503, 200)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        given(httpResponse.entity).willReturn(entity)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)

        keyService.decryptKey("123", "ENCRYPTED_KEY_ID")

        verify(httpClient, times(3)).execute(ArgumentMatchers.any(HttpPost::class.java))
    }

    @Autowired
    private lateinit var keyService: HttpKeyService

    @Autowired
    private lateinit var httpClientProvider: HttpClientProvider

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpKeyService::class.toString())
    }
}