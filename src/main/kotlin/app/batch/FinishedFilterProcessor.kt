package app.batch

import app.domain.EncryptedStream
import com.amazonaws.services.s3.AmazonS3
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import io.prometheus.client.spring.web.PrometheusTimeMethod

@Component
@Qualifier("filter")
class FinishedFilterProcessor(private val amazonS3: AmazonS3, private val s3utils: S3Utils) : ItemProcessor<EncryptedStream, EncryptedStream> {

    @PrometheusTimeMethod(name = "snapshot_sender_filter_items_duration", help = "Duration of filtering items")
    override fun process(item: EncryptedStream) =
            if ( !reprocessFiles.toBoolean() && fileAlreadyProcessed(item.fullPath)) {
                logger.info("Skipping processing of item as already processed", "file_name" to item.fullPath)
                null
            }
            else {
                item
            }

    private fun fileAlreadyProcessed(s3Key: String) =
            amazonS3.doesObjectExist(s3bucket, s3utils.getFinishedStatusKeyName(s3Key))

    @Value("\${s3.bucket}") //where the HTME exports and the Sender picks up from
    private lateinit var s3bucket: String

    @Value("\${reprocess.files:false}")
    private lateinit var reprocessFiles: String

    companion object {
        val logger = DataworksLogger.getLogger(FinishedFilterProcessor::class.toString())
    }
}
