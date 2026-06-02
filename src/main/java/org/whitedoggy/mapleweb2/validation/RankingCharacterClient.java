package org.whitedoggy.mapleweb2.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonRateLimiter;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class RankingCharacterClient {
    private static final String WORLD_NAME = "스카니아";

    private final WebClient nexonWebClient;
    private final NexonRateLimiter nexonRateLimiter;

    public Mono<JsonNode> getOverallRanking(LocalDate date, String classFilter, int page) {
        return nexonRateLimiter.acquire()
                .then(nexonWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/maplestory/v1/ranking/overall")
                                .queryParam("date", date)
                                .queryParam("world_name", WORLD_NAME)
                                .queryParam("class", classFilter)
                                .queryParam("page", page)
                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class));
    }
}
