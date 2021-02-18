package app.services

interface ExportStatusService {
    fun incrementSentCount(fileSent: String)
    fun setCollectionStatus(): CollectionStatus
//    fun setSuccessStatus()
    fun sendingCompletionStatus(): SendingCompletionStatus
}
