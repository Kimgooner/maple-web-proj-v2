package org.whitedoggy.mapleweb2.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로세스 안에만 사는 캐시. 기본값이라 로컬과 테스트는 Redis 없이 돈다.
 * 재시작하면 사라지므로 프로덕션은 {@link RedisMapleCache} 를 쓴다.
 *
 * <p>기본값이라는 점이 위험하기도 하다. 프로덕션에서 {@code MAPLE_CACHE_TYPE} 이 빠지면
 * 오류 없이 이쪽이 뜨고, 레벨 구간 표본이 배포마다 사라져 다시 측정된다(넥슨 8000회).
 * 조용히 넘어가지 않도록 뜰 때 경고를 남긴다 - {@code docker logs app} 에서 바로 보인다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "maple.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryMapleCache implements MapleCache {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public InMemoryMapleCache() {
        log.warn("캐시가 메모리다. 재시작하면 레벨 구간 표본까지 사라진다 - "
                + "프로덕션이면 MAPLE_CACHE_TYPE=redis 인지 확인할 것.");
    }

    @Override
    public <T> Mono<T> get(String key, Class<T> type) {
        CacheEntry cached = cache.get(key);
        if (cached == null || cached.isExpired()) {
            cache.remove(key);
            return Mono.empty();
        }
        return Mono.just(type.cast(cached.value()));
    }

    @Override
    public <T> Mono<T> put(String key, T value, Duration ttl) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttl.toMillis()));
        return Mono.just(value);
    }

    @Override
    public <T> Mono<T> put(String key, T value) {
        cache.put(key, new CacheEntry(value, Long.MAX_VALUE));
        return Mono.just(value);
    }

    private record CacheEntry(
            Object value,
            long expiresAtMillis
    ) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
