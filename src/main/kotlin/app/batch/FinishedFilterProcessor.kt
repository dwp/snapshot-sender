package app.batch

import app.domain.EncryptedStream
import com.amazonaws.services.s3.AmazonS3
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@Qualifier("filter")
class FinishedFilterProcessor(private val amazonS3: AmazonS3, private val s3utils: S3Utils): ItemProcessor<EncryptedStream, EncryptedStream> {

    override fun process(item: EncryptedStream) =
        if (fileAlreadyProcessed(item.fullPath)) {
            logger.info("Skipping '${item.fullPath}', has already been processed.")
            null
        }
        else {
            item
        }

    private fun fileAlreadyProcessed(s3Key: String) =
            amazonS3.doesObjectExist(s3bucket, s3utils.getFinishedStatusKeyName(s3Key))

    @Value("\${s3.bucket}") //where the HTME exports and the Sender picks up from
    lateinit var s3bucket: String

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FinishedFilterProcessor::class.toString())
    }
}
