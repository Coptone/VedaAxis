package dev.vedaaxis.api.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfiguration {
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
