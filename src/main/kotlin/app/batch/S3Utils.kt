package app.batch

import com.amazonaws.services.s3.AmazonS3
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class S3Utils {

    @Autowired
    lateinit var env: Environment

    @Autowired
    lateinit var s3Client: AmazonS3


    val s3PrefixFolder: String by lazy {
        val prefix = env.getProperty("s3.prefix.folder")!!
        if (prefix.endsWith("/")) {
            prefix
        } else {
            "$prefix/"
        }
    }

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

//    fun prefixFolder(): String {
//        return if (s3PrefixFolder.endsWith("/")) {
//            s3PrefixFolder
//        }
//        else {
//            "$s3PrefixFolder/"
//        }
//    }
}
