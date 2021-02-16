package app.batch

import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.BlockedTopicException
import app.exceptions.MetadataException
import app.exceptions.WriterException
import app.services.ExportStatusService
import app.services.SuccessService
import app.utils.FilterBlockedTopicsUtils
import app.utils.NiFiUtility
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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayInputStream
import io.prometheus.client.Counter

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "httpWriter")
@SpringBootTest(classes = [HttpWriter::class, NiFiUtility::class])
@TestPropertySource(properties = [
    "data.key.service.url=datakey.service:8090",
    "nifi.url=nifi:8091/dummy",
    "export.date=2019-01-01",
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output",
    "snapshot.type=incremental",
    "shutdown.flag=true",
    "dynamodb.status.table.name=test_table",
    "pushgateway.host=pushgateway",
])
class HttpWriterTest {

    @SpyBean
    @Autowired
    private lateinit var httpWriter: HttpWriter

    @Autowired
    private lateinit var niFiUtility: NiFiUtility

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

    @MockBean(name = "successPostFileCounter")
    private lateinit var successPostFileCounter: Counter

    @MockBean(name = "retriedPostFilesCounter")
    private lateinit var retriedPostFilesCounter: Counter

    @MockBean(name = "rejectedFilesCounter")
    private lateinit var rejectedFilesCounter: Counter

    @MockBean(name = "blockedTopicFileCounter")
    private lateinit var blockedTopicFileCounter: Counter

    val mockAppender: Appender<ILoggingEvent> = mock()

    val byteArray = "hello, world".toByteArray()
    val s3Path = "exporter-output/job01" //should match the test properties above

    @Before
    fun before() {
        System.setProperty("environment", "test")
        System.setProperty("correlation_id", "123")
        System.setProperty("topic_name", "db.database.collection")
        val root = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.addAppender(mockAppender)
        reset(mockAppender)
        reset(httpClientProvider)
        reset(successPostFileCounter)
        reset(retriedPostFilesCounter)
        reset(rejectedFilesCounter)
        reset(blockedTopicFileCounter)
    }

