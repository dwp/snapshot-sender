package app.services

enum class SendingCompletionStatus (val description: String) {
    COMPLETED_SUCCESSFULLY("Completed successfully"),
    COMPLETED_UNSUCCESSFULLY("Failed"),
    NOT_COMPLETED("In progress")
}