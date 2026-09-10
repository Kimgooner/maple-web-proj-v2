package org.whitedoggy.mapleweb2.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 값을 JSON 문자열로 Redis 에 두는 캐시. 배포·재시작 뒤에도 남는다.
 *
 * <p><b>Redis 장애는 캐시 미스로 취급한다.</b> 읽기·쓰기 어느 쪽이 실패해도 요청 경로로
 * 예외를 올리지 않는다. 캐시가 통째로 죽어도 화면은 느려질 뿐 계속 뜬다.
 * 같은 원인으로 로그가 도배되지 않도록 경고는 1분에 한 번만 남긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "maple.cache.type", havingValue = "redis")
public class RedisMapleCache implements MapleCache {

    private static final Duration WARN_INTERVAL = Duration.ofMinutes(1);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;
    /** 0 은 "아직 한 번도 안 남겼다". 음수 초기값을 쓰면 뺄셈이 넘쳐 첫 경고가 삼켜진다. */
    private final AtomicLong lastWarnedAtMillis = new AtomicLong(0);

    public RedisMapleCache(ReactiveStringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public <T> Mono<T> get(String key, Class<T> type) {
        return redis.opsForValue().get(key)
                .flatMap(json -> deserialize(key, json, type))
                .onErrorResume(error -> {
                    warnOnce("캐시를 읽지 못했습니다", error);
                    return Mono.empty();
                });
    }

    @Override
    public <T> Mono<T> put(String key, T value, Duration ttl) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(value))
                .flatMap(json -> redis.opsForValue().set(key, json, ttl))
                .thenReturn(value)
                .onErrorResume(error -> {
                    warnOnce("캐시에 쓰지 못했습니다", error);
                    return Mono.just(value);
                });
    }

    @Override
    public <T> Mono<T> put(String key, T value) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(value))
                .flatMap(json -> redis.opsForValue().set(key, json))
                .thenReturn(value)
                .onErrorResume(error -> {
                    warnOnce("캐시에 쓰지 못했습니다", error);
                    return Mono.just(value);
                });
    }

    /**
     * 직렬화 형식이 바뀌어 못 읽는 옛 값은 미스로 두고 지운다. 키 접두사 버전을 올리면
     * 이런 값을 마주칠 일이 없지만, 남아 있는 것을 계속 붙들고 있을 이유도 없다.
     */
    private <T> Mono<T> deserialize(String key, String json, Class<T> type) {
        T value;
        try {
            value = mapper.readValue(json, type);
        } catch (RuntimeException error) {
            warnOnce("캐시 값을 읽을 수 없어 지웁니다: " + key, error);
            return redis.delete(key).onErrorComplete().then(Mono.empty());
        }
        return Mono.just(value);
    }

    private void warnOnce(String message, Throwable error) {
        long now = System.currentTimeMillis();
        long lastWarned = lastWarnedAtMillis.get();
        if (now - lastWarned < WARN_INTERVAL.toMillis()) {
            return;
        }
        if (lastWarnedAtMillis.compareAndSet(lastWarned, now)) {
            log.warn("Redis {} — 캐시 미스로 넘어갑니다: {}", message, error.toString());
        }
    }
}
