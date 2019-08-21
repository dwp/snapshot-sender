package app.batch

import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

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
        logger.info("httpWriter: '$httpWriter'.")
        //val byteArray = ByteArray()
//        httpWriter.write()
    }

    @Autowired
    private lateinit var httpWriter: HttpWriter

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpWriterTest::class.toString())
    }

}