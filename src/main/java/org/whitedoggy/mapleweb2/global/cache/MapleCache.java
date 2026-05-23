package org.whitedoggy.mapleweb2.global.cache;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

public interface MapleCache {
    <T> Mono<T> get(String key, Class<T> type);

    <T> Mono<T> put(String key, T value, Duration ttl);

    default <T> Mono<T> getOrLoad(String key, Class<T> type, Duration ttl, Supplier<Mono<T>> loader) {
        return get(key, type)
                .switchIfEmpty(loader.get()
                        .flatMap(value -> put(key, value, ttl)));
    }
}
