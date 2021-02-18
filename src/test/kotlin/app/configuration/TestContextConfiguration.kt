package app.configuration

import app.services.KeyService
import org.apache.http.client.HttpClient
import org.mockito.Mockito
import org.springframework.context.annotation.*
import com.amazonaws.services.sns.AmazonSNS
import io.prometheus.client.Counter

@Configuration
class TestContextConfiguration {

    @Bean
    @Profile("unitTest")
    fun httpClientProvider() = Mockito.mock(HttpClientProvider::class.java)!!

    @Bean
    @Profile("unitTest")
    fun httpClient() = Mockito.mock(HttpClient::class.java)!!

    @Bean
    @Profile("unitTest")
    fun amazonSns() = Mockito.mock(AmazonSNS::class.java)!!

    @Bean
    @Profile("decryptionTest")
    fun dataKeyService(): KeyService {
        return Mockito.mock(KeyService::class.java)
    }
}
