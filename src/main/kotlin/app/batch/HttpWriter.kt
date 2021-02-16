package app.batch

import app.configuration.HttpClientProvider
import app.domain.DecryptedStream
import app.domain.NifiHeaders
import app.exceptions.WriterException
import app.services.ExportStatusService
import app.utils.FilterBlockedTopicsUtils
import app.utils.NiFiUtility
import app.utils.TextParsingUtility
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.InputStreamEntity
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import io.prometheus.client.Counter
import app.exceptions.MetadataException
import app.exceptions.BlockedTopicException
import io.prometheus.client.spring.web.PrometheusTimeMethod

@Component
class HttpWriter(private val httpClientProvider: HttpClientProvider,
                 private val exportStatusService: ExportStatusService,
                 private val filterBlockedTopicsUtils: FilterBlockedTopicsUtils,
                 private val nifiUtility: NiFiUtility,
                 private val successPostFileCounter: Counter,
                 private val retriedPostFilesCounter: Counter,
                 private val rejectedFilesCounter: Counter,
                 private val blockedTopicCounter: Counter) : ItemWriter<DecryptedStream> {

    @Autowired
    lateinit var s3StatusFileWriter: S3StatusFileWriter

    @Throws(Exception::class)
    @PrometheusTimeMethod(name = "snapshot_sender_post_file_duration", help = "Duration of posting a file")
    override fun write(items: MutableList<out DecryptedStream>) {
        logger.info("Writing items to S3", "number_of_items" to items.size.toString())
        items.forEach(::postItem)
    }

    private fun postItem(item: DecryptedStream) {

        logger.info("Checking item to write", "file_name" to item.fileName, "full_path" to item.fullPath)

        val (database, collection) = getDatabaseAndCollection(item.fileName)
        val topicPrefix = if (item.fileName.startsWith("db.")) "db." else ""
        val topic = "$topicPrefix$database.$collection"

        try {
            filterBlockedTopicsUtils.checkIfTopicIsBlocked(topic, item.fullPath)
        } catch (ex: BlockedTopicException) {
            blockedTopicCounter.labels(item.fileName).inc(1.toDouble())
            throw ex
        }

        val filenameHeader = item.fileName.replace(Regex("""\.txt\.gz$"""), ".json.gz")

        logger.info("Posting file name to collection",
                "database" to database,
                "collection" to collection,
                "topic" to topic,
                "file_name" to item.fileName,
                "full_path" to item.fullPath,
                "nifi_url" to nifiUrl,
                "filename_header" to filenameHeader,
                "export_date" to exportDate,
                "snapshot_type" to snapshotType,
                "status_table_name" to statusTableName)

        httpClientProvider.client().use { it ->

            val post = HttpPost(nifiUrl).apply {
                entity = InputStreamEntity(item.inputStream, -1, ContentType.DEFAULT_BINARY)
                nifiUtility.setNifiHeaders(this, NifiHeaders(filename = filenameHeader,
                                                                    database = database,
                                                                    collection = collection))
            }

            it.execute(post).use { response ->
                when (response.statusLine.statusCode) {
                    200 -> {                    
                        successPostFileCounter.labels(item.fileName).inc(1.toDouble())
                        logger.info("Successfully posted file",
                                "database" to database,
                                "collection" to collection,
                                "topic" to topic,
                                "file_name" to item.fullPath,
                                "response" to response.statusLine.statusCode.toString(),
                                "nifi_url" to nifiUrl,
                                "export_date" to exportDate,
                                "snapshot_type" to snapshotType,
                                "status_table_name" to statusTableName)
                        exportStatusService.incrementSentCount(item.fileName)
                        s3StatusFileWriter.writeStatus(item.fullPath)
                    }
                    else -> {
                        val headers = mutableListOf<Pair<String, String>>()
                        response.allHeaders.forEach {
                            headers.add(it.name to it.value)
                        }

                        retriedPostFilesCounter.labels(item.fileName).inc(1.toDouble())

                        logger.warn("Failed to post the provided item",
                                "file_name" to item.fullPath,
                                "response" to response.statusLine.statusCode.toString(),
                                "nifi_url" to nifiUrl,
                                "export_date" to exportDate,
                                "snapshot_type" to snapshotType,
                                "status_table_name" to statusTableName, *headers.toTypedArray())

                        throw WriterException("Failed to post '${item.fullPath}': post returned status code ${response.statusLine.statusCode}")
                    }
                }
            }
        }
    }

    private fun getDatabaseAndCollection(filename: String) =
        try {
            TextParsingUtility.databaseAndCollection(filename)
        } catch (ex: MetadataException) {
            rejectedFilesCounter.labels(filename).inc(1.toDouble())
            throw ex
        }}

    @Value("\${nifi.url}")
    private lateinit var nifiUrl: String

    @Value("\${export.date}")
    private lateinit var exportDate: String

    @Value("\${snapshot.type}")
    private lateinit var snapshotType: String

    @Value("\${dynamodb.status.table.name:UCExportToCrownStatus}")
    private lateinit var statusTableName: String

    companion object {
        val logger = DataworksLogger.getLogger(HttpWriter::class.toString())
    }
}
