package org.whitedoggy.mapleweb2.global.cache;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

public interface MapleCache {
    <T> Mono<T> get(String key, Class<T> type);

    <T> Mono<T> put(String key, T value, Duration ttl);

    /**
     * 만료 없이 보관한다. 다시 만들 수 없는 값에만 쓴다.
     *
     * <p>프로덕션 Redis 는 {@code volatile-lru} 라 <b>만료가 걸린 키만</b> 밀어낸다.
     * 캐시(데이터시트·추이·ocid)는 모두 TTL 이 있어 그대로 밀려나고, 여기 넣은 값은
     * 메모리가 차도 남는다. 주 단위 표본처럼 한 번 놓치면 그 주가 영영 비는 값이
     * LRU 에 쓸려 나가지 않게 하는 자리다.
     */
    <T> Mono<T> put(String key, T value);

    default <T> Mono<T> getOrLoad(String key, Class<T> type, Duration ttl, Supplier<Mono<T>> loader) {
        return get(key, type)
                .switchIfEmpty(loader.get()
                        .flatMap(value -> put(key, value, ttl)));
    }
}
