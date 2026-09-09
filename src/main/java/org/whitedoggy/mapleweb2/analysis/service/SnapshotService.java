package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SnapshotService {
    private final NexonApiClient nexonApiClient;

    public Mono<CharacterSnapshot> getSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, true);
    }

    public Mono<CharacterSnapshot> getCurrentSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, false);
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        // 구독마다 새로 만든다. 어느 문서를 못 받았는지는 조립이 끝나야 알 수 있다.
        return Mono.defer(() -> fetchSnapshot(ocid, date, includeDateParam, ConcurrentHashMap.newKeySet()));
    }

    /**
     * 한 시점의 캐릭터를 이루는 문서들. 순서가 곧 {@code Mono.zip} 결과 배열의 순서다.
     *
     * <p>목록으로 두는 이유는 {@code Mono.zip} 의 인자가 여덟 개까지라, 튜플로 엮으면
     * 열여섯 번째부터는 중첩을 한 겹 더 쌓아야 하기 때문이다. 엔드포인트를 더할 때
     * 여기 한 줄만 넣으면 된다.
     */
    private static final List<NexonEndpoint> SNAPSHOT_ENDPOINTS = List.of(
            NexonEndpoint.BASIC,
            NexonEndpoint.STAT,
            NexonEndpoint.ITEM_EQUIPMENT,
            NexonEndpoint.CASH_ITEM_EQUIPMENT,
            NexonEndpoint.SET_EFFECT,
            NexonEndpoint.SYMBOL_EQUIPMENT,
            NexonEndpoint.PET_EQUIPMENT,
            NexonEndpoint.OTHER_STAT,
            NexonEndpoint.HYPER_STAT,
            NexonEndpoint.ABILITY,
            NexonEndpoint.SKILL_0,
            NexonEndpoint.SKILL_6,
            NexonEndpoint.HEXA_MATRIX_STAT,
            NexonEndpoint.HEXA_MATRIX,
            NexonEndpoint.UNION_RAIDER,
            NexonEndpoint.UNION_CHAMPION,
            NexonEndpoint.UNION_ARTIFACT);

    private Mono<CharacterSnapshot> fetchSnapshot(
            String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        List<Mono<JsonNode>> calls = SNAPSHOT_ENDPOINTS.stream()
                .map(endpoint -> fetchEndpoint(endpoint, ocid, date, includeDateParam, missing))
                .toList();
        return Mono.zip(calls, values -> {
            Map<NexonEndpoint, JsonNode> documents = new EnumMap<>(NexonEndpoint.class);
            for (int index = 0; index < SNAPSHOT_ENDPOINTS.size(); index++) {
                documents.put(SNAPSHOT_ENDPOINTS.get(index), (JsonNode) values[index]);
            }
            return new CharacterSnapshot(ocid, date, documents, Set.copyOf(missing));
        });
    }

    private Mono<JsonNode> fetchEndpoint(NexonEndpoint endpoint, String ocid, LocalDate date,
                                         boolean includeDateParam, Set<NexonEndpoint> missing) {
        return nexonApiClient.get(endpoint, ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorResume(error -> missingDocument(endpoint, error, missing));
    }

    /**
     * 실패한 문서를 {@code NullNode} 로 채워 나머지 계산을 살린다.
     *
     * <p>다만 API 가 4xx 로 "그런 데이터는 없다"고 답한 것은 결손으로 세지 않는다.
     * 챌린저스 월드 캐릭터의 유니온 챔피언이 그렇고, 다시 물어도 같은 대답이 온다.
     * 이것까지 결손으로 세면 그 캐릭터는 캐시를 길게 둘 수 없다.
     */
    private Mono<JsonNode> missingDocument(NexonEndpoint endpoint, Throwable error, Set<NexonEndpoint> missing) {
        if (!isAbsent(error)) {
            missing.add(endpoint);
        }
        return Mono.just(nullNode());
    }

    /** 재시도가 소진돼 감싸인 예외는 여기 걸리지 않는다 — 그건 결손이 맞다. */
    private boolean isAbsent(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode().is4xxClientError()
                && response.getStatusCode().value() != 429;
    }

    private Retry retrySpec() {
        return Retry.backoff(2, Duration.ofMillis(300))
                .filter(this::isRetryable);
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            return true;
        }
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    || responseException.getStatusCode().value() == 429;
        }
        return false;
    }

    private JsonNode nullNode() {
        return tools.jackson.databind.node.NullNode.getInstance();
    }

}
