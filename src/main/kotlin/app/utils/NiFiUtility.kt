package app.utils

import app.domain.NifiHeaders
import org.apache.http.client.methods.HttpPost

object NiFiUtility {
    fun HttpPost.setNifiHeaders(headers: NifiHeaders) {
        setHeader("filename", headers.filename)
        setHeader("environment", headers.environment)
        setHeader("export_date", headers.exportDate)
        setHeader("database", headers.database)
        setHeader("collection", headers.collection)
        setHeader("snapshot_type", headers.snapshotType)
        setHeader("topic", headers.topic)
        setHeader("status_table_name", headers.statusTableName)
        setHeader("correlation_id", headers.correlationId)
    }
}
