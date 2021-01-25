package app.batch

import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import app.exceptions.DataKeyDecryptionException
import app.services.ExportStatusService
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import org.apache.http.client.methods.HttpGet
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
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
import java.io.ByteArrayInputStream

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "S3SourceData")
@SpringBootTest
@TestPropertySource(properties = [
    "data.key.service.url=dummy.com:8090",
    "s3.bucket=bucket1",
    "s3.prefix.folder=exporter-output/job01",
    "s3.status.folder=sender-status",
    "s3.htme.root.folder=exporter-output",
    "nifi.url=http://nifi:9090",
    "export.date=2019-01-01",
    "snapshot.type=full",
    "shutdown.flag=false"
])
class S3DirectoryReaderTest {


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

    @MockBean
    private lateinit var s3Utils: S3Utils

    @MockBean
    private lateinit var exportStatusService: ExportStatusService

    @Before
    fun setUp() {

        listObjectsV2Result = ListObjectsV2Result()
        listObjectsV2Result.prefix = S3_PREFIX_WITH_SLASH

        s3ObjectSummary1 = S3ObjectSummary()
        s3ObjectSummary1.bucketName = BUCKET_NAME1
        s3ObjectSummary1.key = KEY1

        val s3ObjectInputStream1 = mock<S3ObjectInputStream>()

        s3Object1 = mock {
            on { bucketName } doReturn BUCKET_NAME1
            on { key } doReturn KEY1
            on { objectContent } doReturn s3ObjectInputStream1
        }

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

        given(s3Utils.objectContents(s3Object1)).willReturn(OBJECT_CONTENT1.toByteArray())
        given(s3Utils.objectContents(s3Object2)).willReturn(OBJECT_CONTENT2.toByteArray())

        s3DirectorReader.reset()
        Mockito.reset(mockS3Client)
    }

    @Test
    fun should_read_a_file_in_a_given_prefix_if_not_already_processed() {
        //given one object on results
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)

