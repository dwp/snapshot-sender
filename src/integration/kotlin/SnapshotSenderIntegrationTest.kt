import io.kotlintest.specs.StringSpec
import org.apache.http.client.fluent.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class SnapshotSenderIntegrationTest : StringSpec() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SnapshotSenderIntegrationTest::class.toString())
    }

    val s3Bucket = System.getenv("s3.bucket") ?: "demobucket"
    val s3PrefixFolder = System.getenv("s3.prefix.folder") ?: "test/output/"
    val s3HtmeRootFolder = System.getenv("s3.htme.root.folder") ?: "test"
    val s3StatusFolder = System.getenv("s3.status.folder") ?: "status"
    val s3ServiceEndpoint = System.getenv("s3.service.endpoint") ?: "http://localhost:4572"
    val nifiRootFolder = System.getenv("nifi.root.folder") ?: "/data/output"
    val nifiTimestamp = System.getenv("nifi.timestamp") ?: "10"
    val nifiFileNames = System.getenv("nifi.file.names")
        ?: "/data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2"
    val nifiLinecounts = System.getenv("nifi.file.linecounts") ?: "7"
    var bucketUri = "$s3ServiceEndpoint/$s3Bucket"

    init {
        "Verify vars" {
            logger.info(s3PrefixFolder)
            logger.info(s3HtmeRootFolder)
            logger.info(s3StatusFolder)
            logger.info(s3ServiceEndpoint)
            logger.info(nifiRootFolder)
            logger.info(nifiTimestamp)
            logger.info(nifiFileNames)
            logger.info(nifiLinecounts)
        }

        "Verify for every source collection a finished file was written to s3" {
            logger.info("Hello Mum 1: $bucketUri")

            //s3 in:  test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //s3 out: test/status/db.core.addressDeclaration-000001.txt.bz2.enc.finished

            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            //content = "Finished test/output/db.core.addressDeclaration-000001.txt.bz2.enc"
            val results = Request.Get(bucketUri)
                .connectTimeout(100000)
                .socketTimeout(100000)
                .execute().returnContent().asString()
            logger.info(results)

            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val xmlInput = InputSource(StringReader(results))
            val doc = dBuilder.parse(xmlInput)
            val keys = doc.getElementsByTagName("Key")
            logger.info(keys.toString())

            val exporterKeys = mutableListOf<String>()
            val ststusKeys = mutableListOf<String>()

            for (index in 0 until keys.length) {
                val key = keys.item(index)
                val keyText = key.textContent
                logger.info("keyText: $keyText")
            }
        }

        "Verify for every source collection an output file was sent to nifi as bz2 with valid json lines at expected timestamp" {
            logger.info("Hello Mum 1")

            //s3 in:    test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //nifi out: /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2

            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -timestamp 10 \

        }

        "Verify addressDeclaration output file in nifi has 7 lines" {
            logger.info("Hello Mum 2")
            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -linecount 7"
        }
    }
}
