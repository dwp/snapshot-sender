package app.batch

import app.TestUtils.Companion.once
import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.BlockedTopicException
import app.exceptions.MetadataException
import app.exceptions.WriterException
import app.services.ExportStatusService
import app.services.SuccessService
import app.utils.FilterBlockedTopicsUtils
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import com.nhaarman.mockitokotlin2.*

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.apache.http.Header
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayInputStream

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "httpWriter")
@SpringBootTest(classes = [HttpWriter::class])
@TestPropertySource(properties = [
    "data.key.service.url=datakey.service:8090",
    "nifi.url=nifi:8091/dummy",
    "export.date=2019-01-01",
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output",
    "snapshot.type=incremental",
    "dynamodb.status.table.name=test_table"
])
class HttpWriterTest {

    @SpyBean
    @Autowired
    private lateinit var httpWriter: HttpWriter

    @MockBean
    private lateinit var mockS3StatusFileWriter: S3StatusFileWriter

    @MockBean
    private lateinit var exportStatusService: ExportStatusService

    @MockBean
    private lateinit var httpClientProvider: HttpClientProvider

    @MockBean
    private lateinit var successService: SuccessService

    @MockBean
    private lateinit var filterBlockedTopicsUtils: FilterBlockedTopicsUtils

    val mockAppender: Appender<ILoggingEvent> = mock()

    val byteArray = "hello, world".toByteArray()
    val s3Path = "exporter-output/job01" //should match the test properties above

