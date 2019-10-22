
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.matchers.numerics.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.string.shouldNotBeEmpty
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.http.client.fluent.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.*
import java.util.stream.Collectors
import javax.xml.parsers.DocumentBuilderFactory

class SnapshotSenderIntegrationTest : StringSpec() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SnapshotSenderIntegrationTest::class.toString())
    }

    private val s3Bucket = System.getenv("S3_BUCKET") ?: "demobucket"
    private val s3PrefixFolder = System.getenv("S3_PREFIX_FOLDER") ?: "test/output/"
    private val s3HtmeRootFolder = System.getenv("S3_HTME_ROOT_FOLDER") ?: "test"
    private val s3StatusFolder = System.getenv("S3_STATUS_FOLDER") ?: "status"
    private val s3ServiceEndpoint = System.getenv("S3_SERVICE_ENDPOINT") ?: "http://localhost:4572"
    private val nifiRootFolder = System.getenv("NIFI_ROOT_FOLDER") ?: "/data/output"

    //matched triplets of file name, timestamp and line count
    private val nifiFileNamesCSV = System.getenv("NIFI_FILE_NAMES_CSV") ?: "db.core.addressDeclaration-000001.txt.bz2"
    private val nifiLineCountsCSV = System.getenv("NIFI_LINE_COUNTS_CSV") ?: "7"
    private val nifiTimestampsCSV = System.getenv("NIFI_TIME_STAMPS_CSV") ?: "10"

    private val bucketUri = "$s3ServiceEndpoint/$s3Bucket"
    private val s3SourceExporterFolder = s3PrefixFolder
    private val s3SenderStatusFolder = s3SourceExporterFolder.replace("$s3HtmeRootFolder/", "$s3StatusFolder/")
    private val nifiFileNames = nifiFileNamesCSV.split(",")
    private val nifiLineCounts = nifiLineCountsCSV.split(",")
    private val nifiTimestamps = nifiTimestampsCSV.split(",")

    private val dbFactory = DocumentBuilderFactory.newInstance()
    private val dBuilder = dbFactory.newDocumentBuilder()
    private val jsonParser: Parser = Parser.default()

    init {

        "Verify env vars were passed from docker via gradle to here" {
            s3Bucket.shouldNotBeEmpty()
            s3PrefixFolder.shouldNotBeEmpty()
            s3HtmeRootFolder.shouldNotBeEmpty()
            s3StatusFolder.shouldNotBeEmpty()
            s3ServiceEndpoint.shouldNotBeEmpty()
            nifiRootFolder.shouldNotBeEmpty()
            nifiFileNamesCSV.shouldNotBeEmpty()
            nifiLineCountsCSV.shouldNotBeEmpty()
            nifiTimestampsCSV.shouldNotBeEmpty()
            bucketUri.length.shouldBeGreaterThan(1)
            s3SourceExporterFolder.shouldNotBeEmpty()
            s3SenderStatusFolder.shouldNotBeEmpty()
            nifiFileNames.size.shouldBeGreaterThanOrEqual(1)
            nifiLineCounts.size.shouldBeGreaterThanOrEqual(1)
            nifiTimestamps.size.shouldBeGreaterThanOrEqual(1)
            //matched triplets of file name, timestamp and line count
            nifiLineCounts.size.shouldBe(nifiFileNames.size)
            nifiTimestamps.size.shouldBe(nifiFileNames.size)
        }

        "Verify for every source collection a finished file was written to s3" {
            logger.info("Checking $bucketUri ...")

            //s3 in:  test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //s3 out: test/status/db.core.addressDeclaration-000001.txt.bz2.enc.finished

            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished

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
                //content = "Finished test/output/db.core.addressDeclaration-000001.txt.bz2.enc"
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


            println("s3SourceExporterFolder: '$s3SourceExporterFolder'.")
            val exporterKeysToMatchNifi = exporterKeys
                .map { it.replace(s3SourceExporterFolder, "") }
                .map { it.replace(".enc", "") }
                .map {
                    println("it: '$it'.")
                    val collection = File(it).name
                            .replace(Regex("-.*$"), "")
                            .replace(Regex("^.*/"), "")
                    println("collection: '$collection'.")
                    "$nifiRootFolder/$collection/$it"
                }

            logger.info("exporterKeysToMatchNifi: $exporterKeysToMatchNifi")

            val actualNifiFiles = File(nifiRootFolder).walkTopDown()
                .map { it.absolutePath }
                .filter { it.contains("db.") && it.contains(".txt.bz2") }
                .toList()
            logger.info("actualNifiFiles: $actualNifiFiles")

            actualNifiFiles.shouldContainAll(exporterKeysToMatchNifi)
            actualNifiFiles.size.shouldBe(exporterKeysToMatchNifi.size)
        }

        "Verify nifi output files are with valid json lines at expected timestamp with specified line count" {
            logger.info("Check nifi outputs")
            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -linecount 7"
            //              -timestamp 10 \

            val actualNifiFiles = File(nifiRootFolder).walkTopDown()
                .map { it.absolutePath }
                .filter { it.contains("db.") && it.contains(".txt.bz2") }
                .toList()
            logger.info("actualNifiFiles: $actualNifiFiles")

            for (index in nifiFileNames.indices) {
                val expectedFile = nifiFileNames[index]
                val collection = deriveCollection("/$expectedFile")
                val fullPath = "$nifiRootFolder/$collection/$expectedFile"
                val expectedLineCount = nifiLineCounts[index].toInt()
                val expectedTimestamp = nifiTimestamps[index].toLong()

                logger.info("Checking that file $expectedFile was sent with $expectedLineCount lines and $expectedTimestamp latest timestamp in data")

                val contents = BZip2CompressorInputStream(FileInputStream(fullPath)).use {
                    it.readBytes()
                }

                val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(contents)))
                val linesInFile = reader.lines().collect(Collectors.toList())
                linesInFile.size.shouldBe(expectedLineCount)
                linesInFile.forEach { line ->
                    val jsonLine = parseJson(line)
                    jsonLine["timestamp"].shouldBe(expectedTimestamp)
                }
            }
        }
    }

    private fun parseJson(line: String?): JsonObject {
        try {
            val stringBuilder = StringBuilder(line)
            return jsonParser.parse(stringBuilder) as JsonObject
        }
        catch (ex: Exception) {
            fail("Could not parse json line: Got '$ex' from parsing '$line' ")
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
            ?: throw RuntimeException("No elements found for '$keyName' in given xml")
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
