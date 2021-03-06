package app.batch

import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import app.exceptions.DataKeyDecryptionException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.model.ListObjectsV2Result
import com.amazonaws.services.s3.model.S3ObjectSummary
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import io.prometheus.client.spring.web.PrometheusTimeMethod
import io.prometheus.client.Counter

@Component
@Profile("!noOpReader")
class S3DirectoryReader(private val s3Client: AmazonS3,
                        private val s3Utils: S3Utils,
                        private val s3ItemsCounter: Counter) : ItemReader<EncryptedStream> {

    private var iterator: ListIterator<S3ObjectSummary>? = null

    @Value("\${s3.bucket}") //where the HTME exports and the Sender picks up from
    lateinit var s3BucketName: String

    @Synchronized
    @PrometheusTimeMethod(name = "snapshot_sender_read_s3_duration", help = "Duration of reading files from s3")
    override fun read(): EncryptedStream? {
        val iterator = getS3ObjectSummariesIterator(s3Client, s3BucketName)
        return if (iterator.hasNext()) {
            val next = iterator.next()
            val metadata = getS3ObjectMetadata(next, s3Client, s3BucketName)
            logger.info("Returning s3 object from directory", "file_name" to next.key,
                    "metadata" to metadata.toString())
            val bufferedStream = s3Utils.objectContents(s3Client.getObject(s3BucketName, next.key))
            encryptedStream(metadata, next.key, bufferedStream)
        } else {
            null
        }
    }

    @Synchronized
    fun reset() {
        iterator = null
    }

    @Synchronized
    private fun getS3ObjectSummariesIterator(s3Client: AmazonS3, bucketName: String): ListIterator<S3ObjectSummary> {
        if (null == iterator) {
            var results: ListObjectsV2Result?
            val objectSummaries: MutableList<S3ObjectSummary> = mutableListOf()
            val request = ListObjectsV2Request().apply {
                withBucketName(bucketName)
                withPrefix(s3Utils.s3PrefixFolder)
            }
            do {
                logger.info("Getting paginated object summaries result.",
                        "bucket_name" to bucketName, "s3_prefix_folder" to s3Utils.s3PrefixFolder)
                results = s3Client.listObjectsV2(request)
                objectSummaries.addAll(results.objectSummaries)
                request.continuationToken = results.nextContinuationToken
            } while (results != null && results.isTruncated)

            logger.info("S3 object count",
                    "bucket_name" to bucketName,
                    "s3_prefix_folder" to s3Utils.s3PrefixFolder, 
                    "object_count" to objectSummaries.count().toString(),
                    "export_date" to exportDate,
                    "snapshot_type" to snapshotType
            )

            s3ItemsCounter.labels(s3Utils.s3PrefixFolder).inc(objectSummaries.count().toDouble())

            iterator = objectSummaries.listIterator()
        }
        return iterator!!
    }

    private fun getS3ObjectMetadata(objectSummary: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): Map<String, String> {
        return s3Client.getObjectMetadata(bucketName, objectSummary.key).userMetadata
    }

    private fun encryptedStream(metadata: Map<String, String>, filePath: String, contents: ByteArray): EncryptedStream {
        try {
            val iv = metadata[IV_KEY]!!
            val dataKeyEncryptionKey = metadata[DATA_ENCRYPTION_KEY_ID_KEY]!!
            val cipherText = metadata[CIPHERTEXT_KEY]!!
            val encryptionMetadata = EncryptionMetadata(iv, dataKeyEncryptionKey, cipherText, "")
            val fileSplitArr = filePath.split("/")
            val fileName = fileSplitArr[fileSplitArr.size - 1]
            return EncryptedStream(contents, fileName, filePath, encryptionMetadata)
        } catch (e: Exception) {
            throw DataKeyDecryptionException("Couldn't get the metadata for '$filePath'")
        }
    }

    @Value("\${export.date}")
    private lateinit var exportDate: String

    @Value("\${snapshot.type}")
    private lateinit var snapshotType: String

    companion object {
        private const val IV_KEY = "iv"
        private const val DATA_ENCRYPTION_KEY_ID_KEY = "dataKeyEncryptionKeyId"
        private const val CIPHERTEXT_KEY = "cipherText"
        val logger = DataworksLogger.getLogger(S3DirectoryReader::class.toString())
    }
}
