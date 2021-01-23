package app.services.impl

import app.services.ExportStatusService
import com.amazonaws.SdkClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemResult
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@EnableRetry
@SpringBootTest(classes = [DynamoDBExportStatusService::class])
@TestPropertySource(properties = [
    "dynamodb.retry.maxAttempts=5",
    "dynamodb.retry.delay=5",
    "dynamodb.retry.multiplier=1"
])
class DynamoDBExportStatusServiceTest {

    @SpyBean
    @Autowired
    private lateinit var exportStatusService: ExportStatusService

    @MockBean
    private lateinit var amazonDynamoDB: AmazonDynamoDB

    @Before
    fun before() {
        System.setProperty("correlation_id", "123")
        System.setProperty("topic_name", "topic")
        reset(amazonDynamoDB)
    }

    @Test
    fun incrementSentCountRetries() {
        given(amazonDynamoDB.updateItem(any()))
                .willThrow(SdkClientException(""))
                .willThrow(SdkClientException(""))
                .willReturn(mock())
        exportStatusService.incrementSentCount("")
        verify(exportStatusService, times(3)).incrementSentCount("")
    }

    @Test
    fun setSentStatusRetries() {
        given(amazonDynamoDB.getItem(any()))
                .willThrow(SdkClientException(""))
                .willThrow(SdkClientException(""))
                .willReturn(mock())
        exportStatusService.setSentStatus()
        verify(exportStatusService, times(3)).setSentStatus()
    }

    @Test
    fun setSentStatusSetsStatusIfFinished() {

        val status = mock<AttributeValue> {
            on { s } doReturn "Exported"
        }

        val filesExported = mock<AttributeValue> {
            on { n } doReturn "10"
        }

        val filesSent = mock<AttributeValue> {
            on { n } doReturn "10"
        }
        val record =
                mapOf("CollectionStatus" to status,
                "FilesExported" to filesExported,
                "FilesSent" to filesSent)

        val getItemResult = mock<GetItemResult> {
            on { item } doReturn record
        }

        given(amazonDynamoDB.getItem(any())).willReturn(getItemResult)
        given(amazonDynamoDB.updateItem(any())).willReturn(mock())
        exportStatusService.setSentStatus()
        verify(amazonDynamoDB, times(1)).updateItem(any())
    }

    @Test
    fun setSentStatusDoesNotSetStatusIfNotExported() {

        val status = mock<AttributeValue> {
            on { s } doReturn "Exporting"
        }

        val filesExported = mock<AttributeValue> {
            on { n } doReturn "10"
        }

        val filesSent = mock<AttributeValue> {
            on { n } doReturn "10"
        }
        val record =
                mapOf("CollectionStatus" to status,
                        "FilesExported" to filesExported,
                        "FilesSent" to filesSent)

        val getItemResult = mock<GetItemResult> {
            on { item } doReturn record
        }

        given(amazonDynamoDB.getItem(any())).willReturn(getItemResult)
        given(amazonDynamoDB.updateItem(any())).willReturn(mock())
        exportStatusService.setSentStatus()
        verify(amazonDynamoDB, times(0)).updateItem(any())
    }

    @Test
    fun setSentStatusDoesNotSetStatusIfNotAllFilesSent() {

        val status = mock<AttributeValue> {
            on { s } doReturn "Exported"
        }

        val filesExported = mock<AttributeValue> {
            on { n } doReturn "11"
        }

        val filesSent = mock<AttributeValue> {
            on { n } doReturn "10"
        }
        val record =
                mapOf("CollectionStatus" to status,
                        "FilesExported" to filesExported,
                        "FilesSent" to filesSent)

        val getItemResult = mock<GetItemResult> {
            on { item } doReturn record
        }

        val updateItemResult = mock<UpdateItemResult>()

        given(amazonDynamoDB.getItem(any())).willReturn(getItemResult)
        given(amazonDynamoDB.updateItem(any())).willReturn(updateItemResult)
        exportStatusService.setSentStatus()
        verify(amazonDynamoDB, times(0)).updateItem(any())
    }
}
