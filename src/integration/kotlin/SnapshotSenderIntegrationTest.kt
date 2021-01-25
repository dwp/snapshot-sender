
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.dynamodbv2.model.GetItemResult
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

class SnapshotSenderIntegrationTest : StringSpec() {

    init {
        "There should be a finished file for every input file" {
            val inputs = s3.listObjectsV2(BUCKET, INPUT_FOLDER).objectSummaries.map(S3ObjectSummary::getKey).map(::File).map(File::getName)
            val successes = s3.listObjectsV2(BUCKET, STATUS_FILE_FOLDER).objectSummaries.map(S3ObjectSummary::getKey)
                .map(::File).map(File::getName).map { it.replace(Regex(".finished$"), "") }
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
            outputs shouldContainExactlyInAnyOrder inputs
        }

        "There should be a success file for each topic" {
            val successes = File(NIFI_OUTPUT_FOLDER).walkTopDown()
                .filter(File::isFile).map(File::getName)
                .filter { it.endsWith("_successful.gz") }
                .toList()
            successes shouldContainExactlyInAnyOrder listOf("_core_claimant_successful.gz", "_database_empty_successful.gz")
        }

        "Export status is sent for no files exported topic" {
            validateResult(getItemResult("321", "db.database.empty").item, "0", "0")
        }

        "Export status is sent" {
            validateResult(getItemResult("123", "db.core.claimant").item, "100", "100")
        }

        "Nifi output files are valid compressed jsonl" {
            val filesOnNifi = File(NIFI_OUTPUT_FOLDER).walkTopDown()
                .map(File::getAbsolutePath)
                .filter { it.contains("db.") && it.contains(".json.gz") }
                .map(::File)
                .toList()

            filesOnNifi.map(File::getName) shouldContainExactlyInAnyOrder (0..99).map {
                String.format("db.core.claimant-045-050-%06d.json.gz", it)
            }

            val gson = Gson()

            filesOnNifi.forEach { file ->
                BufferedReader(InputStreamReader(GZIPInputStream(FileInputStream(file)))).useLines { sequence ->
                    sequence.count() shouldBe 1_000
                }

                BufferedReader(InputStreamReader(GZIPInputStream(FileInputStream(file)))).useLines { sequence ->
                    sequence.forEach {
                        gson.fromJson(it, JsonObject::class.java)
                    }
                }
            }
        }
   }

    private fun validateResult(item: MutableMap<String, AttributeValue>, expectedExported: String, expectedSent: String) {
        val status = item["CollectionStatus"]
        val filesExported = item["FilesExported"]
        val filesSent = item["FilesSent"]
        status.shouldNotBeNull()
        status.s shouldBe "Sent"
        filesExported.shouldNotBeNull()
        filesExported.n shouldBe expectedExported
        filesSent.shouldNotBeNull()
        filesSent.n shouldBe expectedSent
    }

    private fun getItemResult(correlationId: String, collectionName: String): GetItemResult =
        dynamoDB.getItem(getItemRequest(primaryKeyValue(correlationId, collectionName)))

    private fun getItemRequest(primaryKey: Map<String, AttributeValue>): GetItemRequest =
        GetItemRequest().apply {
            tableName = "UCExportToCrownStatus"
            key = primaryKey
            consistentRead = true
        }

    private fun primaryKeyValue(correlationId: String, collectionName: String): Map<String, AttributeValue> =
        mapOf("CorrelationId" to AttributeValue().apply { s = correlationId },
            "CollectionName" to AttributeValue().apply { s = collectionName })


    companion object {
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

        val dynamoDB: AmazonDynamoDB by lazy {
            with (AmazonDynamoDBClientBuilder.standard()) {
                withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("http://aws:4566", "eu-west-2"))
                withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
                withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials("access-key", "secret-key")))
                build()
            }
        }
    }
}
