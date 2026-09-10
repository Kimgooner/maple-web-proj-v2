/** 레벨 구간별 전투력 분포. 백엔드가 매주 월요일에 랭킹 표본으로 다시 잰다. */

export interface LevelBandStats {
  from: number;
  to: number;
  /** 걸러 내고 남아 실제로 분포에 들어간 인원 */
  sampleSize: number;
  top1Percent: number;
  top10Percent: number;
  top30Percent: number;
  median: number;
  mean: number;
}

export interface LevelBandWeek {
  /** 이 표본이 대표하는 주의 월요일 */
  weekOf: string;
  rankingDate: string;
  bands: LevelBandStats[];
}

/**
 * 기준선이 없다고 화면이 깨지면 안 된다. 아직 한 주도 안 쌓였거나 요청이 실패하면
 * 빈 배열을 돌려주고, 차트는 회색 선 없이 그대로 그린다.
 */
export async function fetchLevelBands(signal?: AbortSignal): Promise<LevelBandWeek[]> {
  try {
    const response = await fetch('/api/analysis/level-band-stats', { signal });
    if (!response.ok) return [];
    const body = (await response.json()) as { weeks?: LevelBandWeek[] };
    return body.weeks ?? [];
  } catch {
    return [];
  }
}
