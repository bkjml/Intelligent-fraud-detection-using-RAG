package com.kifiya.hackhaton.fraud_service.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${external.ai.base-url}")
    private String aiBaseUrl;

    @Value("${external.rag.base-url}")
    private String ragBaseUrl;


    @Bean("ragWebClient")
    public WebClient ragWebClient() {
        return WebClient.builder()
                .baseUrl(ragBaseUrl)
                .build();
    }

    @Bean("aiWebClient")
    public WebClient aiWebClient() {
        return WebClient.builder()
                .baseUrl(aiBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .responseTimeout(Duration.ofSeconds(3))
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                ))
                .build();
    }

}
