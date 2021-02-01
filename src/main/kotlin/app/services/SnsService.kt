package app.services

interface SnsService {
    fun sendMonitoringMessage(completionStatus: SendingCompletionStatus)
}