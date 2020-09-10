package app.utils

import app.batch.HttpWriter
import app.domain.DecryptedStream
import app.exceptions.BlockedTopicException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class FilterBlockedTopicsUtils {

    @Value("\${blocked.topics:NOT_SET}")
    private var blockedTopics: String = "NOT_SET"

    @Throws(BlockedTopicException::class)
    fun checkIfTopicIsBlocked(topic: String, fullPath: String) {
        val blockedTopicsList: MutableList<String> = mutableListOf()

        if (blockedTopics.contains(',')) {
            blockedTopicsList.addAll(blockedTopics.split(","))
        } else {
            blockedTopicsList.add(blockedTopics)
        }

        if (blockedTopicsList.contains(topic)) {
            val errorMessage = "Provided topic is blocked so cannot be processed: '$topic'"
            val exception = BlockedTopicException(errorMessage)
            HttpWriter.logger.error("Provided topic is blocked", exception, "topic_name" to topic, "file_name" to fullPath)
            throw exception
        }
    }
}
