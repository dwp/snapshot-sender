package app.services.impl

import app.configuration.HttpClientProvider
import app.services.SuccessService
import com.nhaarman.mockitokotlin2.*
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.net.SocketTimeoutException
import java.net.URI

@RunWith(SpringRunner::class)
@EnableRetry
@SpringBootTest(classes = [SuccessServiceImpl::class])
@TestPropertySource(properties = [
    "nifi.url=https://nifi:8091/dummy",
    "export.date=2019-01-01",
    "snapshot.type=incremental",
    "dynamodb.status.table.name=test_table"
])
class SuccessServiceImplTest {

    @Before
    fun before() {
        System.setProperty("environment", "test")
        System.setProperty("correlation_id", "123")
        reset(httpClientProvider)
    }

    @SpyBean
    @Autowired
    private lateinit var successService: SuccessService

    @MockBean
    private lateinit var httpClientProvider: HttpClientProvider

    @Test
    fun willPostPayloadWithAppropriateHeaders() {
        System.setProperty("topic_name", "db.core.toDo")

        val status = mock<StatusLine> {
            on { statusCode } doReturn 200
        }

        val response = mock<CloseableHttpResponse> {
            on { statusLine } doReturn status
        }

        val httpClient = mock<CloseableHttpClient> {
            on { execute(ArgumentMatchers.any()) } doReturn response
        }

        given(httpClientProvider.client()).willReturn(httpClient)
        successService.postSuccessIndicator()
        verify(successService, times(1)).postSuccessIndicator()
        verify(httpClientProvider, times(1)).client()
        val captor = argumentCaptor<HttpPost>()
        Mockito.verify(httpClient, times(1)).execute(captor.capture())

        val put = captor.firstValue
        assertEquals(put.uri, URI("https://nifi:8091/dummy"))

        val filenameHeader = put.getHeaders("filename")[0].value
        val environmentHeader = put.getHeaders("environment")[0].value
        val exportDateHeader = put.getHeaders("export_date")[0].value
        val databaseHeader = put.getHeaders("database")[0].value
        val collectionHeader = put.getHeaders("collection")[0].value
        val topicHeader = put.getHeaders("topic")[0].value
        val snapshotTypeHeader = put.getHeaders("snapshot_type")[0].value
        val statusTableNameHeader = put.getHeaders("status_table_name")[0].value
        val correlationIdHeader = put.getHeaders("correlation_id")[0].value

        assertEquals("_core_toDo_successful.gz", filenameHeader)
        assertEquals("aws/test", environmentHeader)
        assertEquals("2019-01-01", exportDateHeader)
        assertEquals("core", databaseHeader)
        assertEquals("toDo", collectionHeader)
        assertEquals("db.core.toDo", topicHeader)
        assertEquals("incremental", snapshotTypeHeader)
        assertEquals("db.core.toDo", topicHeader)
        assertEquals("incremental", snapshotTypeHeader)
        assertEquals("test_table", statusTableNameHeader)
        assertEquals("123", correlationIdHeader)

        val payload = put.entity.content.readBytes()
        assertEquals(20, payload.size)
    }

    @Test
    fun willPostPayloadWithAppropriateHeadersWhenNoPrefixForTopic() {
        System.setProperty("topic_name", "core.toDo")

        val status = mock<StatusLine> {
            on { statusCode } doReturn 200
        }

        val response = mock<CloseableHttpResponse> {
            on { statusLine } doReturn status
        }

        val httpClient = mock<CloseableHttpClient> {
            on { execute(ArgumentMatchers.any()) } doReturn response
        }

        given(httpClientProvider.client()).willReturn(httpClient)
        successService.postSuccessIndicator()
        verify(successService, times(1)).postSuccessIndicator()
        verify(httpClientProvider, times(1)).client()
        val captor = argumentCaptor<HttpPost>()
        Mockito.verify(httpClient, times(1)).execute(captor.capture())

        val put = captor.firstValue
        assertEquals(put.uri, URI("https://nifi:8091/dummy"))

        val filenameHeader = put.getHeaders("filename")[0].value
        val environmentHeader = put.getHeaders("environment")[0].value
        val exportDateHeader = put.getHeaders("export_date")[0].value
        val databaseHeader = put.getHeaders("database")[0].value
        val collectionHeader = put.getHeaders("collection")[0].value
        val topicHeader = put.getHeaders("topic")[0].value
        val snapshotTypeHeader = put.getHeaders("snapshot_type")[0].value
        val statusTableNameHeader = put.getHeaders("status_table_name")[0].value
        val correlationIdHeader = put.getHeaders("correlation_id")[0].value

        assertEquals("_core_toDo_successful.gz", filenameHeader)
        assertEquals("aws/test", environmentHeader)
        assertEquals("2019-01-01", exportDateHeader)
        assertEquals("core", databaseHeader)
        assertEquals("toDo", collectionHeader)
        assertEquals("core.toDo", topicHeader)
        assertEquals("incremental", snapshotTypeHeader)
        assertEquals("test_table", statusTableNameHeader)
        assertEquals("123", correlationIdHeader)

        val payload = put.entity.content.readBytes()
        assertEquals(20, payload.size)
    }

    @Test
    fun testRetriesOnNotOkResponseUntilSuccessful() {
        System.setProperty("topic_name", "db.core.toDo")

        val statusOk = mock<StatusLine> {
            on { statusCode } doReturn 200
        }

        val statusNotOk = mock<StatusLine> {
            on { statusCode } doReturn 503
        }

        val okResponse = mock<CloseableHttpResponse> {
            on { statusLine } doReturn statusOk
        }

        val notOkResponse = mock<CloseableHttpResponse> {
            on { statusLine } doReturn statusNotOk
        }

        val successfulClient = mock<CloseableHttpClient> {
            on { execute(ArgumentMatchers.any()) } doReturn okResponse
        }

        val failureClient = mock<CloseableHttpClient> {
            on { execute(ArgumentMatchers.any()) } doReturn notOkResponse
        }

        given(httpClientProvider.client()).willReturn(failureClient).willReturn(failureClient).willReturn(successfulClient)
        successService.postSuccessIndicator()
        verify(successService, times(3)).postSuccessIndicator()
    }

    @Test
    fun testRetriesOnTimeoutUntilSuccessful() {
        System.setProperty("topic_name", "db.core.toDo")

        val statusOk = mock<StatusLine> {
            on { statusCode } doReturn 200
        }


        val okResponse = mock<CloseableHttpResponse> {
            on { statusLine } doReturn statusOk
        }

        val successfulClient = mock<CloseableHttpClient> {
            on { execute(ArgumentMatchers.any()) } doReturn okResponse
        }

        val failureClient = mock<CloseableHttpClient> {
            on { execute(ArgumentMatchers.any()) } doThrow SocketTimeoutException("TIMEOUT")
        }

        given(httpClientProvider.client()).willReturn(failureClient).willReturn(successfulClient)
        successService.postSuccessIndicator()
        verify(successService, times(2)).postSuccessIndicator()
    }
}
