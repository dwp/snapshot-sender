package app.batch

import app.TestUtils.Companion.once
import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.MetadataException
import app.exceptions.WriterException
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import com.nhaarman.mockitokotlin2.argumentCaptor
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
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output"
])
class HttpWriterTest {

    @MockBean
    private lateinit var mockS3StatusFileWriter: S3StatusFileWriter

    @Autowired
    private lateinit var httpWriter: HttpWriter

    @MockBean
    private lateinit var httpClientProvider: HttpClientProvider

    val mockAppender: Appender<ILoggingEvent> = com.nhaarman.mockitokotlin2.mock()

    val byteArray = "hello, world".toByteArray()
    val s3Path = "exporter-output/job01" //should match the test properties above

    @Before
    fun before() {
        val root = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.addAppender(mockAppender)
        Mockito.reset(mockAppender)
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file() {
        //given
        val filename = "db.core.addressDeclaration-000001.txt.bz2.enc"
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
        assertEquals(2, httpCaptor.firstValue.allHeaders.size)
        assertEquals("filename: db.core.addressDeclaration-000001.txt.bz2.enc", httpCaptor.firstValue.allHeaders[0].toString())
        assertEquals("collection: db.core.addressDeclaration", httpCaptor.firstValue.allHeaders[1].toString())

        verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, Mockito.times(5)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("Writing: '1' items", formattedMessages[0])
        assertEquals("Checking: 'exporter-output/job01/db.core.addressDeclaration-000001.txt.bz2.enc'", formattedMessages[1])
        assertEquals("Found collection: 'db.core.addressDeclaration' from fileName of 'exporter-output/job01/db.core.addressDeclaration-000001.txt.bz2.enc'", formattedMessages[2])
        assertEquals("Posting: 'exporter-output/job01/db.core.addressDeclaration-000001.txt.bz2.enc' to 'db.core.addressDeclaration'.", formattedMessages[3])
        assertEquals("Successfully posted 'exporter-output/job01/db.core.addressDeclaration-000001.txt.bz2.enc': response '200'", formattedMessages[4])
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_embedded_hyphens_in_dbname() {
        val filename = "db.core-with-hyphen.addressDeclaration-000001.txt.bz2.enc"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))

        httpWriter.write(mutableListOf(decryptedStream))

        verify(httpClient, once()).execute(any(HttpPost::class.java))
        verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)
    }

    @Test
    fun test_will_write_to_nifi_when_valid_file_with_embedded_hyphens_in_collection() {
        val filename = "db.core-with-hyphen.address-declaration-has-hyphen-000001.txt.bz2.enc"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))

        httpWriter.write(mutableListOf(decryptedStream))

        verify(httpClient, once()).execute(any(HttpPost::class.java))
        verify(mockS3StatusFileWriter, once()).writeStatus(decryptedStream.fullPath)
    }

    @Test
    fun test_will_raise_error_when_file_cannot_be_sent() {
        val filename = "db.a.b-01.enc"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(400)
        given(httpResponse.statusLine).willReturn((statusLine))

        try {
            httpWriter.write(mutableListOf(decryptedStream))
            fail("Expected WriterException")
        }
        catch (ex: WriterException) {
            assertEquals("Failed to post 'exporter-output/job01/db.a.b-01.enc': post returned status code 400", ex.message)
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, Mockito.times(5)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("Writing: '1' items", formattedMessages[0])
        assertEquals("Checking: 'exporter-output/job01/db.a.b-01.enc'", formattedMessages[1])
        assertEquals("Found collection: 'db.a.b' from fileName of 'exporter-output/job01/db.a.b-01.enc'", formattedMessages[2])
        assertEquals("Posting: 'exporter-output/job01/db.a.b-01.enc' to 'db.a.b'.", formattedMessages[3])
        assertEquals("Failed to post 'exporter-output/job01/db.a.b-01.enc': post returned status code 400", formattedMessages[4])
    }

    @Test
    fun test_will_raise_metatdata_error_when_filename_is_bad() {
        val filename = "dbcoreaddressDeclaration-000001.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        try {
            httpWriter.write(mutableListOf(decryptedStream))
            fail("Expected MetadataException")
        }
        catch (ex: MetadataException) {
            assertEquals("Rejecting: 'exporter-output/job01/dbcoreaddressDeclaration-000001.txt' as fileName does not match '^\\w+\\.(?:\\w|-)+\\.((?:\\w|-)+)'", ex.message)
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
    }

    @Test
    fun test_will_raise_metatdata_error_when_filename_has_no_dash_before_number() {
        val filename = "db.core.address01.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        try {
            httpWriter.write(mutableListOf(decryptedStream))
            fail("Expected MetadataException")
        }
        catch (ex: MetadataException) {
            assertEquals("Rejecting: 'exporter-output/job01/db.core.address01.txt' as fileName does not contain '-' to find number", ex.message)
        }
        verify(mockS3StatusFileWriter, never()).writeStatus(decryptedStream.fullPath)
    }
}
