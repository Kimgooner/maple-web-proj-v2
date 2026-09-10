package org.whitedoggy.mapleweb2.global.visit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 날짜별 카운터 셋을 Redis 에 쌓는다.
 *
 * <ul>
 *   <li>{@code :lookups}    조회 횟수 (INCR)</li>
 *   <li>{@code :visitors}   고유 방문자 수 (HyperLogLog)</li>
 *   <li>{@code :characters} 조회된 고유 캐릭터 수 (HyperLogLog)</li>
 * </ul>
 *
 * <p>고유 수는 HyperLogLog 로 센다. 값을 통째로 담지 않고 스케치만 남기므로 하루치가
 * 수 KB 를 넘지 않고, <b>넣은 값을 되꺼낼 수 없다</b> — 방문자 목록이 남지 않는다는 뜻이다.
 * 대신 수치는 근사(오차 약 0.8%)다.
 *
 * <p>만료를 걸지 않는다. 프로덕션 Redis 가 {@code volatile-lru} 라 TTL 이 있는 키만
 * 밀어내는데, 지나간 날의 방문 수는 다시 만들 수 없다. 하루에 키 셋이라 1년을 쌓아도
 * 수 MB 다.
 *
 * <p>실패는 삼킨다. 통계를 못 세는 것이 조회를 막을 이유가 되면 안 된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "maple.cache.type", havingValue = "redis")
@RequiredArgsConstructor
public class RedisVisitCounter implements VisitCounter {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String PREFIX = "maple:visit:v1:";

    private final ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Void> record(String characterName, String clientKey) {
        String day = PREFIX + LocalDate.now(KST);
        Mono<?> lookups = redis.opsForValue().increment(day + ":lookups");
        Mono<?> characters = characterName == null || characterName.isBlank()
                ? Mono.empty()
                : redis.opsForHyperLogLog().add(day + ":characters", characterName.trim());
        Mono<?> visitors = clientKey == null
                ? Mono.empty()
                : redis.opsForHyperLogLog().add(day + ":visitors", clientKey);

        return Mono.when(lookups, characters, visitors)
                .doOnError(error -> log.debug("방문 집계를 건너뜁니다: {}", error.toString()))
                .onErrorComplete();
    }
}
