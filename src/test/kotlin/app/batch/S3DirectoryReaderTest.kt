package app.batch

import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import app.exceptions.DataKeyDecryptionException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import org.apache.http.client.methods.HttpGet
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.*
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@RunWith(SpringRunner::class)
@ActiveProfiles("httpDataKeyService", "unitTest", "S3DirectoryReader")
@SpringBootTest
@TestPropertySource(properties = [
    "data.key.service.url=dummy.com:8090",
    "s3.bucket=bucket1",
    "s3.prefix.folder=test/output/"

])
class S3DirectoryReaderTest {

    private val BUCKET_NAME1 = "bucket1"
    private val S3_PREFIX_FOLDER = "test/output/"
    private val KEY1 = "key1"
    private val KEY2 = "key2"
    private val IV = "iv"
    private val DATAENCRYPTION_KEY = "dataKeyEncryptionKeyId"
    private val CIPHER_TEXT = "cipherText"
    private val OBJECT_CONTENT1 = "SAMPLE1"
    private val OBJECT_CONTENT2 = "SAMPLE2"

    private lateinit var listObjectsV2Result: ListObjectsV2Result
    private lateinit var s3ObjectSummary1: S3ObjectSummary;
    private lateinit var s3ObjectSummary2: S3ObjectSummary;
    private lateinit var s3Object1: S3Object;
    private lateinit var s3Object2: S3Object;
    private lateinit var objectMetadata1: ObjectMetadata;
    private lateinit var objectMetadata2: ObjectMetadata;

    @Autowired
    private lateinit var s3DirectorReader: S3DirectoryReader

    @MockBean
    private lateinit var s3Client: AmazonS3

    @Before
    fun setUp() {

        listObjectsV2Result = ListObjectsV2Result()
        listObjectsV2Result.prefix = S3_PREFIX_FOLDER

        s3ObjectSummary1 = S3ObjectSummary()
        s3ObjectSummary1.bucketName = BUCKET_NAME1
        s3ObjectSummary1.key = KEY1

        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)

        s3Object1 = S3Object()
        s3Object1.bucketName = BUCKET_NAME1
        s3Object1.key = KEY1
        s3Object1.objectContent = S3ObjectInputStream(ByteArrayInputStream(OBJECT_CONTENT1.toByteArray()), HttpGet())

        objectMetadata1 = ObjectMetadata()
        objectMetadata1.userMetadata = mapOf(IV to IV, DATAENCRYPTION_KEY to DATAENCRYPTION_KEY, CIPHER_TEXT to CIPHER_TEXT)
        s3DirectorReader.reset()
        Mockito.reset(s3Client)
    }

    @Test
    fun should_Read_A_File_In_A_Given_Prefix() {

        given(s3Client.listObjectsV2(BUCKET_NAME1, S3_PREFIX_FOLDER)).willReturn(listObjectsV2Result)
        given(s3Client.getObject(BUCKET_NAME1, KEY1)).willReturn(s3Object1)
        given(s3Client.getObjectMetadata(BUCKET_NAME1, KEY1)).willReturn(objectMetadata1)
        val encryptedStream1 = s3DirectorReader.read()
        val actualStream1 = encryptedStream1?.inputStream
        val actualMetadata1 = encryptedStream1?.encryptionMetadata

        //compare the expected and actual metadata
        assertObjectMetadata(objectMetadata1, actualMetadata1)
        assertObjectContent(OBJECT_CONTENT1, actualStream1)
        assertTrue(KEY1.equals(encryptedStream1?.fileName))
    }

    @Test
    fun should_Read_All_Files_In_A_Given_Prefix() {

        s3ObjectSummary2 = S3ObjectSummary()
        s3ObjectSummary2.bucketName = BUCKET_NAME1
        s3ObjectSummary2.key = KEY2

        listObjectsV2Result.objectSummaries.add(s3ObjectSummary2)

        s3Object2 = S3Object()
        s3Object2.bucketName = BUCKET_NAME1
        s3Object2.key = KEY2
        s3Object2.objectContent = S3ObjectInputStream(ByteArrayInputStream(OBJECT_CONTENT2.toByteArray()), HttpGet())

        objectMetadata2 = ObjectMetadata()
        objectMetadata2.userMetadata = mapOf(IV to IV, DATAENCRYPTION_KEY to DATAENCRYPTION_KEY, CIPHER_TEXT to CIPHER_TEXT)

        given(s3Client.listObjectsV2(BUCKET_NAME1, S3_PREFIX_FOLDER)).willReturn(listObjectsV2Result)
        given(s3Client.getObject(BUCKET_NAME1, KEY1)).willReturn(s3Object1)
        given(s3Client.getObject(BUCKET_NAME1, KEY2)).willReturn(s3Object2)
        given(s3Client.getObjectMetadata(BUCKET_NAME1, KEY1)).willReturn(objectMetadata1)
        given(s3Client.getObjectMetadata(BUCKET_NAME1, KEY2)).willReturn(objectMetadata2)

        val encryptedStream1 = s3DirectorReader.read()
        val actualMetadata1 = encryptedStream1?.encryptionMetadata
        val actualStream1 = encryptedStream1?.inputStream

        val encryptedStream2 = s3DirectorReader.read()
        val actualMetadata2 = encryptedStream2?.encryptionMetadata
        val actualStream2 = encryptedStream2?.inputStream

        //compare the expected and actual metadata
        assertObjectMetadata(objectMetadata1, actualMetadata1)
        assertObjectMetadata(objectMetadata2, actualMetadata2)

        assertObjectContent(OBJECT_CONTENT1, actualStream1)
        assertObjectContent(OBJECT_CONTENT2, actualStream2)

        assertFileName(KEY1, encryptedStream1)
        assertFileName(KEY2, encryptedStream2)
    }

    @Test(expected = DataKeyDecryptionException::class)
    fun should_ThrowException_When_Metadata_Is_Empty() {

        given(s3Client.listObjectsV2(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(listObjectsV2Result)
        given(s3Client.getObject(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(s3Object1)
        given(s3Client.getObjectMetadata(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(ObjectMetadata())

        s3DirectorReader.read()
    }

    private fun assertFileName(key: String, encryptedStream2: EncryptedStream?) {
        assertTrue(key.equals(encryptedStream2?.fileName))
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
        assertTrue(objectContent.equals(textBuilder.toString().trim()))
    }

    private fun assertObjectMetadata(objectMetadata: ObjectMetadata, actualMetadata1: EncryptionMetadata?) {
        assertTrue(objectMetadata.userMetadata.get(IV).equals(actualMetadata1?.initializationVector))
        assertTrue(objectMetadata.userMetadata.get(DATAENCRYPTION_KEY).equals(actualMetadata1?.datakeyEncryptionKeyId))
        assertTrue(objectMetadata.userMetadata.get(CIPHER_TEXT).equals(actualMetadata1?.cipherText))
    }
}