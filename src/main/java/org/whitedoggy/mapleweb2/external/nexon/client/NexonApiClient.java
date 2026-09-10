package org.whitedoggy.mapleweb2.external.nexon.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.external.nexon.dto.OcidResponse;
import org.whitedoggy.mapleweb2.external.nexon.exception.NexonApiException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NexonApiClient {
    private final WebClient nexonWebClient;
    private final NexonRateLimiter nexonRateLimiter;

    /**
     * 넥슨이 429(OPENAPI00007) 를 주면 잠깐 쉬었다 다시 부른다.
     *
     * <p>재시도가 없으면 429 한 번이 그 문서를 통째로 날린다 - {@code fetchSnapshot} 의
     * 호출마다 {@code onErrorResume(→ empty)} 가 붙어 있어서, 실패가 오류로 올라오지 않고
     * <b>빈 문서로 바뀐다.</b> 그러면 전투력이 조각난 데이터로 계산되어 "틀린 숫자"가
     * 조용히 화면에 뜬다. 우리 서비스에서 제일 나쁜 실패 모양이라, 여기서 막는다.
     *
     * <p>{@code Mono.defer} 로 감싸는 것은 재시도마다 속도 제한을 다시 받게 하려는 것이다.
     * 안 그러면 재시도만 제한 밖으로 새어 나가 상황을 더 나쁘게 만든다.
     */
    private static final Retry RATE_LIMIT_RETRY = Retry
            .backoff(3, Duration.ofMillis(300))
            .maxBackoff(Duration.ofSeconds(3))
            .jitter(0.5)
            .filter(NexonApiClient::isRateLimited)
            .doBeforeRetry(signal -> log.warn("넥슨 429 - {}번째 재시도", signal.totalRetries() + 1));

    private static boolean isRateLimited(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }

    private <T> Mono<T> paced(Mono<T> call) {
        return Mono.defer(() -> nexonRateLimiter.acquire().then(call)).retryWhen(RATE_LIMIT_RETRY);
    }

    public Mono<OcidResponse> getOcid(String characterName) {
        return paced(nexonWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/maplestory/v1/id")
                                .queryParam("character_name", characterName)
                                .build())
                        .retrieve()
                        .bodyToMono(OcidResponse.class));
    }

    public Mono<JsonNode> get(NexonEndpoint endpoint, String ocid, LocalDate date, boolean includeDateParam) {
        return paced(nexonWebClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(endpoint.path())
                                    .queryParam("ocid", ocid);
                            // 스킬은 등급이 없으면 400 이다. 경로가 같고 등급만 다른 엔드포인트가 둘 있다.
                            if (endpoint.skillGrade() != null) {
                                uriBuilder.queryParam("character_skill_grade", endpoint.skillGrade());
                            }
                            if (includeDateParam) {
                                uriBuilder.queryParam("date", date);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(JsonNode.class));
    }

    private static String requiredText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new NexonApiException("Nexon API response did not include required field: " + field);
        }
        return node.get(field).asText();
    }

    private static JsonNode nullNode() {
        return tools.jackson.databind.node.NullNode.getInstance();
    }

    public Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, true);
    }

    public Mono<CharacterSnapshot> fetchCurrentSnapshot(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, false);
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        return Mono.zip(
                        get(NexonEndpoint.BASIC, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.STAT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.ITEM_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.CASH_ITEM_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.SET_EFFECT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.SYMBOL_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.PET_EQUIPMENT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode())
                )
                .zipWith(Mono.zip(
                        get(NexonEndpoint.HYPER_STAT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.ABILITY, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.SKILL_0, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.HEXA_MATRIX_STAT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.UNION_RAIDER, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.UNION_CHAMPION, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode()),
                        get(NexonEndpoint.UNION_ARTIFACT, ocid, date, includeDateParam).onErrorResume(throwable -> Mono.empty()).defaultIfEmpty(nullNode())
                ))
                .map(tuple -> {
                    Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
                    documents.put(NexonEndpoint.BASIC, tuple.getT1().getT1());
                    documents.put(NexonEndpoint.STAT, tuple.getT1().getT2());
                    documents.put(NexonEndpoint.ITEM_EQUIPMENT, tuple.getT1().getT3());
                    documents.put(NexonEndpoint.CASH_ITEM_EQUIPMENT, tuple.getT1().getT4());
                    documents.put(NexonEndpoint.SET_EFFECT, tuple.getT1().getT5());
                    documents.put(NexonEndpoint.SYMBOL_EQUIPMENT, tuple.getT1().getT6());
                    documents.put(NexonEndpoint.PET_EQUIPMENT, tuple.getT1().getT7());
                    documents.put(NexonEndpoint.HYPER_STAT, tuple.getT2().getT1());
                    documents.put(NexonEndpoint.ABILITY, tuple.getT2().getT2());
                    documents.put(NexonEndpoint.SKILL_0, tuple.getT2().getT3());
                    documents.put(NexonEndpoint.HEXA_MATRIX_STAT, tuple.getT2().getT4());
                    documents.put(NexonEndpoint.UNION_RAIDER, tuple.getT2().getT5());
                    documents.put(NexonEndpoint.UNION_CHAMPION, tuple.getT2().getT6());
                    documents.put(NexonEndpoint.UNION_ARTIFACT, tuple.getT2().getT7());
                    return new CharacterSnapshot(ocid, date, documents);
                });
    }
}
