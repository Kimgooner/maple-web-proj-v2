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

    /**
     * 응답 하나를 메모리에 담는 상한.
     *
     * <p>WebClient 기본값은 256KB 인데 장비 문서가 그보다 크다 — 2026-09-16 부터 흡혈의
     * item-equipment 가 271KB 로 넘어와 {@code DataBufferLimitException} 이 나고, 그 문서는
     * 조용히 결손 처리돼 장비 없는 전투력(16억 → 746만)이 화면에 찍혔다. 장비 문서는
     * 프리셋 셋을 다 실어 캐릭터가 꾸밀수록 커지므로 넉넉히 둔다.
     */
    private static final int MAX_IN_MEMORY_SIZE = 8 * 1024 * 1024;

    @Bean
    public WebClient nexonWebClient() {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(requestTimeout)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .defaultHeader("x-nxopen-api-key", apiKey)
                .build();
    }
}
