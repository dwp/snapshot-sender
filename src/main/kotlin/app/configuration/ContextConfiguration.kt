package app.configuration

import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class ContextConfiguration {

    @Bean
    @Profile("insecureHttpClient")
    fun insecureHttpClientProvider(): HttpClientProvider {
        return object : HttpClientProvider {
            override fun client(): CloseableHttpClient {
                return HttpClients.createDefault()
            }
        }
    }
}
