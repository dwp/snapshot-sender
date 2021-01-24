package app.utils

import app.exceptions.BlockedTopicException
import arrow.core.success
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotThrow
import io.kotlintest.shouldThrow
import org.junit.Test
import org.springframework.test.util.ReflectionTestUtils

class FilterBlockedTopicsUtilsTest {

    companion object {
        private const val TOPIC = "topic.string"
        private const val FULL_PATH = "some path"
        private const val BLOCKED_TOPIC = "blocked.topic"
    }

    @Test
    fun shouldNotThrowExceptionWhenBlockedTopicsNotSet() {


        val util = FilterBlockedTopicsUtils()

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(TOPIC, FULL_PATH)
            success()
        }

        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicDoesNotMatchBlockedTopicList() {

        val topic = "good.topic"

        val blockedTopic = "blocked.topic,another.blocked.topic"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, FULL_PATH)
            success()
        }

        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsADifferentCaseToABlockedTopicForSingleBlockedTopic() {

        val blockedTopic = "Topic.string"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(TOPIC, FULL_PATH)
            success()
        }

        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsADifferentCaseToABlockedTopicForMultipleBlockedTopics() {

        val topic = "topic.String"

        val blockedTopic = "topic.string,another.topic.string"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, FULL_PATH)
            success()
        }

        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsASubstringOfABlockedTopicForSingleBlockedTopic() {

        val blockedTopic = "topic.string.full"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(TOPIC, FULL_PATH)
            success()
        }

        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsASubstringOfABlockedTopicForMultipleBlockedTopic() {

        val blockedTopic = "blocked.topic,topic.string.full"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(TOPIC, FULL_PATH)
            success()
        }

        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldThrowBlockedTopicExceptionWhenTopicIsInSingularBlockedList() {
        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", BLOCKED_TOPIC)

        val exception = shouldThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(BLOCKED_TOPIC, FULL_PATH)
        }

        exception.message shouldBe "Provided topic is blocked so cannot be processed: 'blocked.topic'"
    }

    @Test
    fun shouldThrowBlockedTopicExceptionWhenTopicIsInBlockedList() {

        val blockedTopic = "blocked.topic,another.blocked.topic"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(BLOCKED_TOPIC, FULL_PATH)
        }

        exception.message shouldBe "Provided topic is blocked so cannot be processed: 'blocked.topic'"
    }

}
