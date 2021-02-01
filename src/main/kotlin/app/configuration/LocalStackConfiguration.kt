package app.configuration


import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("localStackConfiguration")
class LocalStackConfiguration {


    with (AmazonS3ClientBuilder.standard()) {
        withPathStyleAccessEnabled(true)
        disableChunkedEncoding()
        localstack()
    }

    @Bean
    fun amazonS3(): AmazonS3 =
        with (AmazonS3ClientBuilder.standard()) {
            withPathStyleAccessEnabled(true)
            disableChunkedEncoding()
            localstack()
        }

    @Bean
    fun amazonDynamoDb(): AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard().localstack()

    @Bean
    fun amazonSns(): AmazonSNS = AmazonSNSClientBuilder.standard().localstack()

    fun <B: AwsClientBuilder<B, C>, C> AwsClientBuilder<B, C>.localstack(): C =
        run {
            withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndPoint, signingRegion))
            withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
            withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
            build()
        }

    private companion object {
        const val serviceEndPoint = "http://aws:4566/"
        const val signingRegion = "eu-west-2"
        const val accessKey = "accessKey"
        const val secretKey = "secretKey"
    }
}
