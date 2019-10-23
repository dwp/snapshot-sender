package app.batch

import app.TestUtils.Companion.once
import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import app.exceptions.DataKeyDecryptionException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import org.apache.http.client.methods.HttpGet
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.verifyNoMoreInteractions
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "S3SourceData")
@SpringBootTest
@TestPropertySource(properties = [
    "data.key.service.url=dummy.com:8090",
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output"
])
class S3DirectoryReaderTest {

    private val BUCKET_NAME1 = "bucket1" //must match test property "s3.bucket" above
    private val S3_PREFIX_WITH_SLASH = "exporter-output/job01/" //must match test property "s3.prefix.folder" above + "/"
    private val KEY1 = "exporter-output/job01/file1"
    private val KEY1_FINISHED = "sender-status/job01/file1.finished"
    private val KEY2 = "exporter-output/job01/file2"
    private val KEY2_FINISHED = "sender-status/job01/file2.finished"
    private val KEY3 = "exporter-output/job01/file3"
    private val IV = "iv"
    private val DATAENCRYPTION_KEY = "dataKeyEncryptionKeyId"
    private val CIPHER_TEXT = "cipherText"
    private val OBJECT_CONTENT1 = "SAMPLE_1"
    private val OBJECT_CONTENT2 = "SAMPLE_2"
    private val OBJECT_CONTENT3 = "SAMPLE_3"

    private lateinit var listObjectsV2Result: ListObjectsV2Result
    private lateinit var s3ObjectSummary1: S3ObjectSummary
    private lateinit var s3ObjectSummary2: S3ObjectSummary
    private lateinit var s3ObjectSummary3: S3ObjectSummary
    private lateinit var s3Object1: S3Object
    private lateinit var s3Object2: S3Object
    private lateinit var s3Object3: S3Object
    private lateinit var objectMetadata1: ObjectMetadata
    private lateinit var objectMetadata2: ObjectMetadata
    private lateinit var objectMetadata3: ObjectMetadata

    @Autowired
    private lateinit var s3DirectorReader: S3DirectoryReader

    @MockBean
    private lateinit var mockS3Client: AmazonS3

    @Before
    fun setUp() {

        listObjectsV2Result = ListObjectsV2Result()
        listObjectsV2Result.prefix = S3_PREFIX_WITH_SLASH

        s3ObjectSummary1 = S3ObjectSummary()
        s3ObjectSummary1.bucketName = BUCKET_NAME1
        s3ObjectSummary1.key = KEY1


        s3Object1 = S3Object()
        s3Object1.bucketName = BUCKET_NAME1
        s3Object1.key = KEY1
        s3Object1.objectContent = S3ObjectInputStream(ByteArrayInputStream(OBJECT_CONTENT1.toByteArray()), HttpGet())

        objectMetadata1 = ObjectMetadata()
        objectMetadata1.userMetadata = mapOf(IV to IV, DATAENCRYPTION_KEY to DATAENCRYPTION_KEY, CIPHER_TEXT to CIPHER_TEXT)

        s3ObjectSummary2 = S3ObjectSummary()
        s3ObjectSummary2.bucketName = BUCKET_NAME1
        s3ObjectSummary2.key = KEY2

        s3Object2 = S3Object()
        s3Object2.bucketName = BUCKET_NAME1
        s3Object2.key = KEY2
        s3Object2.objectContent = S3ObjectInputStream(ByteArrayInputStream(OBJECT_CONTENT2.toByteArray()), HttpGet())

        objectMetadata2 = ObjectMetadata()
        objectMetadata2.userMetadata = mapOf(IV to IV, DATAENCRYPTION_KEY to DATAENCRYPTION_KEY, CIPHER_TEXT to CIPHER_TEXT)

        s3ObjectSummary3 = S3ObjectSummary()
        s3ObjectSummary3.bucketName = BUCKET_NAME1
        s3ObjectSummary3.key = KEY3

        s3Object3 = S3Object()
        s3Object3.bucketName = BUCKET_NAME1
        s3Object3.key = KEY3
        s3Object3.objectContent = S3ObjectInputStream(ByteArrayInputStream(OBJECT_CONTENT3.toByteArray()), HttpGet())

        objectMetadata3 = ObjectMetadata()
        objectMetadata3.userMetadata = mapOf(IV to IV, DATAENCRYPTION_KEY to DATAENCRYPTION_KEY, CIPHER_TEXT to CIPHER_TEXT)

        s3DirectorReader.reset()
        Mockito.reset(mockS3Client)
    }

    @Test
    fun should_read_a_file_in_a_given_prefix_if_not_already_processed() {
        //given one object on results
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)

        given(mockS3Client.listObjectsV2(BUCKET_NAME1, S3_PREFIX_WITH_SLASH)).willReturn(listObjectsV2Result)
        given(mockS3Client.doesObjectExist(BUCKET_NAME1, KEY1_FINISHED)).willReturn(false)
        given(mockS3Client.getObject(BUCKET_NAME1, KEY1)).willReturn(s3Object1)
        given(mockS3Client.getObjectMetadata(BUCKET_NAME1, KEY1)).willReturn(objectMetadata1)

        //when it is read
        val encryptedStream1 = s3DirectorReader.read()
        val actualStream1 = encryptedStream1?.inputStream
        val actualMetadata1 = encryptedStream1?.encryptionMetadata