        given(mockS3Client.listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))).willReturn(listObjectsV2Result)
        given(mockS3Client.doesObjectExist(BUCKET_NAME1, KEY1_FINISHED)).willReturn(false)
        given(mockS3Client.getObject(BUCKET_NAME1, KEY1)).willReturn(s3Object1)
        given(mockS3Client.getObjectMetadata(BUCKET_NAME1, KEY1)).willReturn(objectMetadata1)

        //when it is read
        val encryptedStream1 = s3DirectorReader.read()
        val actualStream1 = encryptedStream1?.contents ?: ByteArray(0)
        val actualMetadata1 = encryptedStream1?.encryptionMetadata

        //then
        verify(mockS3Client, times(1)).listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))
        verify(mockS3Client, times(1)).getObject(BUCKET_NAME1, KEY1)
        verify(mockS3Client, times(1)).getObjectMetadata(BUCKET_NAME1, KEY1)
        verifyNoMoreInteractions(mockS3Client)

        assertObjectMetadata(objectMetadata1, actualMetadata1)
        assertEquals(OBJECT_CONTENT1, String(actualStream1))
        assertFileNameEndsWith(KEY1, encryptedStream1!!)
        assertEquals("file1", encryptedStream1.fileName)
    }

    @Test
    fun should_read_all_files_in_a_given_prefix_if_not_already_processed() {
        //given two objects ion list
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary2)

        given(mockS3Client.listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))).willReturn(listObjectsV2Result)
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
        verify(mockS3Client, times(1)).listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))
        verify(mockS3Client, times(1)).getObject(BUCKET_NAME1, KEY1)
        verify(mockS3Client, times(1)).getObject(BUCKET_NAME1, KEY2)
        verify(mockS3Client, times(1)).getObjectMetadata(BUCKET_NAME1, KEY1)
        verify(mockS3Client, times(1)).getObjectMetadata(BUCKET_NAME1, KEY2)
        verifyNoMoreInteractions(mockS3Client)

        val actualMetadata1 = encryptedStream1?.encryptionMetadata
        val actualStream1 = encryptedStream1?.contents ?: ByteArray(0)

        val actualMetadata2 = encryptedStream2?.encryptionMetadata
        val actualStream2 = encryptedStream2?.contents ?: ByteArray(0)

        assertObjectMetadata(objectMetadata1, actualMetadata1)
        assertObjectMetadata(objectMetadata2, actualMetadata2)

        assertEquals(OBJECT_CONTENT1, String(actualStream1))
        assertEquals(OBJECT_CONTENT2, String(actualStream2))

        assertFileNameEndsWith(KEY1, encryptedStream1!!)
        assertFileNameEndsWith(KEY2, encryptedStream2!!)
    }

    @Test
    fun should_throw_exception_when_metadata_is_empty() {
        //given an object with blank metadata
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)

        given(mockS3Client.listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))).willReturn(listObjectsV2Result)
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

        verify(mockS3Client, times(1)).listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))
        verify(mockS3Client, times(1)).getObject(BUCKET_NAME1, KEY1)
        verify(mockS3Client, times(1)).getObjectMetadata(BUCKET_NAME1, KEY1)
        verifyNoMoreInteractions(mockS3Client)
    }

    @Test
    fun should_page_when_results_truncated() {

        val bucket = "bucket1"
        val page1Object1Key = "database1.collection1.0001.json.gz.enc"
        val page1Object2Key = "database1.collection1.0001.json.gz.encryption.json"
        val page2Object1Key = "database1.collection2.0001.json.gz.enc"
        val page2Object2Key = "database1.collection2.0001.json.gz.encryption.json"
        val continuationToken = "CONTINUATION_TOKEN"

        val page1ObjectSummary1 = mockS3ObjectSummary(page1Object1Key)
        val page1ObjectSummary2 = mockS3ObjectSummary(page1Object2Key)

        val resultsPage1 = mock<ListObjectsV2Result> {
            on { objectSummaries } doReturn listOf(page1ObjectSummary1, page1ObjectSummary2)
            on { isTruncated } doReturn true
            on { nextContinuationToken } doReturn continuationToken
        }

        val page2ObjectSummary1 = mockS3ObjectSummary(page2Object1Key)
        val page2ObjectSummary2 = mockS3ObjectSummary(page2Object2Key)

        val resultsPage2 = mock<ListObjectsV2Result> {
            on { objectSummaries } doReturn listOf(page2ObjectSummary1, page2ObjectSummary2)
            on { isTruncated } doReturn false
        }

        given(mockS3Client.listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java)))
            .willReturn(resultsPage1)
            .willReturn(resultsPage2)

        val page1Object1 = mockS3Object()
        val page1Object2 = mockS3Object()
        val page2Object1 = mockS3Object()
        val page2Object2 = mockS3Object()

        given(mockS3Client.getObject(bucket, page1Object1Key)).willReturn(page1Object1)
        given(mockS3Client.getObject(bucket, page1Object2Key)).willReturn(page1Object2)
        given(mockS3Client.getObject(bucket, page2Object1Key)).willReturn(page2Object1)
        given(mockS3Client.getObject(bucket, page2Object2Key)).willReturn(page2Object2)

        val objectMetadata = mock<ObjectMetadata> {
            on { userMetadata } doReturn mapOf("iv" to "INITIALISATION_VECTOR",
                "dataKeyEncryptionKeyId" to "DATAKEY_ENCRYPTION_KEY_ID",
                "cipherText" to "CIPHER_TEXT")
        }

        given(mockS3Client.getObjectMetadata(anyString(), anyString()))
            .willReturn(objectMetadata)

        given(s3Utils.objectContents(any())).willReturn("TEXT".toByteArray())

        s3DirectorReader.read()

        verify(mockS3Client, times(2))
            .listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))

    }

    @Test
    fun should_not_page_when_results_not_truncated() {

        val bucket = "bucket1"
        val page1Object1Key = "database1.collection1.0001.json.gz.enc"
        val page1Object2Key = "database1.collection1.0001.json.gz.encryption.json"

        val page1ObjectSummary1 = mockS3ObjectSummary(page1Object1Key)
        val page1ObjectSummary2 = mockS3ObjectSummary(page1Object2Key)

        val resultsPage1 = mock<ListObjectsV2Result> {
            on { objectSummaries } doReturn listOf(page1ObjectSummary1, page1ObjectSummary2)
            on { isTruncated } doReturn false
        }


        given(mockS3Client.listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java)))
            .willReturn(resultsPage1)

        val page1Object1 = mockS3Object()
        val page1Object2 = mockS3Object()

        given(mockS3Client.getObject(bucket, page1Object1Key)).willReturn(page1Object1)
        given(mockS3Client.getObject(bucket, page1Object2Key)).willReturn(page1Object2)

        val objectMetadata = mock<ObjectMetadata> {
            on { userMetadata } doReturn mapOf("iv" to "INITIALISATION_VECTOR",
                "dataKeyEncryptionKeyId" to "DATAKEY_ENCRYPTION_KEY_ID",
                "cipherText" to "CIPHER_TEXT")
        }

        given(mockS3Client.getObjectMetadata(anyString(), anyString()))
            .willReturn(objectMetadata)

        given(s3Utils.objectContents(any())).willReturn("TEXT".toByteArray())

        s3DirectorReader.read()

        verify(mockS3Client, times(1))
            .listObjectsV2(ArgumentMatchers.any(ListObjectsV2Request::class.java))

    }

    private fun mockS3Object(): S3Object {
        val inputStream = mock<S3ObjectInputStream>()
        return mock {
            on { objectContent } doReturn inputStream
        }
    }

    private fun mockS3ObjectSummary(objectKey: String) =
        mock<S3ObjectSummary> {
            on { key } doReturn objectKey
        }

    private fun assertFileNameEndsWith(key: String, encryptedStream: EncryptedStream) {
        assertTrue(key.endsWith("/${encryptedStream.fileName}"))
    }

    private fun assertObjectMetadata(objectMetadata: ObjectMetadata, actualMetadata1: EncryptionMetadata?) {
        assertEquals(objectMetadata.userMetadata[IV], actualMetadata1?.initializationVector)
        assertEquals(objectMetadata.userMetadata[DATAENCRYPTION_KEY], actualMetadata1?.datakeyEncryptionKeyId)
        assertEquals(objectMetadata.userMetadata[CIPHER_TEXT], actualMetadata1?.cipherText)
    }

    companion object {
        private const val BUCKET_NAME1 = "bucket1" //must match test property "s3.bucket" above
        private const val S3_PREFIX_WITH_SLASH = "exporter-output/job01/" //must match test property "s3.prefix.folder" above + "/"
        private const val KEY1 = "exporter-output/job01/file1"
        private const val KEY1_FINISHED = "sender-status/job01/file1.finished"
        private const val KEY2 = "exporter-output/job01/file2"
        private const val KEY2_FINISHED = "sender-status/job01/file2.finished"
        private const val KEY3 = "exporter-output/job01/file3"
        private const val IV = "iv"
        private const val DATAENCRYPTION_KEY = "dataKeyEncryptionKeyId"
        private const val CIPHER_TEXT = "cipherText"
        private const val OBJECT_CONTENT1 = "SAMPLE_1"
        private const val OBJECT_CONTENT2 = "SAMPLE_2"
        private const val OBJECT_CONTENT3 = "SAMPLE_3"

    }
}
