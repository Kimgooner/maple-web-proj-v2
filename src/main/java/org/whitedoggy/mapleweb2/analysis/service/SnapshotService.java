package org.whitedoggy.mapleweb2.analysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SnapshotService {
    private static final Duration OCID_CACHE_TTL = Duration.ofHours(12);
    private static final Duration SNAPSHOT_CACHE_TTL = Duration.ofHours(6);

    private static final List<NexonEndpoint> REQUIRED_ENDPOINTS = List.of(
            NexonEndpoint.BASIC,
            NexonEndpoint.STAT,
            NexonEndpoint.ITEM_EQUIPMENT,
            NexonEndpoint.ABILITY,
            NexonEndpoint.HYPER_STAT,
            NexonEndpoint.UNION_RAIDER
    );

    private final NexonApiClient nexonApiClient;
    private final MapleCache cache;
    private final int maxConcurrency;

    public SnapshotService(
            NexonApiClient nexonApiClient,
            MapleCache cache,
            @Value("${nexon.api.max-concurrency:8}") int maxConcurrency
    ) {
        this.nexonApiClient = nexonApiClient;
        this.cache = cache;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public Mono<String> getOcid(String characterName) {
        String cacheKey = ocidCacheKey(characterName);
        return cache.getOrLoad(cacheKey, String.class, OCID_CACHE_TTL,
                () -> nexonApiClient.getOcid(characterName)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                        .map(response -> response.ocid()));
    }

    public Mono<CharacterSnapshot> getSnapshot(String characterName, LocalDate date) {
        return getOcid(characterName)
                .flatMap(ocid -> getSnapshotByOcid(ocid, date));
    }

    public Mono<CharacterSnapshot> getCurrentSnapshot(String characterName, LocalDate date) {
        return getOcid(characterName)
                .flatMap(ocid -> getCurrentSnapshotByOcid(ocid, date));
    }

    public Flux<CharacterSnapshot> getSnapshots(String characterName, LocalDate today, List<LocalDate> historicalDates) {
        return getOcid(characterName)
                .flatMapMany(ocid -> Flux.concat(
                        getCurrentSnapshotByOcid(ocid, today),
                        Flux.fromIterable(historicalDates)
                                .flatMapSequential(date -> getSnapshotByOcid(ocid, date), maxConcurrency)
                ));
    }

    public boolean hasRequiredDocuments(CharacterSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        for (NexonEndpoint endpoint : REQUIRED_ENDPOINTS) {
            JsonNode document = snapshot.document(endpoint);
            if (document == null || document.isNull()) {
                return false;
            }
        }
        return true;
    }

    private Mono<CharacterSnapshot> getSnapshotByOcid(String ocid, LocalDate date) {
        return getCachedSnapshot(ocid, date);
    }

    private Mono<CharacterSnapshot> getCurrentSnapshotByOcid(String ocid, LocalDate date) {
        return fetchSnapshot(ocid, date, false);
    }

    private Mono<CharacterSnapshot> getCachedSnapshot(String ocid, LocalDate date) {
        String cacheKey = snapshotCacheKey(ocid, date);
        return cache.getOrLoad(cacheKey, CharacterSnapshot.class, SNAPSHOT_CACHE_TTL,
                () -> fetchSnapshot(ocid, date, true));
    }

    private Mono<CharacterSnapshot> fetchSnapshot(String ocid, LocalDate date, boolean includeDateParam) {
        return Mono.zip(
                        fetchBasic(ocid, date, includeDateParam),
                        fetchStat(ocid, date, includeDateParam),
                        fetchItemEquipment(ocid, date, includeDateParam),
                        fetchCashItemEquipment(ocid, date, includeDateParam),
                        fetchSetEffect(ocid, date, includeDateParam),
                        fetchSymbolEquipment(ocid, date, includeDateParam),
                        fetchPetEquipment(ocid, date, includeDateParam)
                )
                .zipWith(Mono.zip(
                        fetchHyperStat(ocid, date, includeDateParam),
                        fetchAbility(ocid, date, includeDateParam),
                        fetchSkill0(ocid, date, includeDateParam),
                        fetchHexaMatrixStat(ocid, date, includeDateParam),
                        fetchUnionRaider(ocid, date, includeDateParam),
                        fetchUnionChampion(ocid, date, includeDateParam),
                        fetchArtifact(ocid, date, includeDateParam)
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

    private Mono<JsonNode> fetchBasic(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.BASIC, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchStat(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.STAT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchItemEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.ITEM_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchCashItemEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.CASH_ITEM_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchSetEffect(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.SET_EFFECT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchSymbolEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.SYMBOL_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchPetEquipment(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.PET_EQUIPMENT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchHyperStat(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.HYPER_STAT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchAbility(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.ABILITY, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchSkill0(String ocid, LocalDate date, boolean includeDateParam) {
        return nexonApiClient.getSkill0(ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorReturn(nullNode());
    }

    private Mono<JsonNode> fetchHexaMatrixStat(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.HEXA_MATRIX_STAT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchUnionRaider(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.UNION_RAIDER, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchUnionChampion(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.UNION_CHAMPION, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchArtifact(String ocid, LocalDate date, boolean includeDateParam) {
        return fetchEndpoint(NexonEndpoint.UNION_ARTIFACT, ocid, date, includeDateParam);
    }

    private Mono<JsonNode> fetchEndpoint(NexonEndpoint endpoint, String ocid, LocalDate date, boolean includeDateParam) {
        return nexonApiClient.get(endpoint, ocid, date, includeDateParam)
                .retryWhen(retrySpec())
                .onErrorReturn(nullNode());
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

    private String ocidCacheKey(String characterName) {
        return "maple:ocid:" + normalizeCharacterName(characterName);
    }

    private String snapshotCacheKey(String ocid, LocalDate date) {
        return "maple:snapshot:" + ocid + ":" + date;
    }

    private String normalizeCharacterName(String characterName) {
        return characterName == null ? "" : characterName.trim().toLowerCase(Locale.ROOT);
    }

}
