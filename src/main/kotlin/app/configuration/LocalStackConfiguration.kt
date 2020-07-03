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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("localStackConfiguration")
class LocalStackConfiguration {

    @Bean
    fun amazonS3(): AmazonS3 =
        AmazonS3ClientBuilder.standard()
            .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
            .withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
            .withCredentials(
                AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
            .withPathStyleAccessEnabled(true)
            .disableChunkedEncoding()
            .build()

    @Bean
    fun amazonDynamoDb(): AmazonDynamoDB {
        return AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration("http://aws:4566/", region))
                .withClientConfiguration(ClientConfiguration().withProtocol(Protocol.HTTP))
                .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
                .build()
    }

    @Value("\${aws.region}")
    private lateinit var region: String

    @Value("\${s3.service.endpoint}")
    private lateinit var serviceEndpoint: String

    @Value("\${s3.access.key}")
    private lateinit var accessKey: String

    @Value("\${s3.secret.key}")
    private lateinit var secretKey: String
}
