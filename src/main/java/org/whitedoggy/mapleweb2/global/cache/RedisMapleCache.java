package org.whitedoggy.mapleweb2.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 값을 JSON 으로 Redis 에 두는 캐시. 배포·재시작 뒤에도 남는다.
 *
 * <p><b>큰 값은 gzip 으로 눌러 둔다.</b> 데이터시트 한 장이 JSON 으로 87~108KB 인데, 필드 이름이
 * 되풀이되는 모양이라 gzip 이 12분의 1(108KB → 9KB)로 줄인다. 버리는 데이터는 없다 — 같은 JSON 을
 * 풀어서 읽는다. 작은 값(추이 지점 300B)은 눌러도 이득이 없어 그대로 둔다. 읽을 때는 gzip 머리
 * (1f 8b)로 가려 두 모양을 다 받으므로, 눌러 두기 전에 쓴 값도 그대로 읽힌다.
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

    /** 이보다 작은 값은 누르지 않는다. gzip 머리(약 20B)와 CPU 만 들고 줄지 않는다. */
    static final int COMPRESS_FROM_BYTES = 512;

    private final ReactiveRedisTemplate<String, byte[]> redis;
    private final ObjectMapper mapper;
    /** 0 은 "아직 한 번도 안 남겼다". 음수 초기값을 쓰면 뺄셈이 넘쳐 첫 경고가 삼켜진다. */
    private final AtomicLong lastWarnedAtMillis = new AtomicLong(0);

    @Autowired
    public RedisMapleCache(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper mapper) {
        this(binaryTemplate(connectionFactory), mapper);
    }

    RedisMapleCache(ReactiveRedisTemplate<String, byte[]> redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** 값은 바이트 그대로. 문자열 템플릿을 쓰면 gzip 바이트가 UTF-8 로 깨진다. */
    private static ReactiveRedisTemplate<String, byte[]> binaryTemplate(ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, byte[]> context = RedisSerializationContext
                .<String, byte[]>newSerializationContext(StringRedisSerializer.UTF_8)
                .value(RedisSerializer.byteArray())
                .hashValue(RedisSerializer.byteArray())
                .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Override
    public <T> Mono<T> get(String key, Class<T> type) {
        return redis.opsForValue().get(key)
                .flatMap(bytes -> deserialize(key, bytes, type))
                .onErrorResume(error -> {
                    warnOnce("캐시를 읽지 못했습니다", error);
                    return Mono.empty();
                });
    }

    @Override
    public <T> Mono<T> put(String key, T value, Duration ttl) {
        return Mono.fromCallable(() -> encode(value))
                .flatMap(bytes -> redis.opsForValue().set(key, bytes, ttl))
                .thenReturn(value)
                .onErrorResume(error -> {
                    warnOnce("캐시에 쓰지 못했습니다", error);
                    return Mono.just(value);
                });
    }

    @Override
    public <T> Mono<T> put(String key, T value) {
        return Mono.fromCallable(() -> encode(value))
                .flatMap(bytes -> redis.opsForValue().set(key, bytes))
                .thenReturn(value)
                .onErrorResume(error -> {
                    warnOnce("캐시에 쓰지 못했습니다", error);
                    return Mono.just(value);
                });
    }

    /** JSON 으로 만들고, 크면 gzip 으로 누른다. */
    static byte[] encode(ObjectMapper mapper, Object value) throws IOException {
        byte[] json = mapper.writeValueAsBytes(value);
        if (json.length < COMPRESS_FROM_BYTES) {
            return json;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length / 8);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json);
        }
        return out.toByteArray();
    }

    /** 눌린 것이면 풀고, 아니면 그대로. gzip 머리 두 바이트(1f 8b)로 가른다. */
    static byte[] decode(byte[] bytes) throws IOException {
        if (bytes.length < 2 || (bytes[0] & 0xff) != 0x1f || (bytes[1] & 0xff) != 0x8b) {
            return bytes;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        }
    }

    private byte[] encode(Object value) throws IOException {
        return encode(mapper, value);
    }

    /**
     * 직렬화 형식이 바뀌어 못 읽는 옛 값은 미스로 두고 지운다. 계산 세대가 값에 적혀 있어
     * 이런 값을 마주칠 일은 드물지만, 남아 있는 것을 계속 붙들고 있을 이유도 없다.
     */
    private <T> Mono<T> deserialize(String key, byte[] bytes, Class<T> type) {
        T value;
        try {
            value = mapper.readValue(decode(bytes), type);
        } catch (RuntimeException | IOException error) {
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
