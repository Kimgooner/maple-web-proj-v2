package org.whitedoggy.mapleweb2.global.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

/**
 * 프런트 빌드 산출물({@code static/app/})의 진입점.
 * WebFlux 정적 핸들러는 하위 경로의 디렉터리 인덱스를 주지 않아서
 * {@code /app} 과 {@code /app/} 을 {@code /app/index.html} 로 넘긴다.
 * 화면 라우팅은 해시({@code /app/#/c/이름})라 서버 쪽 fallback 은 필요 없다.
 */
@Configuration
public class FrontendRouter {

    static final String ENTRY = "/app/index.html";

    @Bean
    public RouterFunction<ServerResponse> frontendEntryRoute() {
        return RouterFunctions.route()
                .GET("/app", request -> ServerResponse.temporaryRedirect(URI.create(ENTRY)).build())
                .GET("/app/", request -> ServerResponse.temporaryRedirect(URI.create(ENTRY)).build())
                .build();
    }
}
