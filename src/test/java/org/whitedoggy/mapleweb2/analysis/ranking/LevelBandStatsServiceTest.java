package org.whitedoggy.mapleweb2.analysis.ranking;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 배포할 때마다 표본을 다시 재지 않는다는 것을 지킨다.
 *
 * <p>구간 표본 한 번이 8,000명 · API 14만 회다. 배포가 잦은 날 이게 매번 돌면 하루 한도를
 * 갉아먹고, 무엇보다 같은 주의 값이 배포할 때마다 달라진다. 그래서 두 가지를 못 박는다 —
 * <b>이미 쌓인 주가 있으면 부팅해도 수집하지 않는다</b>, 그리고 <b>만료를 걸지 않고 넣는다</b>
 * (프로덕션 Redis 가 {@code volatile-lru} 라, 만료가 없는 키는 메모리가 차도 안 밀려난다).
 */
class LevelBandStatsServiceTest {

    private static final LevelBandStats BAND =
            new LevelBandStats(280, 284, 919, 164_710_000L, 107_070_000L, 70_810_000L, 43_550_000L, 53_090_000L);

    @Test
    void 이미_쌓인_주가_있으면_부팅해도_다시_재지_않는다() {
        RecordingCache cache = new RecordingCache();
        cache.put("maple:levelband:v1:weeks", new LevelBandStatsResponse(
                List.of(new LevelBandSnapshot(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 6), List.of(BAND)))))
                .block();

        LevelBandCollector collector = mock(LevelBandCollector.class);
        LevelBandStatsService service = enabled(new LevelBandStatsService(cache, collector));

        service.collectOnFirstBoot();

        // 재수집이 돌았다면 랭킹 날짜부터 물어봤을 것이다.
        org.mockito.Mockito.verify(collector, org.mockito.Mockito.never()).hasRanking(any());
        org.mockito.Mockito.verify(collector, org.mockito.Mockito.never()).collect(any(), any());
        assertThat(service.getStats().block().weeks()).hasSize(1);
    }

    @Test
    void 한_주도_없을_때만_부팅하며_채운다() {
        RecordingCache cache = new RecordingCache();
        LevelBandCollector collector = mock(LevelBandCollector.class);
        when(collector.hasRanking(any())).thenReturn(Mono.just(true));
        when(collector.collect(any(), any())).thenAnswer(invocation -> Mono.just(new LevelBandSnapshot(
                invocation.getArgument(1), invocation.getArgument(0), List.of(BAND))));

        LevelBandStatsService service = enabled(new LevelBandStatsService(cache, collector));
        service.refresh().block();

        assertThat(service.getStats().block().weeks()).hasSize(1);
    }

    @Test
    void 표본은_만료를_걸지_않고_넣는다() {
        RecordingCache cache = new RecordingCache();
        LevelBandCollector collector = mock(LevelBandCollector.class);
        when(collector.hasRanking(any())).thenReturn(Mono.just(true));
        when(collector.collect(any(), any())).thenAnswer(invocation -> Mono.just(new LevelBandSnapshot(
                invocation.getArgument(1), invocation.getArgument(0), List.of(BAND))));

        enabled(new LevelBandStatsService(cache, collector)).refresh().block();

        assertThat(cache.writesWithTtl.get()).isZero();
        assertThat(cache.writesWithoutTtl.get()).isOne();
    }

    @Test
    void 꺼져_있으면_부팅해도_아무것도_안_한다() {
        RecordingCache cache = new RecordingCache();
        LevelBandCollector collector = mock(LevelBandCollector.class);

        new LevelBandStatsService(cache, collector).collectOnFirstBoot();

        org.mockito.Mockito.verifyNoInteractions(collector);
    }

    private LevelBandStatsService enabled(LevelBandStatsService service) {
        ReflectionTestUtils.setField(service, "enabled", true);
        return service;
    }

    /** 만료를 걸었는지 세는 캐시. */
    private static final class RecordingCache implements MapleCache {
        private final Map<String, Object> values = new HashMap<>();
        private final AtomicInteger writesWithTtl = new AtomicInteger();
        private final AtomicInteger writesWithoutTtl = new AtomicInteger();

        @Override
        @SuppressWarnings("unchecked")
        public <T> Mono<T> get(String key, Class<T> type) {
            Object value = values.get(key);
            return value == null ? Mono.empty() : Mono.just((T) value);
        }

        @Override
        public <T> Mono<T> put(String key, T value, Duration ttl) {
            writesWithTtl.incrementAndGet();
            values.put(key, value);
            return Mono.just(value);
        }

        @Override
        public <T> Mono<T> put(String key, T value) {
            writesWithoutTtl.incrementAndGet();
            values.put(key, value);
            return Mono.just(value);
        }

        @Override
        public <T> Mono<T> getOrLoad(String key, Class<T> type, Duration ttl, Supplier<Mono<T>> loader) {
            return MapleCache.super.getOrLoad(key, type, ttl, loader);
        }
    }
}
