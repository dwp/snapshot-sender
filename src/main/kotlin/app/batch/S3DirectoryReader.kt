package app.batch

import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import app.exceptions.DataKeyDecryptionException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.model.S3ObjectSummary
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("S3DirectoryReader")
class S3DirectoryReader : ItemReader<EncryptedStream> {

    @Autowired
    private lateinit var s3Client: AmazonS3
    private var iterator: ListIterator<S3ObjectSummary>? = null
    private val IV_KEY = "iv"
    private val DATAENCRYPTIONKEYID_KEY = "dataKeyEncryptionKeyId"
    private val CIPHERTEXT_KEY = "cipherText"

    override fun read(): EncryptedStream? {
        val iterator = getS3ObjectSummariesIterator(s3Client, s3BucketName)
        do {
            val nextObject = if (iterator.hasNext()) {
                iterator.next()
            }
            else {
                return null
            }
            logger.info("Checking object for '${nextObject.key}'")
            val finishedKeyName = getFinishedStatusKeyName(nextObject.key, s3HtmeRootFolder, s3StatusFolder)
            val objectNeedsSkipping = getS3ObjectExists(finishedKeyName, s3Client, s3BucketName)
            if (objectNeedsSkipping) {
                logger.info("Skipping '${nextObject.key}' as it was already sent: File '$finishedKeyName' exists")
                continue
            }

            val inputStream = getS3ObjectInputStream(nextObject, s3Client, s3BucketName)
            val metadata = getS3ObjectMetadata(nextObject, s3Client, s3BucketName)
            logger.info("Returning object for '${nextObject.key}'")
            logger.info("Returning metadata '$metadata'")
            return encryptedStream(metadata, nextObject.key, inputStream)

        }
        while (objectNeedsSkipping)
        return null
    }

    fun getFinishedStatusKeyName(htmeExportKey: String, htmeRootFolder: String, statusFolder: String): String {
        val senderKey = htmeExportKey.replace(htmeRootFolder, statusFolder)
        return "$senderKey.finished"
    }

    @Synchronized
    fun reset() {
        iterator = null
    }

    @Synchronized
    private fun getS3ObjectSummariesIterator(s3Client: AmazonS3, bucketName: String): ListIterator<S3ObjectSummary> {
        if (null == iterator) {
            iterator = s3Client.listObjectsV2(bucketName, s3PrefixFolder).objectSummaries.listIterator()
        }
        return iterator!!
    }

    private fun getS3ObjectExists(s3Key: String, s3Client: AmazonS3, bucketName: String): Boolean {
        return s3Client.doesObjectExist(bucketName, s3Key)
    }

    private fun getS3ObjectInputStream(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): S3ObjectInputStream {
        return s3Client.getObject(bucketName, os.key).objectContent
    }

    private fun getS3ObjectMetadata(objectSummary: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): Map<String, String> {
        return s3Client.getObjectMetadata(bucketName, objectSummary.key).userMetadata
    }

    private fun encryptedStream(metadata: Map<String, String>, filePath: String, inputStream: S3ObjectInputStream): EncryptedStream {
        try {
            val iv = metadata.get(IV_KEY)!!
            val dataKeyEncryptionKey = metadata.get(DATAENCRYPTIONKEYID_KEY)!!
            val cipherText = metadata.get(CIPHERTEXT_KEY)!!
            val encryptionMetadata = EncryptionMetadata(iv, dataKeyEncryptionKey, cipherText, "")
            val fileSplitArr = filePath.split("/")
            val fileName = fileSplitArr[fileSplitArr.size - 1]
            return EncryptedStream(inputStream, fileName, encryptionMetadata)
        }
        catch (e: Exception) {
            throw DataKeyDecryptionException("Couldn't get the metadata")
        }
    }

    @Value("\${s3.bucket}") //where the HTME exports and the Sender picks up from
    private lateinit var s3BucketName: String

    @Value("\${s3.prefix.folder}") //where the sender searches for work to do i.e. "business-data-export/JobNumber/1990-01-31"
    private lateinit var s3PrefixFolder: String

    @Value("\${s3.status.folder}") //where the sender records its progress i.e. "business-sender-status"
    private lateinit var s3StatusFolder: String

    @Value("\${s3.htme.root.folder}") //the root location the htme will output into i.e. "business-data-export"
    private lateinit var s3HtmeRootFolder: String

    companion object {
        val logger: Logger = LoggerFactory.getLogger(S3DirectoryReader::class.toString())
    }

}