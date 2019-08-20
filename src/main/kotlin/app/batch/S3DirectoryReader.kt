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
    private var  objectSummaries : List<S3ObjectSummary>? = null

    override fun read(): EncryptedStream? {
       return  getS3ObjectSummaries(s3Client,s3BucketName)?.listIterator()?.next()?.let { it ->
            val inputStream = getS3ObjectInputStream(it, s3Client, s3BucketName)
            val metadata = getS3ObjectMetadata(it, s3Client, s3BucketName)
            return encryptedStream(metadata, inputStream)
        }

    }

    private fun getS3ObjectSummaries(s3Client: AmazonS3, bucketName: String): List<S3ObjectSummary> {
        if (null == objectSummaries) {
            objectSummaries =  s3Client.listObjectsV2(bucketName).objectSummaries
        }
        return objectSummaries!!
    }

    private fun getS3ObjectInputStream(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): S3ObjectInputStream {
        return s3Client.getObject(bucketName, os.key).objectContent
    }

    private fun getS3ObjectMetadata(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): Map<String, String> {
       return s3Client.getObjectMetadata(bucketName, os.key).userMetadata
    }

    private fun encryptedStream(metadata: Map<String, String>, inputStream: S3ObjectInputStream): EncryptedStream {
        try {
            val iv = metadata.get("iv")!!
            val dataKeyEncryptionKey = metadata.get("dataKeyEncryptionKey")!!
            val ciphertext = metadata.get("ciphertext")!!
            val encryptionMetadata = EncryptionMetadata(iv, dataKeyEncryptionKey, ciphertext, "")
            return EncryptedStream(inputStream, encryptionMetadata)
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