    @Before
    fun before() {
        System.setProperty("environment", "test")
        System.setProperty("correlation_id", "123")
        val root = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.addAppender(mockAppender)
        Mockito.reset(mockAppender)
        Mockito.reset(httpClientProvider)
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file() {
        //given
        val filename = "db.core.addressDeclaration-001-002-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))

        //when
        httpWriter.write(mutableListOf(decryptedStream))

        //then
        val httpCaptor = argumentCaptor<HttpPost>()
        verify(httpClient, once()).execute(httpCaptor.capture())
        assertEquals("Content-Type: application/octet-stream", httpCaptor.firstValue.entity.contentType.toString())
        assertEquals(9, httpCaptor.firstValue.allHeaders.size)
        assertEquals("filename: ${filename.replace("txt", "json")}", httpCaptor.firstValue.allHeaders[0].toString())
        assertEquals("environment: aws/test", httpCaptor.firstValue.allHeaders[1].toString())
        assertEquals("database: core", httpCaptor.firstValue.allHeaders[3].toString())
        assertEquals("collection: addressDeclaration", httpCaptor.firstValue.allHeaders[4].toString())
        assertEquals("snapshot_type: incremental", httpCaptor.firstValue.allHeaders[5].toString())
        assertEquals("topic: db.core.addressDeclaration", httpCaptor.firstValue.allHeaders[6].toString())
        assertEquals("status_table_name: test_table", httpCaptor.firstValue.allHeaders[7].toString())
        assertEquals("correlation_id: 123", httpCaptor.firstValue.allHeaders[8].toString())

        verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, Mockito.times(4)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("""Writing items to S3", "number_of_items":"1"""", formattedMessages[0])
        assertEquals("""Checking item to  write", "file_name":"db.core.addressDeclaration-001-002-000001.txt.gz", "full_path":"exporter-output\/job01\/db.core.addressDeclaration-001-002-000001.txt.gz"""", formattedMessages[1])
        assertEquals("""Posting file name to collection", "database":"core", "collection":"addressDeclaration", "topic":"db.core.addressDeclaration", "file_name":"db.core.addressDeclaration-001-002-000001.txt.gz", "full_path":"exporter-output\/job01\/db.core.addressDeclaration-001-002-000001.txt.gz", "nifi_url":"nifi:8091\/dummy", "filename_header":"db.core.addressDeclaration-001-002-000001.json.gz", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table"""", formattedMessages[2])
        assertEquals("""Successfully posted file", "database":"core", "collection":"addressDeclaration", "topic":"db.core.addressDeclaration", "file_name":"exporter-output\/job01\/db.core.addressDeclaration-001-002-000001.txt.gz", "response":"200", "nifi_url":"nifi:8091\/dummy", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table"""", formattedMessages[3])
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_without_prefix() {
        //given
        val filename = "core.addressDeclaration-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))

        //when
        httpWriter.write(mutableListOf(decryptedStream))

        //then
        val httpCaptor = argumentCaptor<HttpPost>()
        verify(httpClient, once()).execute(httpCaptor.capture())
        assertEquals("Content-Type: application/octet-stream", httpCaptor.firstValue.entity.contentType.toString())
        assertEquals(9, httpCaptor.firstValue.allHeaders.size)
        assertEquals("filename: core.addressDeclaration-045-050-000001.json.gz", httpCaptor.firstValue.allHeaders[0].toString())
        assertEquals("environment: aws/test", httpCaptor.firstValue.allHeaders[1].toString())
        assertEquals("database: core", httpCaptor.firstValue.allHeaders[3].toString())
        assertEquals("collection: addressDeclaration", httpCaptor.firstValue.allHeaders[4].toString())
        assertEquals("snapshot_type: incremental", httpCaptor.firstValue.allHeaders[5].toString())
        assertEquals("topic: core.addressDeclaration", httpCaptor.firstValue.allHeaders[6].toString())
        assertEquals("status_table_name: test_table", httpCaptor.firstValue.allHeaders[7].toString())
        assertEquals("correlation_id: 123", httpCaptor.firstValue.allHeaders[8].toString())

        verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, Mockito.times(4)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("""Writing items to S3", "number_of_items":"1"""", formattedMessages[0])
        assertEquals("""Checking item to  write", "file_name":"core.addressDeclaration-045-050-000001.txt.gz", "full_path":"exporter-output\/job01\/core.addressDeclaration-045-050-000001.txt.gz"""", formattedMessages[1])
        assertEquals("""Posting file name to collection", "database":"core", "collection":"addressDeclaration", "topic":"core.addressDeclaration", "file_name":"core.addressDeclaration-045-050-000001.txt.gz", "full_path":"exporter-output\/job01\/core.addressDeclaration-045-050-000001.txt.gz", "nifi_url":"nifi:8091\/dummy", "filename_header":"core.addressDeclaration-045-050-000001.json.gz", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table"""", formattedMessages[2])
        assertEquals("""Successfully posted file", "database":"core", "collection":"addressDeclaration", "topic":"core.addressDeclaration", "file_name":"exporter-output\/job01\/core.addressDeclaration-045-050-000001.txt.gz", "response":"200", "nifi_url":"nifi:8091\/dummy", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table"""", formattedMessages[3])
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_embedded_hyphens_in_dbname() {
        val filename = "db.core-with-hyphen.addressDeclaration-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)

        argumentCaptor<HttpPost> {
            given(httpClient.execute(capture())).willReturn(httpResponse)
            val statusLine = Mockito.mock(StatusLine::class.java)
            given(statusLine.statusCode).willReturn(200)
            given(httpResponse.statusLine).willReturn(statusLine)
            httpWriter.write(mutableListOf(decryptedStream))
            assertEquals("Content-Type: application/octet-stream", firstValue.entity.contentType.toString())
            assertEquals(9, firstValue.allHeaders.size)
            assertEquals("filename: ${filename.replace("txt", "json")}", firstValue.allHeaders[0].toString())
            assertEquals("environment: aws/test", firstValue.allHeaders[1].toString())
            assertEquals("database: core-with-hyphen", firstValue.allHeaders[3].toString())
            assertEquals("collection: addressDeclaration", firstValue.allHeaders[4].toString())
            assertEquals("snapshot_type: incremental", firstValue.allHeaders[5].toString())
            assertEquals("topic: db.core-with-hyphen.addressDeclaration", firstValue.allHeaders[6].toString())
            assertEquals("status_table_name: test_table", firstValue.allHeaders[7].toString())
            assertEquals("correlation_id: 123", firstValue.allHeaders[8].toString())


            verify(httpClient, once()).execute(any(HttpPost::class.java))
            verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)
        }
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_no_prefix() {
        val filename = "core-with-hyphen.addressDeclaration-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        argumentCaptor<HttpPost> {
            httpWriter.write(mutableListOf(decryptedStream))
            verify(httpClient, once()).execute(capture())
            verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)
            assertEquals("Content-Type: application/octet-stream", firstValue.entity.contentType.toString())
            assertEquals(9, firstValue.allHeaders.size)
            assertEquals("filename: ${filename.replace("txt", "json")}", firstValue.allHeaders[0].toString())
            assertEquals("environment: aws/test", firstValue.allHeaders[1].toString())
            assertEquals("database: core-with-hyphen", firstValue.allHeaders[3].toString())
            assertEquals("collection: addressDeclaration", firstValue.allHeaders[4].toString())
            assertEquals("snapshot_type: incremental", firstValue.allHeaders[5].toString())
            assertEquals("topic: core-with-hyphen.addressDeclaration", firstValue.allHeaders[6].toString())
            assertEquals("status_table_name: test_table", firstValue.allHeaders[7].toString())
            assertEquals("correlation_id: 123", firstValue.allHeaders[8].toString())
        }
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_embedded_hyphens_in_collection() {
        val filename = "db.core-with-hyphen.address-declaration-has-hyphen-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        argumentCaptor<HttpPost> {
            httpWriter.write(mutableListOf(decryptedStream))
            verify(httpClient, once()).execute(capture())
            verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)
            assertEquals("Content-Type: application/octet-stream", firstValue.entity.contentType.toString())
            assertEquals(9, firstValue.allHeaders.size)
            assertEquals("filename: ${filename.replace("txt", "json")}", firstValue.allHeaders[0].toString())
            assertEquals("environment: aws/test", firstValue.allHeaders[1].toString())
            assertEquals("database: core-with-hyphen", firstValue.allHeaders[3].toString())
            assertEquals("collection: address-declaration-has-hyphen", firstValue.allHeaders[4].toString())
            assertEquals("snapshot_type: incremental", firstValue.allHeaders[5].toString())
            assertEquals("topic: db.core-with-hyphen.address-declaration-has-hyphen", firstValue.allHeaders[6].toString())
            assertEquals("status_table_name: test_table", firstValue.allHeaders[7].toString())
            assertEquals("correlation_id: 123", firstValue.allHeaders[8].toString())
        }
    }

    @Test
    fun test_will_raise_error_when_file_cannot_be_sent() {
        val filename = "db.a.b-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(400)
        given(httpResponse.statusLine).willReturn((statusLine))

        val header = Mockito.mock(Header::class.java)
        given(header.name).willReturn("HEADER_NAME")
        given(header.value).willReturn("HEADER_VALUE")
        given(httpResponse.allHeaders).willReturn(arrayOf(header))
        shouldThrow<WriterException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, Mockito.times(4)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("""Writing items to S3", "number_of_items":"1"""", formattedMessages[0])
        assertEquals("""Checking item to  write", "file_name":"db.a.b-045-050-000001.txt.gz", "full_path":"exporter-output\/job01\/db.a.b-045-050-000001.txt.gz"""", formattedMessages[1])
        assertEquals("""Posting file name to collection", "database":"a", "collection":"b", "topic":"db.a.b", "file_name":"db.a.b-045-050-000001.txt.gz", "full_path":"exporter-output\/job01\/db.a.b-045-050-000001.txt.gz", "nifi_url":"nifi:8091\/dummy", "filename_header":"db.a.b-045-050-000001.json.gz", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table"""", formattedMessages[2])
        assertEquals("""Failed to post the provided item", "file_name":"exporter-output\/job01\/db.a.b-045-050-000001.txt.gz", "response":"400", "nifi_url":"nifi:8091\/dummy", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table", "HEADER_NAME":"HEADER_VALUE"""", formattedMessages[3])
    }

    @Test
    fun test_will_raise_metadata_error_when_filename_is_bad() {
        val filename = "dbcoreaddressDeclaration-000001"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        try {
            httpWriter.write(mutableListOf(decryptedStream))
            fail("Expected MetadataException")
        }
        catch (ex: MetadataException) {
            assertEquals("""Rejecting 'dbcoreaddressDeclaration-000001': does not match '^(?:\w+\.)?(?<database>[\w-]+)\.(?<collection>[\w-]+)-\d{3}-\d{3}-\d+\.\w+\.\w+${'$'}'""", ex.message)
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
    }

    @Test(expected = MetadataException::class)
    fun test_will_raise_metatdata_error_when_filename_has_no_dash_before_number() {
        val filename = "db.core.address-045-05001.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        httpWriter.write(mutableListOf(decryptedStream))
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
    }

    @Test
    fun willIncrementFilesSentCountOnSuccessfulPost() {
        val filename = "db.database.collection-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        val okStatusLine = mock<StatusLine> {
            on { statusCode } doReturn 200
        }

        val response = mock<CloseableHttpResponse> {
            on { statusLine } doReturn okStatusLine
        }

        val httpClient = mock<CloseableHttpClient> {
            on { execute(any()) } doReturn response
        }

        given(httpClientProvider.client()).willReturn(httpClient)
        httpWriter.write(mutableListOf(decryptedStream))
        verify(exportStatusService, once()).incrementSentCount(filename)
    }

    @Test
    fun willNotIncrementFilesSentCountOnFailedPost() {
        val filename = "db.database.collection-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        val okStatusLine = mock<StatusLine> {
            on { statusCode } doReturn 503
        }

        val response = mock<CloseableHttpResponse> {
            on { statusLine } doReturn okStatusLine
            on { allHeaders } doReturn arrayOf(mock())
        }

        val httpClient = mock<CloseableHttpClient> {
            on { execute(any()) } doReturn response
        }

        given(httpClientProvider.client()).willReturn(httpClient)

        val exception = shouldThrow<WriterException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        exception.message shouldBe "Failed to post 'exporter-output/job01/db.database.collection-045-050-000001.txt.gz': post returned status code 503"
        verifyZeroInteractions(exportStatusService)
    }

    @Test
    fun shouldThrowMetadataExceptionWhenFileNameDoesNotMatchRegex() {
        val filename = "bad_filename-045-050-000001"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        shouldThrow<MetadataException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        verifyZeroInteractions(exportStatusService)
    }

    @Test
    fun shouldThrowMetadataExceptionWhenFilenameDoesNotContainAHyphen() {
        val filename = "db.type.nonum.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        shouldThrow<MetadataException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        verifyZeroInteractions(exportStatusService)
    }

    @Test
    fun shouldThrowBlockedTopicExceptionWhenTopicNameIsInBlockedList() {

        val filename = "db.crypto.unencrypted-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")


        whenever(filterBlockedTopicsUtils.checkIfTopicIsBlocked("db.crypto.unencrypted", decryptedStream.fullPath)).doThrow(BlockedTopicException("Provided topic is blocked so cannot be processed: 'db.crypto.unencrypted'"))

        val exception = shouldThrow<BlockedTopicException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }

        exception.message shouldBe "Provided topic is blocked so cannot be processed: 'db.crypto.unencrypted'"

        verifyZeroInteractions(exportStatusService)
    }
}
