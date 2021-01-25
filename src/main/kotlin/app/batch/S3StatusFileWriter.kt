package app.batch

import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.ByteArrayInputStream
import java.io.InputStream

@Component
class S3StatusFileWriter(val s3utils: S3Utils) {

    @Value("\${s3.bucket}") //where the HTME exports and the Sender picks up from
    lateinit var s3BucketName: String

    fun writeStatus(originalS3Key: String) {

        val payloadText = "Finished $originalS3Key"
        val payloadBytes = payloadText.toByteArray(Charsets.UTF_8)
        val payloadInputStream: InputStream = ByteArrayInputStream(payloadBytes)

        // i.e. sourceFile: s3://bucket/business-data-export/JobNumber/1990-01-31/db.user.data-0001.bz2.enc
        // i.e. statusFile: s3://bucket/business-sender-status/JobNumber/1990-01-31/db.user.data-0001.bz2.enc.finished
        val statusFileKey = s3utils.getFinishedStatusKeyName(originalS3Key)
        logger.info("Writing S3 status file", "file_name" to originalS3Key, "status_file_name" to statusFileKey)

        try {
            // Upload a file as a new object with ContentType and title specified.
            val metadata = ObjectMetadata()
            metadata.contentType = "text/plain"
            metadata.contentLength = payloadBytes.size.toLong()
            metadata.addUserMetadata("x-amz-meta-title", statusFileKey)
            metadata.addUserMetadata("original-s3-filename", originalS3Key)
            val request = PutObjectRequest(s3BucketName, statusFileKey, payloadInputStream, metadata)

            s3utils.s3Client.putObject(request)
            logger.info("Writing S3 status file", "file_name" to originalS3Key, "status_file_name" to statusFileKey)
        } catch (e: AmazonServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace()
            logger.error("AmazonServiceException processing", e, "file_name" to originalS3Key)
        } catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace()
            logger.error("SdkClientException processing", e, "file_name" to originalS3Key)
        }
    }

    companion object {
        val logger = DataworksLogger.getLogger(S3StatusFileWriter::class.toString())
    }
}
