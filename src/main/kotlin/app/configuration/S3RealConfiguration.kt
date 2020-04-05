package app.configuration

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("realS3Client")
class S3RealConfiguration {

    @Bean
    fun awss3(): AmazonS3 {
        val updatedRegion = region.toUpperCase().replace("-", "_")
        val clientRegion = Regions.valueOf(updatedRegion)

        val clientConfiguration = ClientConfiguration().apply {
            maxConnections = maximumS3Connections.toInt()
        }

        return AmazonS3ClientBuilder.standard()
                .withCredentials(DefaultAWSCredentialsProviderChain())
                .withRegion(clientRegion)
                .withClientConfiguration(clientConfiguration.withSocketTimeout(socketTimeOut.toInt()))
                .build()
    }

    @Value("\${aws.region}")
    private lateinit var region: String

    @Value("\${aws.s3.max.connections:50}")
    private lateinit var maximumS3Connections: String

    @Value("\${s3.socket.timeout:1800000}")
    private lateinit var socketTimeOut: String
}
