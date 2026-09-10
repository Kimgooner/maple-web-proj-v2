package org.whitedoggy.mapleweb2.global.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 만료 없이 넣은 값에 Redis 가 TTL 을 걸지 않는지 본다.
 *
 * <p>프로덕션 Redis 는 {@code maxmemory-policy volatile-lru} 다 — <b>만료가 걸린 키만</b>
 * 밀어낸다. 여기서 실수로 TTL 이 붙으면 레벨 구간 표본이 캐시와 함께 밀려나고, 그러면
 * 다음 부팅 때 8,000명을 다시 재게 된다. 배포마다 표본이 새로 잡히는 사고가 이 한 줄에서 난다.
 */
class RedisMapleCacheNoExpiryTest {

    @Test
    void 만료_없이_넣으면_TTL_인자를_쓰지_않는다() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.set(anyString(), anyString())).thenReturn(Mono.just(true));

        new RedisMapleCache(redis, new ObjectMapper()).put("maple:levelband:v1:weeks", "값").block();

        verify(values).set(eq("maple:levelband:v1:weeks"), anyString());
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void 만료를_준_값은_그대로_TTL_이_걸린다() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        new RedisMapleCache(redis, new ObjectMapper())
                .put("maple:datasheet:v11:x:2026-09-09", "값", Duration.ofHours(6)).block();

        verify(values).set(eq("maple:datasheet:v11:x:2026-09-09"), anyString(), eq(Duration.ofHours(6)));
        verify(values, never()).set(anyString(), anyString());
    }
}
