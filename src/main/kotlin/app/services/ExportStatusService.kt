package app.services

interface ExportStatusService {
    fun incrementSentCount(fileSent: String)
    fun setSentStatus(): Boolean
}
