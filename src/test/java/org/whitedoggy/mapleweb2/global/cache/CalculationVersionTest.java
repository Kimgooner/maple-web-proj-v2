package org.whitedoggy.mapleweb2.global.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalculationVersionTest {

    /** 캐시 키에 들어가므로 짧고, 부팅마다 같은 값이어야 한다. */
    @Test
    void 지문은_짧고_같은_빌드에서_같다() {
        String first = new CalculationVersion().tag();
        String second = new CalculationVersion().tag();
        assertThat(first).matches("[0-9a-f]{12}");
        assertThat(second).isEqualTo(first);
    }
}
