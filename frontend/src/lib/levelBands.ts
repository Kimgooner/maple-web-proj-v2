import type { LevelBandStats, LevelBandWeek } from '../api/levelBands';

export interface BandAtDate {
  stats: LevelBandStats;
  /**
   * 그 시점보다 이른 주가 없어 한참 뒤의 주로 대신했는가.
   *
   * <p>전투력은 시간이 갈수록 오르므로, 1년 전 지점 옆에 최근 주의 중앙값을 그으면 그때의
   * 자신이 실제보다 못해 보인다. 그림은 그리되(안 그리면 서비스 시작 첫 해 내내 12개월
   * 그래프에 기준선이 하나도 안 뜬다) 말풍선에서 추정치임을 밝힌다.
   *
   * <p>{@link GRACE_DAYS} 안쪽은 알리지 않는다. 표본을 월요일에 재니 그 주의 지점은 늘
   * 표본보다 이르고, 한두 주 차이는 분포가 움직이지 않는다. 이걸 다 알리면 30일 그래프의
   * 거의 모든 지점에 안내가 붙어 정작 의미 있는 경고가 묻힌다.
   */
  estimated: boolean;
}

/**
 * 그 시점·그 레벨의 구간 통계.
 *
 * <p>주는 지점 날짜 이전의 가장 가까운 주를 고른다. 1년 전 지점 옆에 지금 주의
 * 중앙값을 그으면 그때는 없던 기준으로 재는 셈이 된다.
 */
/** 이만큼 이내로 앞선 지점은 최근 주를 그대로 써도 된다고 본다. */
const GRACE_DAYS = 30;

export function bandAt(
  weeks: LevelBandWeek[],
  date: string,
  level: number | null,
): BandAtDate | null {
  if (!weeks.length || level == null) return null;
  const earlier = [...weeks].reverse().find((candidate) => candidate.weekOf <= date);
  const week = earlier ?? weeks[0];
  const stats = week.bands.find((band) => level >= band.from && level <= band.to);
  if (!stats) return null;
  const gapDays = earlier ? 0 : (Date.parse(week.weekOf) - Date.parse(date)) / 86_400_000;
  return { stats, estimated: gapDays > GRACE_DAYS };
}
