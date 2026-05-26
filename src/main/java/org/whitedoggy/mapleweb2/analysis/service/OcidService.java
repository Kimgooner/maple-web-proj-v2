package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.dto.OcidResponse;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OcidService {
    private final NexonApiClient nexonApiClient;
    private final MapleCache cache;

    private static final Duration OCID_CACHE_TTL = Duration.ofHours(12);

    public Mono<String> getOcid(String characterName) {
        String cacheKey = ocidCacheKey(characterName);
        return cache.getOrLoad(cacheKey, String.class, OCID_CACHE_TTL,
                () -> nexonApiClient.getOcid(characterName)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                        .map(OcidResponse::ocid));
    }

    private String ocidCacheKey(String characterName) {
        return "maple:ocid:" + normalizeCharacterName(characterName);
    }

    private String normalizeCharacterName(String characterName) {
        return characterName == null ? "" : characterName.trim().toLowerCase(Locale.ROOT);
    }
}
