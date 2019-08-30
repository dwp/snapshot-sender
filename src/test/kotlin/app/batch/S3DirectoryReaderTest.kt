package app.batch

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
    private val IV = "iv"
    private val DATAENCRYPTION_KEY = "dataKeyEncryptionKeyId"
    private val CIPHER_TEXT = "cipherText"
    private val OBJECT_CONTENT = "SAMPLE"

    private lateinit var listObjectsV2Result:ListObjectsV2Result
    private lateinit var s3ObjectSummary1: S3ObjectSummary;
    private lateinit var s3Object:S3Object;
    private lateinit var objectMetadata: ObjectMetadata;

    @Autowired
    private lateinit var s3DirectorReader: S3DirectoryReader

    @MockBean
    private lateinit var s3Client: AmazonS3

    @Before
    fun prepare() {

        listObjectsV2Result = ListObjectsV2Result()
        listObjectsV2Result.prefix = S3_PREFIX_FOLDER
        s3ObjectSummary1 = S3ObjectSummary()
        s3ObjectSummary1.bucketName = BUCKET_NAME1
        s3ObjectSummary1.key = KEY1
        listObjectsV2Result.objectSummaries.add(s3ObjectSummary1)

        s3Object = S3Object()
        s3Object.bucketName = BUCKET_NAME1
        s3Object.key = KEY1
        s3Object.objectContent = S3ObjectInputStream(ByteArrayInputStream(OBJECT_CONTENT.toByteArray()),HttpGet())

        objectMetadata = ObjectMetadata()
        objectMetadata.userMetadata = mapOf(IV to IV, DATAENCRYPTION_KEY to DATAENCRYPTION_KEY, CIPHER_TEXT to CIPHER_TEXT)
        s3DirectorReader.reset()
        Mockito.reset(s3Client)

    }

    @Test
    fun testRead() {

        given(s3Client.listObjectsV2(ArgumentMatchers.anyString(),ArgumentMatchers.anyString())).willReturn(listObjectsV2Result)
        given(s3Client.getObject(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(s3Object)
        given(s3Client.getObjectMetadata(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(objectMetadata)
        val encryptedStream = s3DirectorReader.read()
        val actualStream  = encryptedStream?.inputStream
        val actualMetadata  = encryptedStream?.encryptionMetadata

        //compare the expected and actual metadata
        assertTrue(objectMetadata.userMetadata.get(IV).equals(actualMetadata?.initializationVector))
        assertTrue(objectMetadata.userMetadata.get(DATAENCRYPTION_KEY).equals(actualMetadata?.datakeyEncryptionKeyId))
        assertTrue(objectMetadata.userMetadata.get(CIPHER_TEXT).equals(actualMetadata?.cipherText))

        val textBuilder = StringBuilder()
        BufferedReader(InputStreamReader(actualStream, Charset.forName(StandardCharsets.UTF_8.name()))).use { reader ->
            var c = 0
            while (c != -1) {
                c = reader.read()
                if(c != -1) {
                    textBuilder.append(c.toChar())
                }
            }
        }
        assertTrue(OBJECT_CONTENT.equals(textBuilder.toString().trim()))
        assertTrue(KEY1.equals(encryptedStream?.fileName))

    }


    @Test(expected = DataKeyDecryptionException::class)
    fun testException() {

        given(s3Client.listObjectsV2(ArgumentMatchers.anyString(),ArgumentMatchers.anyString())).willReturn(listObjectsV2Result)
        given(s3Client.getObject(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(s3Object)
        given(s3Client.getObjectMetadata(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).willReturn(ObjectMetadata())

        s3DirectorReader.read()
    }
}