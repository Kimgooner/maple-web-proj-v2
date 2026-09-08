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

    /**
     * 문서 하나만 따로 받는다. 스냅샷 15종에 넣으면 그 문서가 필요 없는 조회
     * (monthly·yearly·검증)까지 호출이 늘어나므로, 쓰는 쪽에서만 부르게 열어 둔다.
     *
     * <p>실패하면 {@code NullNode} 다 — 부르는 쪽이 그 값 없이도 답을 만들 수 있어야 한다.
     */
    public Mono<JsonNode> getDocument(NexonEndpoint endpoint, String ocid, LocalDate date, boolean includeDateParam) {
        return nexonApiClient.get(endpoint, ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorReturn(nullNode());
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        // 구독마다 새로 만든다. 어느 문서를 못 받았는지는 조립이 끝나야 알 수 있다.
        return Mono.defer(() -> fetchSnapshot(ocid, date, includeDateParam, ConcurrentHashMap.newKeySet()));
    }

    private Mono<CharacterSnapshot> fetchSnapshot(
            String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return Mono.zip(
                        fetchBasic(ocid, date, includeDateParam, missing),
                        fetchStat(ocid, date, includeDateParam, missing),
                        fetchItemEquipment(ocid, date, includeDateParam, missing),
                        fetchCashItemEquipment(ocid, date, includeDateParam, missing),
                        fetchSetEffect(ocid, date, includeDateParam, missing),
                        fetchSymbolEquipment(ocid, date, includeDateParam, missing),
                        fetchPetEquipment(ocid, date, includeDateParam, missing),
                        fetchEndpoint(NexonEndpoint.OTHER_STAT, ocid, date, includeDateParam, missing)
                )
                .zipWith(Mono.zip(
                        fetchHyperStat(ocid, date, includeDateParam, missing),
                        fetchAbility(ocid, date, includeDateParam, missing),
                        fetchSkill0(ocid, date, includeDateParam, missing),
                        fetchHexaMatrixStat(ocid, date, includeDateParam, missing),
                        fetchUnionRaider(ocid, date, includeDateParam, missing),
                        fetchUnionChampion(ocid, date, includeDateParam, missing),
                        fetchArtifact(ocid, date, includeDateParam, missing)
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
                    documents.put(NexonEndpoint.OTHER_STAT, tuple.getT1().getT8());
                    documents.put(NexonEndpoint.HYPER_STAT, tuple.getT2().getT1());
                    documents.put(NexonEndpoint.ABILITY, tuple.getT2().getT2());
                    documents.put(NexonEndpoint.SKILL_0, tuple.getT2().getT3());
                    documents.put(NexonEndpoint.HEXA_MATRIX_STAT, tuple.getT2().getT4());
                    documents.put(NexonEndpoint.UNION_RAIDER, tuple.getT2().getT5());
                    documents.put(NexonEndpoint.UNION_CHAMPION, tuple.getT2().getT6());
                    documents.put(NexonEndpoint.UNION_ARTIFACT, tuple.getT2().getT7());
                    return new CharacterSnapshot(ocid, date, documents, Set.copyOf(missing));
                });
    }

    private Mono<JsonNode> fetchBasic(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.BASIC, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchStat(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.STAT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchItemEquipment(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.ITEM_EQUIPMENT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchCashItemEquipment(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.CASH_ITEM_EQUIPMENT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchSetEffect(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.SET_EFFECT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchSymbolEquipment(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.SYMBOL_EQUIPMENT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchPetEquipment(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.PET_EQUIPMENT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchHyperStat(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.HYPER_STAT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchAbility(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.ABILITY, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchSkill0(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return nexonApiClient.getSkill0(ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorResume(error -> missingDocument(NexonEndpoint.SKILL_0, error, missing));
    }

    private Mono<JsonNode> fetchHexaMatrixStat(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.HEXA_MATRIX_STAT, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchUnionRaider(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.UNION_RAIDER, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchUnionChampion(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.UNION_CHAMPION, ocid, date, includeDateParam, missing);
    }

    private Mono<JsonNode> fetchArtifact(String ocid, LocalDate date, boolean includeDateParam, Set<NexonEndpoint> missing) {
        return fetchEndpoint(NexonEndpoint.UNION_ARTIFACT, ocid, date, includeDateParam, missing);
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
