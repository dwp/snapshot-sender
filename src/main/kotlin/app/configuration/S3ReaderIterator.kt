package dwp.snapshot.sender.configuration

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.model.S3ObjectSummary
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

class S3ReaderIterator : ItemReader<S3ObjectInputStream>{

    @Autowired
    private lateinit var s3Client: AmazonS3

    override fun read(): S3ObjectInputStream? {
         getS3ObjectSummaries(s3Client, s3BucketName).listIterator().next()?.let { it ->
             return getS3ObjectInputStream(it, s3Client, s3BucketName)
         }
    }

    private fun getS3ObjectInputStream(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): S3ObjectInputStream {
        println("* " + os.key)
        val o = s3Client.getObject(bucketName, os.key)
        val s3is = o.objectContent
        return s3is
    }

    private fun getS3ObjectSummaries(s3Client: AmazonS3, bucketName: String): List<S3ObjectSummary> {
        val result = s3Client.listObjectsV2(bucketName)
        return result.objectSummaries
    }

    @Value("\${s3.bucket}")
    private lateinit var s3BucketName: String
}