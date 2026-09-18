package org.whitedoggy.mapleweb2.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 계산 결과(추이 지점·데이터시트·오늘 앞머리)를 두는 캐시. 값마다 <b>어느 계산 세대로 만든
 * 것인지</b>를 함께 적어 두고, 꺼낼 때 지금 세대({@link CalculationVersion})와 다르면 히트로
 * 치지 않는다 — 호출하는 쪽은 미스와 똑같이 다시 계산해 같은 키에 덮어쓴다.
 *
 * <p>키에 버전을 넣어 옛 값을 못 보게 하는 방법과 결과는 같지만, 옛 값이 다른 키 밑에 30일씩
 * 남아 Redis 를 차지하는 대신 <b>제자리에서 새 값으로 바뀐다</b>. 키가 세대와 무관하게
 * 고정이라 Redis 에서 캐릭터·날짜로 바로 찾아볼 수도 있다.
 *
 * <p>값은 {@code {"version": "...", "value": {...}}} 로 실린다. 세대가 지난 값은 지우지 않는다 —
 * 곧 덮어쓰이고, 다시 계산이 서지 않는 값(그 날짜에 없던 캐릭터)은 어차피 안 쓰인다.
 */
@Slf4j
@Component
public class CalculatedCache {

    /** 캐시에 실리는 모양. {@code value} 는 형을 모른 채 트리로 두고 꺼낼 때 맞춰 읽는다. */
    public record Envelope(String version, JsonNode value) {
    }

    private final MapleCache cache;
    private final ObjectMapper mapper;
    private final CalculationVersion version;

    public CalculatedCache(MapleCache cache, ObjectMapper mapper, CalculationVersion version) {
        this.cache = cache;
        this.mapper = mapper;
        this.version = version;
    }

    /** 지금 세대로 만든 값만 돌려준다. 옛 세대 값은 미스다. */
    public <T> Mono<T> get(String key, Class<T> type) {
        return cache.get(key, Envelope.class)
                .filter(envelope -> isCurrent(key, envelope))
                .map(envelope -> mapper.treeToValue(envelope.value(), type));
    }

    public <T> Mono<T> put(String key, T value, Duration ttl) {
        return cache.put(key, new Envelope(version.tag(), mapper.valueToTree(value)), ttl)
                .thenReturn(value);
    }

    public <T> Mono<T> getOrLoad(String key, Class<T> type, Duration ttl, Supplier<Mono<T>> loader) {
        return get(key, type)
                .switchIfEmpty(Mono.defer(() -> loader.get().flatMap(value -> put(key, value, ttl))));
    }

    private boolean isCurrent(String key, Envelope envelope) {
        if (version.tag().equals(envelope.version())) {
            return true;
        }
        log.debug("계산 세대가 지난 캐시라 다시 계산합니다: {} ({} → {})", key, envelope.version(), version.tag());
        return false;
    }
}
