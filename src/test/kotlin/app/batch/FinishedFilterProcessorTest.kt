package app.batch

import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import com.amazonaws.services.s3.AmazonS3
import com.nhaarman.mockitokotlin2.given
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [FinishedFilterProcessor::class, S3Utils::class])
@TestPropertySource(properties = [
    "s3.bucket=phoney",
    "s3.prefix.folder=test/output/",
    "s3.status.folder=status",
    "s3.htme.root.folder=test"
])
class FinishedFilterProcessorTest {

    @MockBean
    private lateinit var amazonS3: AmazonS3

    @Autowired
    private lateinit var finishedFilterProcessor: FinishedFilterProcessor

    @Autowired
    private lateinit var s3utils: S3Utils

    @Test
    fun testUnprocessedFileIsProcessed() {
        logger.info("hello")
        val initializationVector = "initializationVector"
        val datakeyEncryptionKeyId = "datakeyEncryptionKeyId"
        val cipherText = "cipherText"
        val plaintext = "plaintext"
        val encryptionMetadata = EncryptionMetadata(initializationVector, datakeyEncryptionKeyId, cipherText, plaintext)
        val fileName = "db.core.addressDeclaration-000001.txt.bz2.enc"
        val fullpath = "${s3utils.s3PrefixFolder}$fileName"
        val encryptedStream = EncryptedStream("inputstream".toByteArray(), fileName, fullpath, encryptionMetadata)
        val finishedFlag = s3utils.getFinishedStatusKeyName(fullpath)
        logger.info("finishedFlag: '$finishedFlag'.")
        given(amazonS3.doesObjectExist(s3bucket, finishedFlag)).willReturn(false)
        val result = finishedFilterProcessor.process(encryptedStream)
        assertEquals(encryptedStream, result)
    }

    @Test
    fun testProcessedFileIsNotProcessed() {
        val initializationVector = "initializationVector"
        val datakeyEncryptionKeyId = "datakeyEncryptionKeyId"
        val cipherText = "cipherText"
        val plaintext = "plaintext"
        val encryptionMetadata = EncryptionMetadata(initializationVector, datakeyEncryptionKeyId, cipherText, plaintext)
        val fileName = "db.core.addressDeclaration-000001.txt.bz2.enc"
        val fullpath = "${s3utils.s3PrefixFolder}$fileName"
        val encryptedStream = EncryptedStream("inputstream".toByteArray(), fileName, fullpath, encryptionMetadata)
        val finishedFlag = s3utils.getFinishedStatusKeyName(fullpath)
        logger.info("finishedFlag: '$finishedFlag'.")
        given(amazonS3.doesObjectExist(s3bucket, finishedFlag)).willReturn(true)
        val result = finishedFilterProcessor.process(encryptedStream)
        assertNull(result)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FinishedFilterProcessor::class.toString())
    }

    @Value("\${s3.bucket}") //where the HTME exports and the Sender picks up from
    lateinit var s3bucket: String

}
