package org.whitedoggy.mapleweb2.analysis.ranking;

import java.util.List;

/**
 * 주간 분포를 모아 둔 응답.
 *
 * <p>화면은 추이의 지점마다 그 시점 이전의 가장 가까운 주를 골라 쓴다. 그래서 한 주만
 * 주지 않고 가지고 있는 주를 다 준다 — 구간이 8개, 주가 60주라도 몇 KB다.
 *
 * @param weeks 오래된 주 → 최신 주 순
 */
public record LevelBandStatsResponse(List<LevelBandSnapshot> weeks) {
}
