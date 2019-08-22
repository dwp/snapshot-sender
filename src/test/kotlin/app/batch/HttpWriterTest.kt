package app.batch

import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.exceptions.MetadataException
import app.exceptions.WriterException
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayInputStream

@RunWith(SpringRunner::class)
@ActiveProfiles( "httpDataKeyService", "unitTest", "httpWriter")
@SpringBootTest
@TestPropertySource(properties = [
    "data.key.service.url=datakey.service:8090",
    "nifi.url=nifi:8091"
])
class HttpWriterTest {

    @Test
    fun testOk() {
        val byteArray = "hello, world".toByteArray()
        val filename = "db.core.addressDeclaration.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename)
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(200)
        given(httpResponse.statusLine).willReturn((statusLine))
        httpWriter.write(mutableListOf(decryptedStream));
    }

    @Test(expected = WriterException::class)
    fun testNotOk() {
        val byteArray = "hello, world".toByteArray()
        val filename = "db.core.addressDeclaration.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename)
        val httpClient = Mockito.mock(CloseableHttpClient::class.java)
        given(httpClientProvider.client()).willReturn(httpClient)
        val httpResponse = Mockito.mock(CloseableHttpResponse::class.java)
        given(httpClient.execute(ArgumentMatchers.any(HttpPost::class.java))).willReturn(httpResponse)
        val statusLine = Mockito.mock(StatusLine::class.java)
        given(statusLine.statusCode).willReturn(400)
        given(httpResponse.statusLine).willReturn((statusLine))
        httpWriter.write(mutableListOf(decryptedStream));
    }

    @Test(expected = MetadataException::class)
    fun testBadMetadata() {
        logger.info("httpWriter: '$httpWriter'.")
        logger.info("httpClientProvider: '$httpClientProvider'.")
        val byteArray = "hello, world".toByteArray()
        val filename = "dbcoreaddressDeclaration.txt"
        val decryptedStream = DecryptedStream(ByteArrayInputStream(byteArray), filename)
        httpWriter.write(mutableListOf(decryptedStream));
    }

    @Autowired
    private lateinit var httpWriter: HttpWriter

    @Autowired
    private lateinit var httpClientProvider: HttpClientProvider

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpWriterTest::class.toString())
    }

}