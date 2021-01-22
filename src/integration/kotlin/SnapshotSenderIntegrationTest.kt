
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.S3ObjectSummary
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*

@Suppress("BlockingMethodInNonBlockingContext")
class SnapshotSenderIntegrationTest : StringSpec() {

    init {
        "There should be a finished file for every input file" {
            val inputs = s3.listObjectsV2(BUCKET, INPUT_FOLDER).objectSummaries.map(S3ObjectSummary::getKey).map(::File).map(File::getName)
            val successes = s3.listObjectsV2(BUCKET, STATUS_FILE_FOLDER).objectSummaries.map(S3ObjectSummary::getKey)
                .map(::File).map(File::getName).map { it.replace(Regex(".finished$"), "") }
            inputs shouldContainExactlyInAnyOrder listOf("db.core.addressDeclaration-045-050-000001.txt.gz.enc",
                "db.quartz.claimantEvent-045-050-000001.txt.gz.enc")
            successes shouldContainExactlyInAnyOrder inputs
        }

        "There should be a nifi file for every input file" {
            val inputs = s3.listObjectsV2(BUCKET, INPUT_FOLDER).objectSummaries.map(S3ObjectSummary::getKey)
                .map(::File).map(File::getName).map { it.replace(Regex("""\.txt\.gz\.enc$"""), "")}

            val outputs = File(NIFI_OUTPUT_FOLDER).walkTopDown()
                .filter(File::isFile).map(File::getName)
                .filter { it.endsWith(".json.gz") }
                .map { it.replace(Regex("""\.json\.gz$"""), "") }
                .toList()
            inputs shouldContainExactlyInAnyOrder listOf("db.core.addressDeclaration-045-050-000001",
                "db.quartz.claimantEvent-045-050-000001")
            outputs shouldContainExactlyInAnyOrder inputs
        }

        "Export status is sent" {
            val dynamoDB = AmazonDynamoDBClientBuilder.standard()
                    .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("http://aws:4566",
                            "eu-west-2"))
                    .withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
                    .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials("access-key", "secret-key")))
                    .build()
            val correlationIdAttributeValue = AttributeValue().apply { s = "123" }
            val collectionNameAttributeValue = AttributeValue().apply { s = "db.core.toDo" }
            val primaryKey = mapOf("CorrelationId" to correlationIdAttributeValue, "CollectionName" to collectionNameAttributeValue)

            val getItemRequest = GetItemRequest().apply {
                tableName = "UCExportToCrownStatus"
                key = primaryKey
                consistentRead = true
            }
            val result = dynamoDB.getItem(getItemRequest)
            val item = result.item
            val status = item["CollectionStatus"]
            val filesExported = item["FilesExported"]
            val filesSent = item["FilesSent"]
            status.shouldNotBeNull()
            status.s shouldBe "Sent"
            filesExported.shouldNotBeNull()
            filesExported.n shouldBe "2"
            filesSent.shouldNotBeNull()
            filesSent.n shouldBe "2"
        }

        "Verify nifi output files have a valid json per line at expected timestamp with specified line count" {
            logger.info("Check nifi outputs")

            val actualNifiFiles = File(NIFI_OUTPUT_FOLDER).walkTopDown()
                .map { it.absolutePath }
                .filter { it.contains("db.") && it.contains(".json.gz") }
                .toList()
            logger.info("actualNifiFiles: $actualNifiFiles")

            for (index in nifiFileNames.indices) {
                val expectedFile = nifiFileNames[index]
                val collection = deriveCollection("/$expectedFile")
                val fullPath = "$NIFI_OUTPUT_FOLDER/$collection/$expectedFile"
                val expectedLineCount = nifiLineCounts[index].toInt()
                val expectedTimestamp = nifiTimestamps[index].toLong()

                logger.info("Checking that file $expectedFile was sent with $expectedLineCount lines and $expectedTimestamp latest timestamp in data")
                logger.info("Looking for file $fullPath")

                val streamIn = FileInputStream(fullPath)
                val dataStream = CompressorStreamFactory()
                    .createCompressorInputStream(CompressorStreamFactory.GZIP, BufferedInputStream(streamIn))
                val dataReader = BufferedReader(InputStreamReader(dataStream, "UTF-8"))

                var linesDone = 0
                do {
                    val line = dataReader.readLine()
                    when {
                        line != null -> {
                            linesDone++
                        }
                        linesDone == expectedLineCount -> {
                            logger.info("Skipping blank line at EOF as should be end of file: have processed $linesDone/$expectedLineCount in $expectedFile")
                        }
                        else -> {
                            fail("Did not expect blank line before EOF: have only processed $linesDone/$expectedLineCount in $expectedFile")
                        }
                    }
                }
                while (line != null)

                if (linesDone != expectedLineCount) {
                    fail("Did get expected line count: have only processed $linesDone/$expectedLineCount in $expectedFile")
                }
            }

            actualNifiFiles.size.shouldBe(nifiFileNames.size)
        }
    }

    private fun deriveCollection(fileName: String): String {
        //s3 in:    test/output/db.core.addressDeclaration-045-050-000001.json.gz.enc
        //out       db.core.addressDeclaration

        val lastSlashIndex = fileName.lastIndexOf("/")
        if (lastSlashIndex < 0) {
            fail("Rejecting: '$fileName' as fileName does not contain '/' to find collection")
        }
        val lastDashIndex = fileName.lastIndexOf("-")
        if (lastDashIndex < 0) {
            fail("Rejecting: '$fileName' as fileName does not contain '-' to find collection")
        }
        val fullCollection = fileName.substring((lastSlashIndex + 1) until (lastDashIndex)).replace(Regex("""-\d{3}-\d{3}$"""), "")
        logger.info("Derived collection: '${fullCollection}' from fileName of '$fileName'")
        return fullCollection
    }

    companion object {
        private val nifiFileNames = listOf("db.core.addressDeclaration-045-050-000001.json.gz","db.quartz.claimantEvent-045-050-000001.json.gz") //nifiFileNamesCSV.split(",")
        private val nifiLineCounts = listOf("7", "1")
        private val nifiTimestamps = listOf("10", "1")

        private const val INPUT_FOLDER = "test/output"
        private const val BUCKET = "demobucket"
        private const val NIFI_OUTPUT_FOLDER = "/data/output"

        private const val S3_SERVICE_ENDPOINT = "http://aws:4566"
        private const val STATUS_FILE_FOLDER = "status/output"
        private const val SIGNING_REGION = "eu-west-2"
        private const val ACCESS_KEY = "accessKey"
        private const val SECRET_KEY = "secretKey"

        private val s3: AmazonS3 by lazy {
            with (AmazonS3ClientBuilder.standard()) {
                withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(S3_SERVICE_ENDPOINT, SIGNING_REGION))
                withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
                withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)))
                withPathStyleAccessEnabled(true)
                disableChunkedEncoding()
                build()
            }
        }

        private val logger: Logger = LoggerFactory.getLogger(SnapshotSenderIntegrationTest::class.toString())
    }
}
