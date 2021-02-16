package app.services.impl

import app.configuration.HttpClientProvider
import app.exceptions.DataKeyDecryptionException
import app.exceptions.DataKeyServiceUnavailableException
import app.services.KeyService
import app.utils.UUIDGenerator
import com.nhaarman.mockitokotlin2.firstValue
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayInputStream
import io.prometheus.client.Counter

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [HttpKeyService::class])
@EnableRetry
@TestPropertySource(properties = [
    "data.key.service.url=http://dummydks",
    "dks.retry.maxAttempts=5",
    "dks.retry.delay=5",
    "dks.retry.multiplier=1"
])
class HttpKeyServiceTest {

    @Autowired
    private lateinit var keyService: KeyService

    @MockBean
    private lateinit var httpClientProvider: HttpClientProvider

    @MockBean
    private lateinit var uuidGenerator: UUIDGenerator

    @MockBean(name = "keysDecryptedCounter")
    private lateinit var keysDecryptedCounter: Counter

    @MockBean(name = "keyDecryptionRetriesCounter")
    private lateinit var keyDecryptionRetriesCounter: Counter

    companion object {
        private var dksCorrelationId = 0

        private fun nextDksCorrelationId(): String {
            return "dks-id-${++dksCorrelationId}"
        }
    }

    @Before
    fun init() {
        keyService.clearCache()
        reset(this.httpClientProvider)
        reset(this.uuidGenerator)
        reset(keysDecryptedCounter)
        reset(keyDecryptionRetriesCounter)
    }

    @Test
    fun test_decrypt_key_will_call_server_and_decrypt() {
        val keysDecryptedCounterChild = mock<Counter.Child>()
        given(keysDecryptedCounter.labels(any())).willReturn(keysDecryptedCounterChild)

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
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)
        val dksCallId = nextDksCorrelationId()
        whenever(uuidGenerator.randomUUID()).thenReturn(dksCallId)

        val dataKeyResult = keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
        assertEquals("PLAINTEXT_DATAKEY", dataKeyResult)

        val argumentCaptor = ArgumentCaptor.forClass(HttpPost::class.java)
        verify(httpClient, times(1)).execute(argumentCaptor.capture())
        assertEquals("http://dummydks/datakey/actions/decrypt?keyId=123&correlationId=$dksCallId", argumentCaptor.firstValue.uri.toString())
        verify(keysDecryptedCounterChild, times(1)).inc(1.toDouble())
        verifyZeroInteractions(keyDecryptionRetriesCounter)
    }

    @Test
    fun test_decrypt_key_will_call_server_and_cache_result() {
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
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)
        val dksCallId = nextDksCorrelationId()
        whenever(uuidGenerator.randomUUID()).thenReturn(dksCallId)

        val dataKeyResult = keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
        assertEquals("PLAINTEXT_DATAKEY", dataKeyResult)

        keyService.decryptKey("123", "ENCRYPTED_KEY_ID")

        val argumentCaptor = ArgumentCaptor.forClass(HttpPost::class.java)
        verify(httpClient, times(1)).execute(argumentCaptor.capture())
        assertEquals("http://dummydks/datakey/actions/decrypt?keyId=123&correlationId=$dksCallId", argumentCaptor.firstValue.uri.toString())
    }

    @Test
    fun test_decrypt_key_with_bad_key_will_call_server_and_not_retry() {
        val statusLine = mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(400)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)
        val dksCallId = nextDksCorrelationId()
        whenever(uuidGenerator.randomUUID()).thenReturn(dksCallId)

        try {
            keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
            fail("Expected a DataKeyDecryptionException")
        } catch (ex: DataKeyDecryptionException) {
            assertEquals("Decrypting encryptedKey: 'ENCRYPTED_KEY_ID' with keyEncryptionKeyId: '123', dks_correlation_id: '$dksCallId' data key service returned status_code: '400'", ex.message)
            val argumentCaptor = ArgumentCaptor.forClass(HttpPost::class.java)
            verify(httpClient, times(1)).execute(argumentCaptor.capture())
            assertEquals("http://dummydks/datakey/actions/decrypt?keyId=123&correlationId=$dksCallId", argumentCaptor.firstValue.uri.toString())
        }
    }

    @Test
    fun test_decrypt_key_with_server_error_will_retry_with_max_calls() {
        val keyDecryptionRetriesCounterChild = mock<Counter.Child>()
        given(keyDecryptionRetriesCounter.labels(any())).willReturn(keyDecryptionRetriesCounterChild)

        val statusLine = mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(503)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)
        val dksCallId = nextDksCorrelationId()
        whenever(uuidGenerator.randomUUID()).thenReturn(dksCallId)

        try {
            keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
            fail("Expected a DataKeyServiceUnavailableException")
        } catch (ex: DataKeyServiceUnavailableException) {
            assertEquals("Decrypting encryptedKey: 'ENCRYPTED_KEY_ID' with keyEncryptionKeyId: '123', dks_correlation_id: '$dksCallId' data key service returned status_code: '503'", ex.message)
            val argumentCaptor = ArgumentCaptor.forClass(HttpPost::class.java)
            verify(httpClient, times(5)).execute(argumentCaptor.capture())
            assertEquals("http://dummydks/datakey/actions/decrypt?keyId=123&correlationId=$dksCallId", argumentCaptor.firstValue.uri.toString())
        }

        verify(keyDecryptionRetriesCounterChild, times(5)).inc(1.toDouble())
    }

    @Test
    fun test_decrypt_key_with_http_error_will_retry_until_max_calls() {
        val statusLine = mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        val httpResponse = mock(CloseableHttpResponse::class.java)
        given(httpResponse.statusLine).willReturn(statusLine)
        val httpClient = mock(CloseableHttpClient::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willThrow(RuntimeException("Boom"))
        given(httpClientProvider.client()).willReturn(httpClient)
        val dksCallId = nextDksCorrelationId()
        whenever(uuidGenerator.randomUUID()).thenReturn(dksCallId)

        try {
            keyService.decryptKey("123", "ENCRYPTED_KEY_ID")
            fail("Expected a DataKeyServiceUnavailableException")
        } catch (ex: DataKeyServiceUnavailableException) {
            assertEquals("Error contacting data key service: 'java.lang.RuntimeException: Boom', dks_correlation_id: '$dksCallId'", ex.message)
            val argumentCaptor = ArgumentCaptor.forClass(HttpPost::class.java)
            verify(httpClient, times(5)).execute(argumentCaptor.capture())
            assertEquals("http://dummydks/datakey/actions/decrypt?keyId=123&correlationId=$dksCallId", argumentCaptor.firstValue.uri.toString())
        }
    }

    @Test
    fun shouldRetryDecryptKeyUntilSuccessfulBeforeMaxCalls() {
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
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        given(httpClientProvider.client()).willReturn(httpClient)
        val dksCallId = nextDksCorrelationId()
        whenever(uuidGenerator.randomUUID()).thenReturn(dksCallId)

        keyService.decryptKey("123", "ENCRYPTED_KEY_ID")

        val argumentCaptor = ArgumentCaptor.forClass(HttpPost::class.java)
        verify(httpClient, times(3)).execute(argumentCaptor.capture())
        assertEquals("http://dummydks/datakey/actions/decrypt?keyId=123&correlationId=$dksCallId", argumentCaptor.firstValue.uri.toString())
    }
}
