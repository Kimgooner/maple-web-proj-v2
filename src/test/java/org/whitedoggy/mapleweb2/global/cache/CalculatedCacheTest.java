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
}
