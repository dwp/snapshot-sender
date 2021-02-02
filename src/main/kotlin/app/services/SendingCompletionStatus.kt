package app.services

enum class SendingCompletionStatus (val description: String) {
    COMPLETED_SUCCESSFULLY("success"),
    COMPLETED_UNSUCCESSFULLY("failed"),
    NOT_COMPLETED("in progress")
}