    @Test
    fun testWriteToNifiIncrementsSuccessCounter() {
        val filename = "db.core.addressDeclaration-001-002-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))

        val successPostFileCounterChild = mock<Counter.Child>()
        given(successPostFileCounter.labels(any())).willReturn(successPostFileCounterChild)

        httpWriter.write(mutableListOf(decryptedStream))

        verify(successPostFileCounterChild, times(1)).inc()
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun testFailedSendIncrementsRetryCounter() {
        val filename = "db.a.b-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(400)
        given(httpResponse.statusLine).willReturn((statusLine))

        val retriedPostFilesCounterChild = mock<Counter.Child>()
        given(retriedPostFilesCounter.labels(any())).willReturn(retriedPostFilesCounterChild)

        val header = mock<Header>()
        given(header.name).willReturn("HEADER_NAME")
        given(header.value).willReturn("HEADER_VALUE")
        given(httpResponse.allHeaders).willReturn(arrayOf(header))
        shouldThrow<WriterException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        
        verify(retriedPostFilesCounterChild, times(1)).inc()
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file() {
        val filename = "db.core.addressDeclaration-001-002-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        httpWriter.write(mutableListOf(decryptedStream))
        argumentCaptor<HttpPost> {
            verifyPostHeaders(httpClient, filename)
        }

        verify(mockS3StatusFileWriter, times(1)).writeStatus(decryptedStream.fullPath)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, times(4)).doAppend(logCaptor.capture())
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
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        httpWriter.write(mutableListOf(decryptedStream))
        argumentCaptor<HttpPost>() {
            verifyPostHeaders(httpClient, filename, "database: core")
        }

        verify(mockS3StatusFileWriter, times(1)).writeStatus(decryptedStream.fullPath)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, times(4)).doAppend(logCaptor.capture())
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
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn(statusLine)
        httpWriter.write(mutableListOf(decryptedStream))
        argumentCaptor<HttpPost> {
            verifyPostHeaders(httpClient, filename, DATABASE_WITH_HYPHEN_HEADER)
        }
        verify(mockS3StatusFileWriter, times(1)).writeStatus(decryptedStream.fullPath)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_no_prefix() {
        val filename = "core-with-hyphen.addressDeclaration-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        httpWriter.write(mutableListOf(decryptedStream))
        argumentCaptor<HttpPost> {
            verifyPostHeaders(httpClient, filename, DATABASE_WITH_HYPHEN_HEADER)
        }
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_embedded_hyphens_in_collection() {
        val filename = "db.core-with-hyphen.address-declaration-has-hyphen-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        httpWriter.write(mutableListOf(decryptedStream))
        argumentCaptor<HttpPost> {
            verifyPostHeaders(httpClient, filename, DATABASE_WITH_HYPHEN_HEADER,"collection: address-declaration-has-hyphen")
        }
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun test_will_raise_error_when_file_cannot_be_sent() {
        val filename = "db.a.b-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = mock<CloseableHttpClient>()
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = mock<CloseableHttpResponse>()
        given(httpClient.execute(any())).willReturn(httpResponse)
        val statusLine = mock<StatusLine>()
        given(statusLine.statusCode).willReturn(400)
        given(httpResponse.statusLine).willReturn((statusLine))

        val header = mock<Header>()
        given(header.name).willReturn("HEADER_NAME")
        given(header.value).willReturn("HEADER_VALUE")
        given(httpResponse.allHeaders).willReturn(arrayOf(header))
        shouldThrow<WriterException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, times(4)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("""Writing items to S3", "number_of_items":"1"""", formattedMessages[0])
        assertEquals("""Checking item to  write", "file_name":"db.a.b-045-050-000001.txt.gz", "full_path":"exporter-output\/job01\/db.a.b-045-050-000001.txt.gz"""", formattedMessages[1])
        assertEquals("""Posting file name to collection", "database":"a", "collection":"b", "topic":"db.a.b", "file_name":"db.a.b-045-050-000001.txt.gz", "full_path":"exporter-output\/job01\/db.a.b-045-050-000001.txt.gz", "nifi_url":"nifi:8091\/dummy", "filename_header":"db.a.b-045-050-000001.json.gz", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table"""", formattedMessages[2])
        assertEquals("""Failed to post the provided item", "file_name":"exporter-output\/job01\/db.a.b-045-050-000001.txt.gz", "response":"400", "nifi_url":"nifi:8091\/dummy", "export_date":"2019-01-01", "snapshot_type":"incremental", "status_table_name":"test_table", "HEADER_NAME":"HEADER_VALUE"""", formattedMessages[3])
    }

    @Test
    fun shouldRaiseMetadataExceptionWhenFilenameIsBad() {
        val filename = "dbcoreaddressDeclaration-000001"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        val rejectedFilesCounterChild = mock<Counter.Child>()
        given(rejectedFilesCounter.labels(any())).willReturn(rejectedFilesCounterChild)

        try {
            httpWriter.write(mutableListOf(decryptedStream))
            fail("Expected MetadataException")
        }
        catch (ex: MetadataException) {
            assertEquals("""Rejecting 'dbcoreaddressDeclaration-000001': does not match '^(?:\w+\.)?(?<database>[\w-]+)\.(?<collection>[\w-]+)-\d{3}-\d{3}-\d+\.\w+\.\w+${'$'}'""", ex.message)
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
        verify(rejectedFilesCounterChild, times(1)).inc()
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
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
        verify(exportStatusService, times(1)).incrementSentCount(filename)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
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
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(rejectedFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun shouldThrowMetadataExceptionWhenFileNameDoesNotMatchRegex() {
        val filename = "bad_filename-045-050-000001"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        shouldThrow<MetadataException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        verifyZeroInteractions(exportStatusService)
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun shouldThrowMetadataExceptionWhenFilenameDoesNotContainAHyphen() {
        val filename = "db.type.nonum.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        shouldThrow<MetadataException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }
        verifyZeroInteractions(exportStatusService)
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(blockedTopicFileCounter)
    }

    @Test
    fun shouldThrowBlockedTopicExceptionWhenTopicNameIsInBlockedList() {

        val filename = "db.crypto.unencrypted-045-050-000001.txt.gz"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        val blockedTopicFileCounterChild = mock<Counter.Child>()
        given(blockedTopicFileCounter.labels(any())).willReturn(blockedTopicFileCounterChild)

        whenever(filterBlockedTopicsUtils.checkIfTopicIsBlocked("db.crypto.unencrypted", decryptedStream.fullPath)).doThrow(BlockedTopicException("Provided topic is blocked so cannot be processed: 'db.crypto.unencrypted'"))

        val exception = shouldThrow<BlockedTopicException> {
            httpWriter.write(mutableListOf(decryptedStream))
        }

        exception.message shouldBe "Provided topic is blocked so cannot be processed: 'db.crypto.unencrypted'"

        verify(blockedTopicFileCounterChild, times(1)).inc()
        verifyZeroInteractions(exportStatusService)
        verifyZeroInteractions(successPostFileCounter)
        verifyZeroInteractions(retriedPostFilesCounter)
        verifyZeroInteractions(rejectedFilesCounter)
    }


    private fun KArgumentCaptor<HttpPost>.verifyPostHeaders(httpClient: CloseableHttpClient,
                                                            filename: String,
                                                            databaseHeader: String = DATABASE_HEADER,
                                                            collectionHeader: String = COLLECTION_HEADER) {
        verify(httpClient, times(1)).execute(capture())
        assertEquals(CONTENT_TYPE_HEADER, firstValue.entity.contentType.toString())
        assertEquals(NIFI_HEADER_COUNT, firstValue.allHeaders.size)
        assertEquals("filename: ${filename.replace("txt", "json")}", firstValue.allHeaders[0].toString())
        assertEquals(ENVIRONMENT_HEADER, firstValue.allHeaders[1].toString())
        assertEquals(databaseHeader, firstValue.allHeaders[3].toString())
        assertEquals(collectionHeader, firstValue.allHeaders[4].toString())
        assertEquals(SNAPSHOT_TYPE_HEADER, firstValue.allHeaders[5].toString())
        assertEquals(TOPIC_HEADER, firstValue.allHeaders[6].toString())
        assertEquals(STATUS_TABLE_HEADER, firstValue.allHeaders[7].toString())
        assertEquals(CORRELATION_ID_HEADER, firstValue.allHeaders[8].toString())
        assertEquals(S3_PREFIX_HEADER, firstValue.allHeaders[9].toString())
        assertEquals(SHUTDOWN_FLAG_HEADER, firstValue.allHeaders[10].toString())
        assertEquals(REPROCESS_FILES_HEADER, firstValue.allHeaders[11].toString())
    }

    companion object {
        private const val COLLECTION_HEADER = "collection: addressDeclaration"
        private const val CONTENT_TYPE_HEADER = "Content-Type: application/octet-stream"
        private const val CORRELATION_ID_HEADER = "correlation_id: 123"
        private const val DATABASE_HEADER = "database: core"
        private const val DATABASE_WITH_HYPHEN_HEADER = "database: core-with-hyphen"
        private const val ENVIRONMENT_HEADER = "environment: aws/test"
        private const val NIFI_HEADER_COUNT: Int = 12
        private const val S3_PREFIX_HEADER = "s3_prefix: exporter-output/job01"
        private const val SNAPSHOT_TYPE_HEADER = "snapshot_type: incremental"
        private const val STATUS_TABLE_HEADER = "status_table_name: test_table"
        private const val TOPIC_HEADER = "topic: db.database.collection"
        private const val REPROCESS_FILES_HEADER = "reprocess_files: false"
        private const val SHUTDOWN_FLAG_HEADER = "shutdown_flag: true"
    }
}
