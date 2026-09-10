package org.whitedoggy.mapleweb2.analysis.ranking;

/**
 * 레벨 구간 하나의 전투력 분포.
 *
 * <p>표본은 랭킹에서 뽑아 우리 계산기로 다시 계산한 값이다. 넥슨이 주는 전투력은
 * 오래 접속하지 않은 캐릭터에서 옛 값에 멈춰 있어(→ {@code api-stat-document-can-freeze})
 * 분포를 만드는 데 쓰지 않는다.
 *
 * @param from        구간 하한 레벨(포함)
 * @param to          구간 상한 레벨(포함)
 * @param sampleSize  걸러 내고 남아 실제로 분포에 들어간 인원
 * @param top1Percent 상위 1% 지점의 전투력
 * @param median      중앙값. 평균은 상위 구간에서 소수의 큰 값에 끌려가므로 화면의 기준선은 이쪽이다
 */
public record LevelBandStats(
        int from,
        int to,
        int sampleSize,
        long top1Percent,
        long top10Percent,
        long top30Percent,
        long median,
        long mean
) {
}
