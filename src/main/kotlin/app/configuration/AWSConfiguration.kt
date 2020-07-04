package app.configuration

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("awsConfiguration")
class AWSConfiguration {

    @Bean
    fun amazonS3(): AmazonS3  =
            AmazonS3ClientBuilder.standard()
                .withCredentials(DefaultAWSCredentialsProviderChain())
                .withRegion(awsRegion)
                .withClientConfiguration(ClientConfiguration().apply {
                    maxConnections = maximumS3Connections.toInt()
                    socketTimeout = socketTimeOut.toInt()
                })
                .build()

    @Bean
    fun amazonDynamoDb(): AmazonDynamoDB =
            AmazonDynamoDBClientBuilder.standard()
            .withCredentials(DefaultAWSCredentialsProviderChain())
            .withRegion(awsRegion)
            .build()

    private val awsRegion by lazy {
        Regions.valueOf(region.toUpperCase().replace("-", "_"))
    }

    @Value("\${aws.region:eu-west-2}")
    private lateinit var region: String

    @Value("\${aws.s3.max.connections:50}")
    private lateinit var maximumS3Connections: String

    @Value("\${s3.socket.timeout:1800000}")
    private lateinit var socketTimeOut: String
}
