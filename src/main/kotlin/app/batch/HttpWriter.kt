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

@Component
class HttpWriter(private val httpClientProvider: HttpClientProvider,
                 private val exportStatusService: ExportStatusService,
                 private val filterBlockedTopicsUtils: FilterBlockedTopicsUtils,
                 private val nifiUtility: NiFiUtility) : ItemWriter<DecryptedStream> {

    @Autowired
    lateinit var s3StatusFileWriter: S3StatusFileWriter

    @Throws(Exception::class)
    override fun write(items: MutableList<out DecryptedStream>) {
        logger.info("Writing items to S3", "number_of_items" to items.size.toString())
        items.forEach(::postItem)
    }

    private fun postItem(item: DecryptedStream) {

        logger.info("Checking item to  write", "file_name" to item.fileName, "full_path" to item.fullPath)
        val (database, collection) = TextParsingUtility.databaseAndCollection(item.fileName)
        val topicPrefix = if (item.fileName.startsWith("db.")) "db." else ""
        val topic = "$topicPrefix$database.$collection"

        filterBlockedTopicsUtils.checkIfTopicIsBlocked(topic, item.fullPath)

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
                    collection = collection,
                    topic = topic))
            }

            it.execute(post).use { response ->
                when (response.statusLine.statusCode) {
                    200 -> {
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
