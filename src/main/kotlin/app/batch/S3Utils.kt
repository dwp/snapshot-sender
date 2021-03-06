package app.batch

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

@Component
class S3Utils {

    @Autowired
    lateinit var s3Client: AmazonS3

    @Value("\${s3.prefix.folder}")
    lateinit var s3PrefixFolder: String

    @Value("\${s3.status.folder}") //where the sender records its progress i.e. "business-sender-status"
    lateinit var s3StatusFolder: String

    @Value("\${s3.htme.root.folder}") //the root location the htme will output into i.e. "business-data-export"
    lateinit var s3HtmeRootFolder: String

    fun getFinishedStatusKeyName(htmeExportKey: String): String {
        return getFinishedStatusKeyName(htmeExportKey, s3HtmeRootFolder, s3StatusFolder)
    }

    fun getFinishedStatusKeyName(htmeExportKey: String, htmeRootFolder: String, statusFolder: String): String {
        val senderKey = htmeExportKey.replace(htmeRootFolder, statusFolder)
        return "$senderKey.finished"
    }

    fun objectContents(from: S3Object?): ByteArray {

        val outputStream = ByteArrayOutputStream()
        from?.objectContent.use {
            it?.copyTo(outputStream)
        }

        return outputStream.toByteArray()
    }

}
