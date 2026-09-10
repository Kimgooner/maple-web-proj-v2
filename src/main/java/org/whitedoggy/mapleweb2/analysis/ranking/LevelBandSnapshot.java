package org.whitedoggy.mapleweb2.analysis.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 주에 잰 구간별 분포.
 *
 * @param weekOf      이 표본이 대표하는 주의 월요일
 * @param rankingDate 표본을 뽑은 랭킹 날짜. 배치가 도는 시각에 열려 있는 가장 최근 날짜다
 * @param bands       레벨 높은 구간부터
 */
public record LevelBandSnapshot(
        LocalDate weekOf,
        LocalDate rankingDate,
        List<LevelBandStats> bands
) {
}
