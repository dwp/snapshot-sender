package dwp.snapshot.sender.configuration

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

fun main(args: Array<String>) {

    var bucketName = "demobucket"
    try {
        val s3Client = getS3Client()
        getS3ObjectSummaries(s3Client, bucketName).listIterator().next()?.let { it ->
            val s3is = getS3ObjectInputStream(it, s3Client, bucketName)
            writeS3Object(it, s3is)
        }
    } catch (e: AmazonServiceException) {
        println(e.getErrorMessage());
        System.exit(1);
    } catch (e: FileNotFoundException) {
        println(e.message);
        System.exit(1);
    } catch (e: IOException) {
        println(e.message);
        System.exit(1);
    }
}


private fun writeS3Object(os: S3ObjectSummary, s3is: S3ObjectInputStream) {
    val fileName = os.key.split("/")[2];
    println("Filename -->" + fileName)
    val fos = FileOutputStream(fileName)
    val read_buf = ByteArray(1024)
    while (true) {
        var read_len = s3is.read(read_buf)
        if (read_len > 0) {
            fos.write(read_buf, 0, read_len)
        }
        break
    }
    fos.close();
    s3is.close();
}

private fun getS3ObjectInputStream(os: S3ObjectSummary, s3Client: AmazonS3, bucketName: String): S3ObjectInputStream {
    println("* " + os.key)
    val o = s3Client.getObject(bucketName, os.key)
    val s3is = o.objectContent
    return s3is
}

private fun getS3ObjectSummaries(s3Client: AmazonS3, bucketName: String): List<S3ObjectSummary> {
    val result = s3Client.listObjectsV2(bucketName)
    return result.objectSummaries
}

private fun getS3Client(): AmazonS3 {
    var region: String = "eu-west-2"
    var serviceEndpoint: String = "http://localhost:4572"
    var accessKey: String = "dummy"
    var secretKey: String = "dummy"

    return AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
            .withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
            .withCredentials(
                    AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
            .withPathStyleAccessEnabled(true)
            .disableChunkedEncoding()
            .build()
}
