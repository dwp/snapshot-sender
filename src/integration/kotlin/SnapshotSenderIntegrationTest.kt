import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldContainAll
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

    val s3Bucket = System.getenv("S3_BUCKET") ?: "demobucket"
    val s3PrefixFolder = System.getenv("S3_PREFIX_FOLDER") ?: "test/output/"
    val s3HtmeRootFolder = System.getenv("S3_HTME_ROOT_FOLDER") ?: "test"
    val s3StatusFolder = System.getenv("S3_STATUS_FOLDER") ?: "status"
    val s3ServiceEndpoint = System.getenv("S3_SERVICE_ENDPOINT") ?: "http://localhost:4572"
    val nifiRootFolder = System.getenv("NIFI_ROOT_FOLDER") ?: "/data/output"
    val nifiTimestamp = System.getenv("NIFI_TIMESTAMP") ?: "10"
    val nifiFileNamesCSV = System.getenv("NIFI_FILE_NAMES_CSV")
        ?: "db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2"
    val nifiLineCountsCSV = System.getenv("NIFI_FILE_LINECOUNTS_CSV") ?: "7"

    var bucketUri = "$s3ServiceEndpoint/$s3Bucket"
    var s3SourceExporterFolder = "$s3PrefixFolder"
    var s3SenderStatusFolder = s3SourceExporterFolder.replace("$s3HtmeRootFolder/", "$s3StatusFolder/")
    var nifiFileNames = nifiFileNamesCSV.split(",")
    var nifiLineCounts = nifiLineCountsCSV.split(",")

    init {

        "Verify env vars" {
            logger.info("env vars: ${System.getenv()}")
        }

        "Verify for every source collection a finished file was written to s3" {
            logger.info("Checking $bucketUri ...")

            //s3 in:  test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //s3 out: test/status/db.core.addressDeclaration-000001.txt.bz2.enc.finished

            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            //content = "Finished test/output/db.core.addressDeclaration-000001.txt.bz2.enc"
            val bucketResultsXml = getS3Content(bucketUri)
            val fileKeys = getXmlNodesByTagName("Key", bucketResultsXml)
            val allKeys = getFileKeys(fileKeys)
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

        "Verify for every source collection an output file was sent to nifi as bz2" {
            logger.info("Check collections vs nifi")

            //s3 in:    test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //nifi out: /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2

            val bucketResultsXml = getS3Content(bucketUri)
            val fileKeys = getXmlNodesByTagName("Key", bucketResultsXml)
            val allKeys = getFileKeys(fileKeys)
            val exporterKeys = allKeys.filter { it.startsWith(s3SourceExporterFolder) }
            logger.info("exporterKeys: $exporterKeys")

            val exporterKeysToMatchNifi = exporterKeys
                .map { it.replace(s3SourceExporterFolder, "") }
                .map { it.replace(".enc", "") }
                .map {
                    val collection = deriveCollection(it)
                    "$collection/$it"
                }
            logger.info("exporterKeysToMatchNifi: $exporterKeysToMatchNifi")

            val nifiFiles = File(nifiRootFolder).walkTopDown()
                .map {it.absolutePath}
                .filter { it.contains("db.") && it.contains(".txt.bz2")}
                .toList()
            logger.info("nifiFiles: $nifiFiles")

            nifiFiles.shouldContainAll(exporterKeysToMatchNifi)
            nifiFiles.size.shouldBe(exporterKeysToMatchNifi.size)
        }

        "Verify nifi output files are with valid json lines at expected timestamp with specified line count" {
            logger.info("Check nifi outputs")
            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -linecount 7"
            //              -timestamp 10 \
        }
    }

    private fun getFileKeys(fileKeys: NodeList): MutableList<String> {
        val allKeys = mutableListOf<String>()
        for (index in 0 until fileKeys.length) {
            val key = fileKeys.item(index)
            val keyText = key.textContent
            logger.info("keyText: $keyText")
            allKeys.add(keyText)
        }
        return allKeys
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
        val keys = doc.getElementsByTagName(keyName) ?: throw RuntimeException("No elements found for '$keyName' in given xml")
        logger.info("Found ${keys.length} keys with tag name '$keyName'")
        return keys
    }

    private fun deriveCollection(fileName: String): String {
        //s3 in:    test/output/db.core.addressDeclaration-000001.txt.bz2.enc
        //out       db.core.addressDeclaration

        val lastSlashIndex = fileName.lastIndexOf("/")
        if (lastSlashIndex < 0) {
            val errorMessage = "Rejecting: '$fileName' as fileName does not contain '/' to find collection"
            logger.error(errorMessage)
            throw RuntimeException(errorMessage)
        }
        val lastDashIndex = fileName.lastIndexOf("-")
        if (lastDashIndex < 0) {
            val errorMessage = "Rejecting: '$fileName' as fileName does not contain '-' to find collection"
            logger.error(errorMessage)
            throw RuntimeException(errorMessage)
        }
        val fullCollection = fileName.substring((lastSlashIndex + 1) until (lastDashIndex))
        logger.info("Found collection: '${fullCollection}' from fileName of '$fileName'")
        return fullCollection
    }
}
