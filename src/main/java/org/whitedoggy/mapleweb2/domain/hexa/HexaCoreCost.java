package org.whitedoggy.mapleweb2.domain.hexa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * HEXA 코어를 올리는 데 드는 솔 에르다 조각. 실제 값은 {@code hexa-core-cost.yml}에 있다.
 *
 * <p>각 배열의 n번째 값이 {@code (n-1)레벨 → n레벨} 한 단계의 비용이다.
 * 넥슨이 표를 고치면 yml 만 고치면 된다.
 */
@ConfigurationProperties(prefix = "maple.hexa.core-cost")
public record HexaCoreCost(
        List<Integer> origin,
        List<Integer> ascent,
        List<Integer> third,
        List<Integer> mastery,
        List<Integer> boost,
        List<Integer> solJanusHecate,
        List<Integer> commonBoost
) {
    /**
     * {@code level}레벨까지 올리는 데 들어간 조각의 합.
     *
     * <p>표보다 높은 레벨이 오면 표까지만 센다 — 상한이 30에서 올라가는 업데이트가 오면
     * 조용히 틀리는 대신 덜 세는 쪽으로 둔다. yml 을 고치면 바로 맞는다.
     */
    static long cumulative(List<Integer> table, int level) {
        if (table == null || level <= 0) {
            return 0;
        }
        long total = 0;
        for (int step = 0; step < Math.min(level, table.size()); step++) {
            total += table.get(step);
        }
        return total;
    }
}
