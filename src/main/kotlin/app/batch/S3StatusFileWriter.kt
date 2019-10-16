package app.batch

import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStream

@Component
@Profile("S3DirectoryReader", "httpWriter", "dummyS3Client")
class S3StatusFileWriter : S3Utils() {

    fun writeStatus(originalFileKey: String) {

        val payloadText = "finished $originalFileKey"
        val payloadBytes = payloadText.toByteArray(Charsets.UTF_8)
        val payloadInputStream: InputStream = ByteArrayInputStream(payloadBytes)

        // i.e. sourceFile: s3://bucket/business-data-export/JobNumber/1990-01-31/db.user.data-0001.bz2.enc
        // i.e. statusFile: s3://bucket/business-sender-status/JobNumber/1990-01-31/db.user.data-0001.bz2.enc.finished
        val statusFileKey = getFinishedStatusKeyName(originalFileKey)
        logger.info("Writting status file $statusFileKey for $originalFileKey")

        try {
            // Upload a file as a new object with ContentType and title specified.
            val metadata = ObjectMetadata()
            metadata.contentType = "text/plain"
            metadata.contentLength = payloadBytes.size.toLong()
            metadata.addUserMetadata("x-amz-meta-title", statusFileKey)
            metadata.addUserMetadata("original-filename", originalFileKey)
            val request = PutObjectRequest(s3BucketName, statusFileKey, payloadInputStream, metadata)

            s3Client.putObject(request)
            logger.info("Written status file $statusFileKey for $originalFileKey")
        }
        catch (e: AmazonServiceException) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace()
            logger.error(e.message)
        }
        catch (e: SdkClientException) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace()
            logger.error(e.message)
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(S3StatusFileWriter::class.toString())
    }

}