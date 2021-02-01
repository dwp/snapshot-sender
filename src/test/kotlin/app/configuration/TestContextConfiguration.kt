package app.configuration

import app.services.KeyService
import org.apache.http.client.HttpClient
import org.mockito.Mockito
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class TestContextConfiguration {

    @Bean
    @Profile("unitTest")
    fun httpClientProvider() = Mockito.mock(HttpClientProvider::class.java)!!

    @Bean
    @Profile("unitTest")
    fun httpClient() = Mockito.mock(HttpClient::class.java)!!

    @Bean
    @Profile("decryptionTest")
    fun dataKeyService(): KeyService {
        return Mockito.mock(KeyService::class.java)
    }
}
