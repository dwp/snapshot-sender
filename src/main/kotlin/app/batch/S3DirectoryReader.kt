package app.batch

import app.domain.*
import app.exceptions.*
import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*
import org.slf4j.*
import org.springframework.batch.item.*
import org.springframework.context.annotation.*
import org.springframework.stereotype.*

@Component
@Profile("S3SourceData")
class S3DirectoryReader : ItemReader<EncryptedStream>, S3Utils() {

    private var iterator: ListIterator<S3ObjectSummary>? = null
    private val IV_KEY = "iv"
    private val DATAENCRYPTIONKEYID_KEY = "dataKeyEncryptionKeyId"
    private val CIPHERTEXT_KEY = "cipherText"

    override fun read(): EncryptedStream? {
        val iterator = getS3ObjectSummariesIterator(s3Client, s3BucketName)
        do {
            val nextObject = if (iterator.hasNext()) {
                iterator.next()
            } else {
                return null
            }
            logger.info("Checking s3 object '${nextObject.key}'")
            val finishedKeyName = getFinishedStatusKeyName(nextObject.key)
            val objectNeedsSkipping = getS3ObjectExists(finishedKeyName, s3Client, s3BucketName)
            if (objectNeedsSkipping) {
                logger.info("Skipping s3 object '${nextObject.key}' as it was already sent: File '$finishedKeyName' exists")
                continue
            }

            val inputStream = getS3ObjectInputStream(nextObject, s3Client, s3BucketName)
            val metadata = getS3ObjectMetadata(nextObject, s3Client, s3BucketName)
            logger.info("Returning s3 object for '${nextObject.key}' with metadata '$metadata'")
            return encryptedStream(metadata, nextObject.key, inputStream)
        } while (objectNeedsSkipping)
        return null
    }

    @Synchronized
    fun reset() {
        iterator = null
    }

    @Synchronized
    private fun getS3ObjectSummariesIterator(s3Client: AmazonS3, bucketName: String): ListIterator<S3ObjectSummary> {
        if (null == iterator) {
            iterator = s3Client.listObjectsV2(bucketName, prefixFolder()).objectSummaries.listIterator()
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
            return EncryptedStream(inputStream, fileName, filePath, encryptionMetadata)
        } catch (e: Exception) {
            throw DataKeyDecryptionException("Couldn't get the metadata for '$filePath'")
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(S3DirectoryReader::class.toString())
    }

}
