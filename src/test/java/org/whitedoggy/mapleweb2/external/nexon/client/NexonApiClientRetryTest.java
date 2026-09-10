package org.whitedoggy.mapleweb2.external.nexon.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 넥슨이 429 를 주면 다시 부르는지.
 *
 * <p>이 재시도가 없으면 429 한 번이 그 문서를 빈 값으로 바꾼다 - {@code fetchSnapshot} 이
 * 호출마다 {@code onErrorResume(→ empty)} 로 감싸고 있어서 실패가 오류로 올라오지 않기
 * 때문이다. 그러면 전투력이 조각난 데이터로 계산되어 <b>틀린 숫자가 조용히</b> 화면에 뜬다.
 * 초당 호출 상한을 키의 한도(500)에 가깝게 올릴수록 429 가 실제로 날아오므로, 이 동작을
 * 테스트로 묶어 둔다.
 */
class NexonApiClientRetryTest {

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    /** 앞의 {@code rateLimitedTimes} 번은 429, 그 뒤로는 200 을 주는 가짜 넥슨. */
    private void startServer(int rateLimitedTimes) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int n = hits.incrementAndGet();
            byte[] body = (n <= rateLimitedTimes
                    ? "{\"error\":{\"name\":\"OPENAPI00007\"}}"
                    : "{\"character_level\":287}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(n <= rateLimitedTimes ? 429 : 200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private NexonApiClient client() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        // 제한기는 이 테스트의 관심사가 아니라 통과시킨다.
        return new NexonApiClient(webClient, new NexonRateLimiter(0));
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void reset() {
        hits.set(0);
    }

    @Test
    @DisplayName("429 두 번 뒤 200 이면 값을 받아 낸다")
    void retriesUntilSuccess() throws IOException {
        startServer(2);

        StepVerifier.create(client().get(NexonEndpoint.BASIC, "ocid", LocalDate.now(), false))
                .assertNext(node -> assertThat(node.path("character_level").asInt()).isEqualTo(287))
                .verifyComplete();

        assertThat(hits.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("계속 429 면 결국 실패하지만, 한 번에 포기하지는 않는다")
    void givesUpAfterRetries() throws IOException {
        startServer(Integer.MAX_VALUE);

        StepVerifier.create(client().get(NexonEndpoint.BASIC, "ocid", LocalDate.now(), false))
                .expectError()
                .verify();

        // 처음 1회 + 재시도 3회
        assertThat(hits.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("429 가 아닌 오류는 재시도하지 않는다 - 400 은 다시 불러도 400 이다")
    void doesNotRetryOtherErrors() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            byte[] body = "{\"error\":{\"name\":\"OPENAPI00004\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        StepVerifier.create(client().get(NexonEndpoint.BASIC, "ocid", LocalDate.now(), false))
                .expectError()
                .verify();

        assertThat(hits.get()).isEqualTo(1);
    }
}
