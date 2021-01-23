package app.utils

object PropertyUtility {

    fun correlationId(): String =
        System.getProperty("correlation_id") ?: System.getenv("CORRELATION_ID") ?: throw Exception("No correlation id specified.")


    fun topicName(): String =
        System.getProperty("topic_name") ?: System.getenv("TOPIC_NAME") ?: throw Exception("No topic name specified.")

}
