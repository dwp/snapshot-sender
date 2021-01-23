package app.utils

object PropertyUtility {

    val correlationId: String by lazy {
        System.getProperty("correlation_id") ?: System.getenv("CORRELATION_ID") ?: throw Exception("No correlation id specified.")
    }

    val topicName: String by lazy {
        System.getProperty("topic_name") ?: System.getenv("TOPIC_NAME") ?: throw Exception("No topic name specified.")
    }
}
