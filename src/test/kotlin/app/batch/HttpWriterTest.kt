package app.batch

import app.TestUtils.Companion.once
import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.MetadataException
import app.exceptions.WriterException
import com.amazonaws.services.s3.AmazonS3
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.slf4j.Logger
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
@SpringBootTest
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
    private lateinit var s3Client: AmazonS3

    val byteArray = "hello, world".toByteArray()
    val s3Path = "exporter-output/job01" //should match the test properties above

    @Test
    fun test_will_write_to_nifi_when_valid_file() {
        val filename = "db.core.addressDeclaration-000001.txt.bz2.enc"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))

        httpWriter.write(mutableListOf(decryptedStream));
        verify(httpClient, once()).execute(any(HttpPost::class.java))
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
    }

    @Test
    fun test_will_raise_error_when_file_cannot_be_sent() {
        val filename = "db.core.addressDeclaration-000001.txt.bx2.enc"
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
            assertEquals("Failed to write 'db.core.addressDeclaration-000001.txt.bx2.enc': post returned status code 400", ex.message)
        }
    }

    @Test
    fun test_will_raise_metatdata_error_when_metadata_is_bad() {
        val filename = "dbcoreaddressDeclaration-000001.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename, "$s3Path/$filename")

        try {
            httpWriter.write(mutableListOf(decryptedStream))
            fail("Expected MetadataException")
        }
        catch (ex: MetadataException) {
            assertEquals("Rejecting: 'dbcoreaddressDeclaration-000001.txt' as name does not match '^\\w+\\.(?:\\w|-)+\\.((?:\\w|-)+)'", ex.message)
        }
    }

    @Autowired
    private lateinit var httpWriter: HttpWriter

    @Autowired
    private lateinit var httpClientProvider: HttpClientProvider

}