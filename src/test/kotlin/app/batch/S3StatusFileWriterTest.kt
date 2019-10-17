package app.batch

import app.TestUtils.Companion.once
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import com.amazonaws.AmazonServiceException
import com.amazonaws.SdkClientException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.PutObjectRequest
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.argumentCaptor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "S3SourceData")
@SpringBootTest
@TestPropertySource(properties = [
    "data.key.service.url=datakey.service:8090",
    "nifi.url=nifi:8091/dummy",
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output"
])
class S3StatusFileWriterTest {

    //these must match test properties above
    private val BUCKET_NAME1 = "bucket1"
    private val HTME_FOLDER = "exporter-output"
    private val STATUS_FOLDER = "sender-status"
    val htmeFileName = "$HTME_FOLDER/myfilename.enc"

    @Autowired
    private lateinit var s3StatusFileWriter: S3StatusFileWriter

    @MockBean
    private lateinit var mockS3Client: AmazonS3

    val mockAppender: Appender<ILoggingEvent> = com.nhaarman.mockitokotlin2.mock()

    @Before
    fun setUp() {
        val root = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.addAppender(mockAppender)

        reset(mockS3Client)
        reset(mockAppender)
    }

    @Test
    fun should_calculate_finished_status_file_name_from_htme_file_name() {
        val htmeFileName = "$HTME_FOLDER/1990-01-31/myfilename.enc"

        val actual = s3StatusFileWriter.getFinishedStatusKeyName(htmeFileName, HTME_FOLDER, STATUS_FOLDER)
        assertEquals("$STATUS_FOLDER/1990-01-31/myfilename.enc.finished", actual)
    }

    @Test
    fun will_write_finished_file_to_s3_based_on_source_file() {
        //when
        s3StatusFileWriter.writeStatus(htmeFileName)

        //then
        val awsCaptor = argumentCaptor<PutObjectRequest>()
        verify(mockS3Client, once()).putObject(awsCaptor.capture())

        assertCorrectPutObject(awsCaptor)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, times(2)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("Writing status file 'sender-status/myfilename.enc.finished' for 'exporter-output/myfilename.enc'", formattedMessages[0])
        assertEquals("Written status file 'sender-status/myfilename.enc.finished' for 'exporter-output/myfilename.enc'", formattedMessages[1])
    }

    @Test
    fun will_log_error_when_aws_throws_service_exception() {
        //given
        given(mockS3Client.putObject(any())).willThrow(AmazonServiceException("boom"))

        //when
        s3StatusFileWriter.writeStatus(htmeFileName)

        //then
        val awsCaptor = argumentCaptor<PutObjectRequest>()
        verify(mockS3Client, once()).putObject(awsCaptor.capture())

        assertCorrectPutObject(awsCaptor)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, times(2)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("Writing status file 'sender-status/myfilename.enc.finished' for 'exporter-output/myfilename.enc'", formattedMessages[0])
        assertEquals("AmazonServiceException processing 'exporter-output/myfilename.enc': boom (Service: null; Status Code: 0; Error Code: null; Request ID: null)", formattedMessages[1])
    }

    @Test
    fun will_log_error_when_aws_throws_sdk_exception() {
        //given
        given(mockS3Client.putObject(any())).willThrow(SdkClientException("boom"))

        //when
        s3StatusFileWriter.writeStatus(htmeFileName)

        //then
        val awsCaptor = argumentCaptor<PutObjectRequest>()
        verify(mockS3Client, once()).putObject(awsCaptor.capture())

        assertCorrectPutObject(awsCaptor)

        val logCaptor = argumentCaptor<ILoggingEvent>()
        verify(mockAppender, times(2)).doAppend(logCaptor.capture())
        val formattedMessages = logCaptor.allValues.map { it.formattedMessage }
        assertEquals("Writing status file 'sender-status/myfilename.enc.finished' for 'exporter-output/myfilename.enc'", formattedMessages[0])
        assertEquals("SdkClientException processing 'exporter-output/myfilename.enc': boom", formattedMessages[1])
    }

    private fun assertCorrectPutObject(awsCaptor: KArgumentCaptor<PutObjectRequest>) {
        assertEquals(BUCKET_NAME1, awsCaptor.firstValue.bucketName)
        assertEquals("sender-status/myfilename.enc.finished", awsCaptor.firstValue.key)
        val actualMetadata = awsCaptor.firstValue.metadata
        assertEquals("text/plain", actualMetadata.contentType)
        assertEquals(39, actualMetadata.contentLength)
        assertEquals("sender-status/myfilename.enc.finished", actualMetadata.userMetadata.get("x-amz-meta-title"))
        assertEquals("exporter-output/myfilename.enc", actualMetadata.userMetadata.get("original-s3-filename"))
    }
}
