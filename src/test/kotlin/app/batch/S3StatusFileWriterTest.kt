package app.batch

import app.domain.EncryptionMetadata
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "S3DirectoryReader")
@SpringBootTest
@TestPropertySource(properties = [
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output"
])
class S3StatusFileWriterTest {

    private val BUCKET_NAME1 = "bucket1" //must match test property "s3.bucket" above
    private val S3_PREFIX_WITH_SLASH = "exporter-output/job01/" //must match test property "s3.prefix.folder" above + "/"
    private val KEY1 = "exporter-output/job01/file1"
    private val KEY1_FINISHED = "sender-status/job01/file1.finished"
    private val OBJECT_CONTENT1 = "SAMPLE_1"

    private lateinit var s3Object1: S3Object
    private lateinit var objectMetadata1: ObjectMetadata

    @Autowired
    private lateinit var s3StatusFileWriter: S3StatusFileWriter

    @MockBean
    private lateinit var mockS3Client: AmazonS3

    @Before
    fun setUp() {
        Mockito.reset(mockS3Client)
    }

    @Test
    fun should_calculate_finished_status_file_name_from_htme_file_name() {
        val htmeFileName = "business-data-export/JobNumber/1990-01-31/myfilename.00001.txt.bz2.enc"
        val htmeRootFolder = "business-data-export"
        val statusFolder = "business-sender-status"

        val actual = s3StatusFileWriter.getFinishedStatusKeyName(htmeFileName, htmeRootFolder, statusFolder)
        assertEquals("business-sender-status/JobNumber/1990-01-31/myfilename.00001.txt.bz2.enc.finished", actual)
    }

    @Test
    fun will_write_finished_file_to_s3_based_on_source_file() {



    }

    private fun assertObjectMetadata(objectMetadata: ObjectMetadata, actualMetadata1: EncryptionMetadata?) {
        assertEquals(objectMetadata.userMetadata.get(IV), actualMetadata1?.initializationVector)
        assertEquals(objectMetadata.userMetadata.get(DATAENCRYPTION_KEY), actualMetadata1?.datakeyEncryptionKeyId)
        assertEquals(objectMetadata.userMetadata.get(CIPHER_TEXT), actualMetadata1?.cipherText)
    }
}