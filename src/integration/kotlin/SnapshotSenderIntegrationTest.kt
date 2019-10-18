import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.apache.http.client.fluent.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
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
    val nifiFileNamesCSV = System.getenv("nifi.file.names.csv")
        ?: "/data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2"
    val nifiLineCountsCSV = System.getenv("nifi.file.linecounts.csv") ?: "7"

    var bucketUri = "$s3ServiceEndpoint/$s3Bucket"
    var s3SourceExporterFolder = "$s3PrefixFolder"
    var s3SenderStatusFolder = s3SourceExporterFolder.replace("$s3HtmeRootFolder/", "$s3StatusFolder/")
    var nifiFileNames = nifiFileNamesCSV.split(",")
    var nifiLineCounts = nifiLineCountsCSV.split(",")

    init {

        "Verify for every source collection a finished file was written to s3" {
            logger.info("Checking $bucketUri ...")

            //s3 in:  test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //s3 out: test/status/db.core.addressDeclaration-000001.txt.bz2.enc.finished

            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            //content = "Finished test/output/db.core.addressDeclaration-000001.txt.bz2.enc"
            val bucketResultsXml = getS3Content(bucketUri)
            val fileKeys = getXmlNodesByTagName("Key", bucketResultsXml)

            val allKeys = mutableListOf<String>()
            for (index in 0 until fileKeys.length) {
                val key = fileKeys.item(index)
                val keyText = key.textContent
                logger.info("keyText: $keyText")
                allKeys.add(keyText)
            }

            val exporterKeys = allKeys.filter { it.startsWith(s3SourceExporterFolder) }
            logger.info("exporterKeys: $exporterKeys")
            val statusKeys = allKeys.filter { it.startsWith(s3SenderStatusFolder) }
            logger.info("statusKeys: $statusKeys")
            exporterKeys.size.shouldBeGreaterThan(0)
            exporterKeys.size.shouldBe(statusKeys.size)

            val matchedExporterAndStatusKeys = mutableMapOf<String, String>()
            exporterKeys.forEach { exporterKey ->
                val withUpdatedFolder = exporterKey.replace(s3SourceExporterFolder, s3SenderStatusFolder)
                val expectedStatusKey = "$withUpdatedFolder.finished"
                logger.info("Checking statusKeys contains '$expectedStatusKey'")
                statusKeys.shouldContain(expectedStatusKey)
                matchedExporterAndStatusKeys[exporterKey] = expectedStatusKey
            }

            matchedExporterAndStatusKeys.forEach { pair ->
                val fullPath = "$bucketUri/${pair.value}"
                logger.info("Loading status file '$fullPath'")
                val keyResult = getS3Content(fullPath)
                logger.info("$keyResult file contains text '$keyResult'")
                logger.info("Checking file '${pair.value}' contains '${pair.key}'")
                keyResult.shouldBe("Finished ${pair.key}")
            }
        }

        "Verify for every source collection an output file was sent to nifi as bz2 with valid json lines at expected timestamp" {
            logger.info("Hello Mum 1")

            //s3 in:    test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //nifi out: /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2

            File(nifiRootFolder).walkTopDown().forEach { println(it) }

            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -timestamp 10 \

        }

        "Verify nifi output files have specified line count" {
            logger.info("Hello Mum 2")
            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -linecount 7"
        }
    }

    private val dbFactory = DocumentBuilderFactory.newInstance()
    private val dBuilder = dbFactory.newDocumentBuilder()

    private fun getS3Content(s3FullPath: String): String {
        val results = Request.Get(s3FullPath)
            .connectTimeout(1000)
            .socketTimeout(1000)
            .execute().returnContent().asString()
        logger.info("S3 results for '$s3FullPath':\n$results")
        if (results == null) {
            throw RuntimeException("No results found for $s3FullPath")
        }
        return results
    }

    private fun getXmlNodesByTagName(keyName: String, sourceXmlString: String): NodeList {
        val xmlInput = InputSource(StringReader(sourceXmlString))
        val doc = dBuilder.parse(xmlInput)
        val keys = doc.getElementsByTagName(keyName)
        if (keys == null) {
            throw RuntimeException("No results found for '$keyName' in given xml")
        }
        logger.info("Found ${keys.length} keys with tag name '$keyName'")
        return keys
    }

}
