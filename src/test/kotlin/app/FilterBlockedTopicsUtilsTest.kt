package app

import app.exceptions.BlockedTopicException
import app.utils.FilterBlockedTopicsUtils
import arrow.core.success
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotThrow
import io.kotlintest.shouldThrow
import org.junit.Test
import org.springframework.test.util.ReflectionTestUtils

class FilterBlockedTopicsUtilsTest {

    @Test
    fun shouldNotThrowExceptionWhenBlockedTopicsNotSet() {

        val topic = "some.topic"
        val fullPath = "some path"

        val util = FilterBlockedTopicsUtils()

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
            success()
        }
        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicDoesNotMatchBlockedTopicList() {

        val topic = "good.topic"
        val fullPath = "some path"

        val blockedTopic = "blocked.topic,another.blocked.topic"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
            success()
        }
        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsADifferentCaseToABlockedTopicForSingleBlockedTopic() {

        val topic = "topic.string"
        val fullPath = "some path"

        val blockedTopic = "Topic.string"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
            success()
        }
        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsADifferentCaseToABlockedTopicForMultipleBlockedTopics() {

        val topic = "topic.String"
        val fullPath = "some path"

        val blockedTopic = "topic.string,another.topic.string"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
            success()
        }
        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsASubstringOfABlockedTopicForSingleBlockedTopic() {

        val topic = "topic.string"
        val fullPath = "some path"

        val blockedTopic = "topic.string.full"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
            success()
        }
        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldNotThrowExceptionWhenTopicIsASubstringOfABlockedTopicForMultipleBlockedTopic() {

        val topic = "topic.string"
        val fullPath = "some path"

        val blockedTopic = "blocked.topic,topic.string.full"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldNotThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
            success()
        }
        assert(exception.success().isSuccess())
    }

    @Test
    fun shouldThrowBlockedTopicExceptionWhenTopicIsInSingularBlockedList() {

        val topic = "blocked.topic"
        val fullPath = "some path"

        val blockedTopic = "blocked.topic"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
        }
        exception.message shouldBe "Provided topic is blocked so cannot be processed: 'blocked.topic'"
    }

    @Test
    fun shouldThrowBlockedTopicExceptionWhenTopicIsInBlockedList() {

        val topic = "blocked.topic"
        val fullPath = "some path"

        val blockedTopic = "blocked.topic,another.blocked.topic"

        val util = FilterBlockedTopicsUtils()

        ReflectionTestUtils.setField(util, "blockedTopics", blockedTopic)

        val exception = shouldThrow<BlockedTopicException> {
            util.checkIfTopicIsBlocked(topic, fullPath)
        }
        exception.message shouldBe "Provided topic is blocked so cannot be processed: 'blocked.topic'"
    }
}