        //then
        verify(mockS3Client, once()).listObjectsV2(BUCKET_NAME1, S3_PREFIX_WITH_SLASH)
        verify(mockS3Client, once()).getObject(BUCKET_NAME1, KEY1)
        verify(mockS3Client, once()).getObjectMetadata(BUCKET_NAME1, KEY1)
        verifyNoMoreInteractions(mockS3Client)

        assertObjectMetadata(objectMetadata1, actualMetadata1)
        assertObjectContent(OBJECT_CONTENT1, actualStream1)
        assertFileNameEndsWith(KEY1, encryptedStream1!!)
        assertEquals("file1", encryptedStream1.fileName)
    }

    @Test
    fun should_read_all_files_in_a_given_prefix_if_not_already_processed() {
        //given two objects ion list
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary2)

        given(mockS3Client.listObjectsV2(BUCKET_NAME1, S3_PREFIX_WITH_SLASH)).willReturn(listObjectsV2Result)
        given(mockS3Client.doesObjectExist(BUCKET_NAME1, KEY1_FINISHED)).willReturn(false)
        given(mockS3Client.doesObjectExist(BUCKET_NAME1, KEY2_FINISHED)).willReturn(false)
        given(mockS3Client.getObject(BUCKET_NAME1, KEY1)).willReturn(s3Object1)
        given(mockS3Client.getObject(BUCKET_NAME1, KEY2)).willReturn(s3Object2)
        given(mockS3Client.getObjectMetadata(BUCKET_NAME1, KEY1)).willReturn(objectMetadata1)
        given(mockS3Client.getObjectMetadata(BUCKET_NAME1, KEY2)).willReturn(objectMetadata2)

        //when read in turn
        val encryptedStream1 = s3DirectorReader.read()
        val encryptedStream2 = s3DirectorReader.read()

        //then
        verify(mockS3Client, once()).listObjectsV2(BUCKET_NAME1, S3_PREFIX_WITH_SLASH)
        verify(mockS3Client, once()).getObject(BUCKET_NAME1, KEY1)
        verify(mockS3Client, once()).getObject(BUCKET_NAME1, KEY2)
        verify(mockS3Client, once()).getObjectMetadata(BUCKET_NAME1, KEY1)
        verify(mockS3Client, once()).getObjectMetadata(BUCKET_NAME1, KEY2)
        verifyNoMoreInteractions(mockS3Client)

        val actualMetadata1 = encryptedStream1?.encryptionMetadata
        val actualStream1 = encryptedStream1?.inputStream

        val actualMetadata2 = encryptedStream2?.encryptionMetadata
        val actualStream2 = encryptedStream2?.inputStream

        assertObjectMetadata(objectMetadata1, actualMetadata1)
        assertObjectMetadata(objectMetadata2, actualMetadata2)

        assertObjectContent(OBJECT_CONTENT1, actualStream1)
        assertObjectContent(OBJECT_CONTENT2, actualStream2)

        assertFileNameEndsWith(KEY1, encryptedStream1!!)
        assertFileNameEndsWith(KEY2, encryptedStream2!!)
    }

    @Test
    fun should_throw_exception_when_metadata_is_empty() {
        //given an object with blank metadata
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)

        given(mockS3Client.listObjectsV2(BUCKET_NAME1, S3_PREFIX_WITH_SLASH)).willReturn(listObjectsV2Result)
        given(mockS3Client.doesObjectExist(BUCKET_NAME1, KEY1_FINISHED)).willReturn(false)
        given(mockS3Client.getObject(anyString(), anyString())).willReturn(s3Object1)
        given(mockS3Client.getObjectMetadata(anyString(), anyString())).willReturn(ObjectMetadata())

        try {
            //when
            s3DirectorReader.read()
            fail("Expected a DataKeyDecryptionException")
        }
        catch (ex: DataKeyDecryptionException) {
            //then
            assertEquals("Couldn't get the metadata for 'exporter-output/job01/file1'", ex.message)
        }

        verify(mockS3Client, once()).listObjectsV2(BUCKET_NAME1, S3_PREFIX_WITH_SLASH)
        verify(mockS3Client, once()).getObject(BUCKET_NAME1, KEY1)
        verify(mockS3Client, once()).getObjectMetadata(BUCKET_NAME1, KEY1)
        verifyNoMoreInteractions(mockS3Client)
    }

    private fun assertFileNameEndsWith(key: String, encryptedStream: EncryptedStream) {
        assertTrue(key.endsWith("/${encryptedStream.fileName}"))
    }

    private fun assertObjectContent(objectContent: String, actualStream: InputStream?) {
        val textBuilder = StringBuilder()
        BufferedReader(InputStreamReader(actualStream, Charset.forName(StandardCharsets.UTF_8.name()))).use { reader ->
            var c = 0
            while (c != -1) {
                c = reader.read()
                if (c != -1) {
                    textBuilder.append(c.toChar())
                }
            }
        }
        assertEquals(objectContent, textBuilder.toString().trim())
    }

    private fun assertObjectMetadata(objectMetadata: ObjectMetadata, actualMetadata1: EncryptionMetadata?) {
        assertEquals(objectMetadata.userMetadata[IV], actualMetadata1?.initializationVector)
        assertEquals(objectMetadata.userMetadata[DATAENCRYPTION_KEY], actualMetadata1?.datakeyEncryptionKeyId)
        assertEquals(objectMetadata.userMetadata[CIPHER_TEXT], actualMetadata1?.cipherText)
    }
}
