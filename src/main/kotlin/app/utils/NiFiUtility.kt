package app.utils

import app.domain.NifiHeaders
import org.apache.http.client.methods.HttpPost
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NiFiUtility {

    fun setNifiHeaders(httpPost: HttpPost, headers: NifiHeaders) {
        httpPost.setHeader("filename", headers.filename)
        httpPost.setHeader("environment", "aws/${System.getProperty("environment")}")
        httpPost.setHeader("export_date", exportDate)
        httpPost.setHeader("database", headers.database)
        httpPost.setHeader("collection", headers.collection)
        httpPost.setHeader("snapshot_type", snapshotType)
        httpPost.setHeader("topic", headers.topic)
        httpPost.setHeader("status_table_name", statusTableName)
        httpPost.setHeader("correlation_id", PropertyUtility.correlationId())
        httpPost.setHeader("s3_prefix", s3Prefix)
        httpPost.setHeader("shutdown_flag", shutdownFlag)
        httpPost.setHeader("reprocess_files", reprocessFiles)
    }

    @Value("\${export.date}")
    private lateinit var exportDate: String

    @Value("\${snapshot.type}")
    private lateinit var snapshotType: String

    @Value("\${dynamodb.status.table.name:UCExportToCrownStatus}")
    private lateinit var statusTableName: String

    @Value("\${s3.prefix.folder}")
    lateinit var s3Prefix: String

    @Value("\${reprocess.files:false}")
    private lateinit var reprocessFiles: String

    @Value("\${shutdown.flag}")
    private lateinit var shutdownFlag: String

}
