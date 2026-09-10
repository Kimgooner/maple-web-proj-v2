package org.whitedoggy.mapleweb2.global.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로세스 안에만 사는 캐시. 기본값이라 로컬과 테스트는 Redis 없이 돈다.
 * 재시작하면 사라지므로 프로덕션은 {@link RedisMapleCache} 를 쓴다.
 */
@Component
@ConditionalOnProperty(name = "maple.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryMapleCache implements MapleCache {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

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
