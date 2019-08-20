package dwp.snapshot.sender.configuration

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.model.S3ObjectSummary
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

class S3Reader : ItemReader<List<S3ObjectInputStream>>{

    @Autowired
    private lateinit var s3Client: AmazonS3

    override fun read(): List<S3ObjectInputStream> {
        val s3ObjectInputStreamList = mutableListOf<S3ObjectInputStream>()
        try {
            val objects = getS3ObjectSummaries(s3Client, s3BucketName)
            for (os in objects) {
                s3ObjectInputStreamList.add(getS3ObjectInputStream(os, s3Client, s3BucketName))
            }

        } catch (e: Exception) {
            println(e.message);
        }
        return s3ObjectInputStreamList
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