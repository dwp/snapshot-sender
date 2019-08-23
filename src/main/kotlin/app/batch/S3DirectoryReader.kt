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
    private var iterator : ListIterator<S3ObjectSummary>? = null
    private val IV = "iv"
    private val DATAENCRYPTIONKEYID = "dataKeyEncryptionKeyId"
    private val CIPHERTEXT = "cipherText"

    override fun read(): EncryptedStream? {
        val iterator = getS3ObjectSummariesIterator(s3Client,s3BucketName)
        return if (iterator.hasNext()) {
            iterator.next().let {
                val inputStream = getS3ObjectInputStream(it, s3Client, s3BucketName)
                val metadata = getS3ObjectMetadata(it, s3Client, s3BucketName)
                logger.info("Returning '$metadata'.")
                return encryptedStream(metadata, it.key, inputStream)
            }
        }
        else {
            null
        }
    }

    fun reset (){
        iterator = null
    }

    @Synchronized
    private fun getS3ObjectSummariesIterator(s3Client: AmazonS3, bucketName: String): ListIterator<S3ObjectSummary> {
        if (null == iterator) {
            iterator =  s3Client.listObjectsV2(bucketName).objectSummaries.listIterator()
        }
        return iterator!!
    }

    private fun getS3ObjectInputStream(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): S3ObjectInputStream {
        return s3Client.getObject(bucketName, os.key).objectContent
    }

    private fun getS3ObjectMetadata(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): Map<String, String> {
       return s3Client.getObjectMetadata(bucketName, os.key).userMetadata
    }

    private fun encryptedStream(metadata: Map<String, String>, filePath:String, inputStream: S3ObjectInputStream): EncryptedStream {
        try {
            val iv = metadata.get(IV)!!
            val dataKeyEncryptionKey = metadata.get(DATAENCRYPTIONKEYID)!!
            val ciphertext = metadata.get(CIPHERTEXT)!!
            val encryptionMetadata = EncryptionMetadata(iv, dataKeyEncryptionKey, ciphertext, "")
            val fileSplitArr = filePath.split("/")
            val fileName = fileSplitArr[fileSplitArr.size -1]
            return EncryptedStream(inputStream, fileName,encryptionMetadata)
        } catch (e: Exception) {
            throw DataKeyDecryptionException("Couldn't get the metadata")
        }
    }

    @Value("\${s3.bucket}")
    private lateinit var s3BucketName: String

    companion object {
        val logger: Logger = LoggerFactory.getLogger(S3DirectoryReader::class.toString())
    }

}