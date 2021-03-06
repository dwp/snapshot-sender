
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
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.Message
import com.google.gson.*
import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
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

        "There should be a success file for each successful topic" {
            val successes = File(NIFI_OUTPUT_FOLDER).walkTopDown()
                .filter(File::isFile).map(File::getName)
                .filter { it.endsWith("_successful.gz") }
                .toList()
            successes shouldContainExactlyInAnyOrder listOf("_database_empty_successful.gz", "_database_sent_successful.gz")
        }

        "Export status is 'Received' for no files exported topic" {
            validateResult(getItemResult("321", "db.database.empty").item, "Received", "0", "0")
        }

        "Export status is 'Sent' for successful topic" {
            validateResult(getItemResult("111", "db.database.sent").item, "Sent", "10", "10")
        }

        "Export status is 'Sent' for sent topic" {
            validateResult(getItemResult("123", "db.core.claimant").item, "Sent", "100", "100")
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

        "It should send the monitoring messages" {
            validateQueueMessage(monitoringQueueUrl, """{
                    "severity": "Critical",
                    "notification_type": "Information",
                    "slack_username": "Snapshot Sender",
                    "title_text": "Full - All files sent - success",
                    "custom_elements":[
                        {
                            "key":"Export date",
                            "value":"2019-01-01"
                        },
                        {
                            "key":"Correlation Id",
                            "value":"321"
                        }
                    ]
                }""", """{
                    "severity": "Critical",
                    "notification_type": "Information",
                    "slack_username": "Snapshot Sender",
                    "title_text": "Full - All files sent - success",
                    "custom_elements":[
                        {
                            "key":"Export date",
                            "value":"2019-01-01"
                        },
                        {
                            "key":"Correlation Id",
                            "value":"123"
                        }
                    ]
                }""")
        }

        "It should have pushed metrics" {
            val response = client.get<JsonObject>("http://prometheus:9090/api/v1/targets/metadata")
            val metricNames = response["data"].asJsonArray
                .map(JsonElement::getAsJsonObject)
                .filter { it["target"].asJsonObject["job"].asJsonPrimitive.asString == "pushgateway" }
                .map { it["metric"].asJsonPrimitive.asString }
                .filterNot {
                    it.startsWith("go_") || it.startsWith("process_") ||
                            it.startsWith("pushgateway_") || it.startsWith("push_")
                }

            metricNames shouldContainAll listOf(
                "snapshot_sender_completed_empty_collections",
                "snapshot_sender_completed_non_empty_collections",
                "snapshot_sender_decrypt_item_duration",
                "snapshot_sender_decrypt_key_duration",
                "snapshot_sender_dks_key_decryption_retries",
                "snapshot_sender_dks_keys_decrypted",
                "snapshot_sender_failed_runs",
                "snapshot_sender_files_posted_successfully",
                "snapshot_sender_filter_items_duration",
                "snapshot_sender_get_completion_status_duration",
                "snapshot_sender_increment_sent_count_duration",
                "snapshot_sender_incremented_files_sent",
                "snapshot_sender_items_read_from_s3",
                "snapshot_sender_monitoring_message_send_duration",
                "snapshot_sender_monitoring_messages_sent",
                "snapshot_sender_post_file_duration",
                "snapshot_sender_process_key_duration",
                "snapshot_sender_read_s3_duration",
                "snapshot_sender_set_collection_status_duration",
                "snapshot_sender_success_file_sending_retries",
                "snapshot_sender_success_files_sent",
                "snapshot_sender_successful_runs",
                "snapshot_sender_failed_collections",
                "snapshot_sender_failed_files",
                "snapshot_sender_failed_success_files",
                "snapshot_sender_running_applications")
        }

        "It should have pushed correct records post metrics " {
            val results = metricResults("""snapshot_sender_files_posted_successfully{topic_name="db.core.claimant"}""")

            val files = results.asSequence()
                .map(JsonElement::getAsJsonObject).map { it["metric"] }
                .map(JsonElement::getAsJsonObject).map { it["file_name"] }
                .map(JsonElement::getAsJsonPrimitive)
                .map(JsonPrimitive::getAsString).toList()

            files shouldContainExactlyInAnyOrder List(100) { String.format("db.core.claimant-045-050-%06d.txt.gz", it) }

            val counts = results.asSequence()
                .map(JsonElement::getAsJsonObject).map { it["value"] }
                .map(JsonElement::getAsJsonArray)
                .map { it[1] }
                .map(JsonElement::getAsJsonPrimitive)
                .map(JsonPrimitive::getAsString).toList()

            counts shouldContainExactly List(100) { "1" }
        }

        "It should have pushed correct successful runs metrics " {
            validateMetric("""snapshot_sender_successful_runs{correlation_id="123"}""", "1")
        }

        "It should have pushed correct incremented files sent metrics " {
            validateMetric("""snapshot_sender_incremented_files_sent{topic_name="db.core.claimant"}""", "100")
            validateMetric("""snapshot_sender_incremented_files_sent{topic_name="db.database.empty"}""", "0")
        }

        "It should have pushed correct completed non empty collections metrics " {
            validateMetric("""snapshot_sender_completed_non_empty_collections{topic_name="db.core.claimant"}""", "1")
            validateMetric("""snapshot_sender_completed_non_empty_collections{topic_name="db.database.empty"}""", "0")
        }

        "It should have pushed correct completed empty collections metrics " {
            validateMetric("""snapshot_sender_completed_empty_collections{topic_name="db.core.claimant"}""", "0")
            validateMetric("""snapshot_sender_completed_empty_collections{topic_name="db.database.empty"}""", "1")
        }
    }

    private fun validateQueueMessage(queueUrl: String, expectedMessageOne: String, expectedMessageTwo: String) {
        val received = queueMessages(queueUrl)
            .map(Message::getBody)
            .map {Gson().fromJson(it, JsonObject::class.java)}
            .mapNotNull { it["Message"] }

        received shouldHaveSize 2
        received[0].asString shouldMatchJson expectedMessageOne
        received[1].asString shouldMatchJson expectedMessageTwo
    }

    private tailrec fun queueMessages(queueUrl: String, accumulated: List<Message> = listOf()): List<Message> {
        val messages = amazonSqs.receiveMessage(queueUrl).messages

        if (messages == null || messages.isEmpty()) {
            return accumulated
        }
        messages.forEach{ deleteMessage(queueUrl, it) }
        return queueMessages(queueUrl, accumulated + messages)
    }

    private suspend fun validateMetric(resource: String, expected: String) {
        val results = metricResults(resource)
        results.size() shouldBe 1
        val result = results[0].asJsonObject["value"].asJsonArray[1].asJsonPrimitive.asString
        result shouldBe expected
    }

    private suspend fun metricResults(resource: String): JsonArray =
            client.get<JsonObject>("http://prometheus:9090/api/v1/query?query=$resource").let { response ->
                response["data"].asJsonObject["result"].asJsonArray
            }

    private fun deleteMessage(queueUrl: String, it: Message) = amazonSqs.deleteMessage(queueUrl, it.receiptHandle)

    private fun validateResult(item: MutableMap<String, AttributeValue>, expectedStatus: String, expectedExported: String, expectedSent: String) {
        val status = item["CollectionStatus"]
        val filesExported = item["FilesExported"]
        val filesSent = item["FilesSent"]
        status.shouldNotBeNull()
        status.s shouldBe expectedStatus
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

        private const val monitoringQueueUrl = "http://aws:4566/000000000000/monitoring-queue"

        val client = HttpClient {
            install(JsonFeature) {
                serializer = GsonSerializer {
                    setPrettyPrinting()
                }
            }
        }

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
                withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(S3_SERVICE_ENDPOINT, SIGNING_REGION))
                withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
                withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)))
                build()
            }
        }

        val amazonSqs: AmazonSQS by lazy {
            with (AmazonSQSClientBuilder.standard()) {
                withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(S3_SERVICE_ENDPOINT, SIGNING_REGION))
                withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
                withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(ACCESS_KEY, SECRET_KEY)))
                build()
            }
        }
    }
}
