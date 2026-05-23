package org.whitedoggy.mapleweb2.global.cache;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
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

    private record CacheEntry(
            Object value,
            long expiresAtMillis
    ) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
