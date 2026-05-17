package org.whitedoggy.mapleweb2.external.nexon.config;

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

    @Value("${nexon.api.base-url:https://open.api.nexon.com}")
    private String baseUrl;

    @Value("${nexon.api.api-key}")
    private String apiKey;

    @Value("${nexon.api.request-timeout:5s}")
    private Duration requestTimeout;

    @Bean
    public WebClient nexonWebClient() {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(requestTimeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("x-nxopen-api-key", apiKey)
                .build();
    }
}
