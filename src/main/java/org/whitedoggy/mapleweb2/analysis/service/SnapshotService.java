package org.whitedoggy.mapleweb2.analysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final int maxConcurrency;
    private final Map<String, CacheEntry<String>> ocidCache = new ConcurrentHashMap<>();
    private final Map<SnapshotCacheKey, CacheEntry<CharacterSnapshot>> snapshotCache = new ConcurrentHashMap<>();

    public SnapshotService(
            NexonApiClient nexonApiClient,
            @Value("${nexon.api.max-concurrency:8}") int maxConcurrency
    ) {
        this.nexonApiClient = nexonApiClient;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public Mono<String> getOcid(String characterName) {
        String cacheKey = normalizeCharacterName(characterName);
        CacheEntry<String> cached = ocidCache.get(cacheKey);
        if (cached != null && !cached.isExpired(OCID_CACHE_TTL)) {
            return Mono.just(cached.value());
        }

        return nexonApiClient.getOcid(characterName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                .map(response -> {
                    ocidCache.put(cacheKey, new CacheEntry<>(response.ocid()));
                    return response.ocid();
                });
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
        return getCachedSnapshot(new SnapshotCacheKey(ocid, date, false),
                () -> nexonApiClient.fetchSnapshot(ocid, date));
    }

    private Mono<CharacterSnapshot> getCurrentSnapshotByOcid(String ocid, LocalDate date) {
        return getCachedSnapshot(new SnapshotCacheKey(ocid, date, true),
                () -> nexonApiClient.fetchCurrentSnapshot(ocid, date));
    }

    private Mono<CharacterSnapshot> getCachedSnapshot(SnapshotCacheKey cacheKey, SnapshotFetcher fetcher) {
        CacheEntry<CharacterSnapshot> cached = snapshotCache.get(cacheKey);
        if (cached != null && !cached.isExpired(SNAPSHOT_CACHE_TTL)) {
            return Mono.just(cached.value());
        }

        return fetcher.fetch()
                .map(snapshot -> {
                    snapshotCache.put(cacheKey, new CacheEntry<>(snapshot));
                    return snapshot;
                });
    }

    private String normalizeCharacterName(String characterName) {
        return characterName == null ? "" : characterName.trim().toLowerCase(Locale.ROOT);
    }

    private record SnapshotCacheKey(
            String ocid,
            LocalDate date,
            boolean current
    ) {
    }

    private record CacheEntry<T>(
            T value,
            long cachedAtMillis
    ) {
        private CacheEntry(T value) {
            this(value, System.currentTimeMillis());
        }

        private boolean isExpired(Duration ttl) {
            return System.currentTimeMillis() - cachedAtMillis > ttl.toMillis();
        }
    }

    @FunctionalInterface
    private interface SnapshotFetcher {
        Mono<CharacterSnapshot> fetch();
    }
}
