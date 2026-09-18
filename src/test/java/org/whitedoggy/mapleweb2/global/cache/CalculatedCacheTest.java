package org.whitedoggy.mapleweb2.global.cache;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatedCacheTest {

    record Point(String date, long combatPower) {
    }

    private final MapleCache store = new InMemoryMapleCache();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void 같은_세대의_값은_히트다() {
        CalculatedCache cache = new CalculatedCache(store, mapper, CalculationVersion.fixed("aaa"));
        cache.put("k", new Point("2026-09-18", 100), Duration.ofMinutes(5)).block();

        assertThat(cache.get("k", Point.class).block()).isEqualTo(new Point("2026-09-18", 100));
    }

    /** 배포로 계산 세대가 바뀌면, 같은 키에 남아 있던 옛 값은 히트가 아니고 새 값이 그 자리를 덮는다. */
    @Test
    void 지난_세대의_값은_미스로_보고_다시_계산해_덮어쓴다() {
        CalculatedCache before = new CalculatedCache(store, mapper, CalculationVersion.fixed("aaa"));
        before.put("k", new Point("2026-09-18", 100), Duration.ofMinutes(5)).block();

        CalculatedCache after = new CalculatedCache(store, mapper, CalculationVersion.fixed("bbb"));
        assertThat(after.get("k", Point.class).blockOptional()).isEmpty();

        AtomicInteger computed = new AtomicInteger();
        Point loaded = after.getOrLoad("k", Point.class, Duration.ofMinutes(5),
                () -> Mono.fromSupplier(() -> { computed.incrementAndGet(); return new Point("2026-09-18", 200); }))
                .block();
        assertThat(loaded.combatPower()).isEqualTo(200);
        assertThat(computed.get()).isEqualTo(1);

        // 덮어쓴 뒤로는 새 세대의 히트다. 다시 계산하지 않는다.
        after.getOrLoad("k", Point.class, Duration.ofMinutes(5),
                () -> Mono.fromSupplier(() -> { computed.incrementAndGet(); return new Point("x", 0); })).block();
        assertThat(computed.get()).isEqualTo(1);
        assertThat(after.get("k", Point.class).block().combatPower()).isEqualTo(200);
        // 옛 세대에서 보면 이제 그쪽이 미스다 — 값은 한 자리에 하나뿐이다.
        assertThat(before.get("k", Point.class).blockOptional()).isEmpty();
    }

    /** 같은 키를 동시에 만들면 한 번만 만든다 — 캐시가 비었을 때 같은 캐릭터를 여럿이 열어도 넥슨은 한 번. */
    @Test
    void 같은_키를_동시에_만들면_한_번만_만든다() {
        CalculatedCache cache = new CalculatedCache(store, mapper, CalculationVersion.fixed("aaa"));
        AtomicInteger computed = new AtomicInteger();
        java.util.function.Supplier<Mono<Point>> loader = () -> Mono.delay(Duration.ofMillis(80))
                .map(ignored -> { computed.incrementAndGet(); return new Point("2026-09-18", 300); });

        Mono<Point> first = cache.getOrLoad("k", Point.class, Duration.ofMinutes(5), loader);
        Mono<Point> second = cache.getOrLoad("k", Point.class, Duration.ofMinutes(5), loader);
        Mono<Point> third = cache.getOrLoad("k", Point.class, Duration.ofMinutes(5), loader);
        var results = Mono.zip(first, second, third).block();

        assertThat(computed.get()).isEqualTo(1);
        assertThat(results.getT1()).isEqualTo(results.getT2()).isEqualTo(results.getT3());
        // 다 만든 뒤에는 캐시 히트라 또 만들지 않고, 만들던 표에서도 빠져 있다.
        cache.getOrLoad("k", Point.class, Duration.ofMinutes(5), loader).block();
        assertThat(computed.get()).isEqualTo(1);
    }

    /** 실패는 같이 받고 굳히지 않는다 — 다음 요청은 새로 만든다. */
    @Test
    void 만들다_실패하면_다음_요청은_새로_만든다() {
        CalculatedCache cache = new CalculatedCache(store, mapper, CalculationVersion.fixed("aaa"));
        AtomicInteger attempts = new AtomicInteger();
        java.util.function.Supplier<Mono<Point>> loader = () -> Mono.defer(() ->
                attempts.incrementAndGet() == 1
                        ? Mono.error(new IllegalStateException("넥슨 5xx"))
                        : Mono.just(new Point("2026-09-18", 400)));

        assertThat(cache.getOrLoad("f", Point.class, Duration.ofMinutes(5), loader)
                .onErrorResume(e -> Mono.empty()).blockOptional()).isEmpty();
        assertThat(cache.getOrLoad("f", Point.class, Duration.ofMinutes(5), loader).block().combatPower()).isEqualTo(400);
        assertThat(attempts.get()).isEqualTo(2);
    }
}
