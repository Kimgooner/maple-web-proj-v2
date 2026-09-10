import type { HistoryPoint } from './types';

/**
 * 장비 프리셋이 갈린 날짜를 되돌려 다시 계산한 추이.
 *
 * <p>화면의 "전투력이 이상해요!" 가 이 길로 온다. 서버가 이 구간에서 제일 많이 쓴 프리셋
 * 번호를 정답으로 보고, 그와 다른 번호가 뽑힌 날만 다시 계산해서 돌려준다.
 */
export async function fetchRepairedHistory(
  characterName: string,
  range: string,
  signal?: AbortSignal,
): Promise<HistoryPoint[]> {
  const params = new URLSearchParams({ characterName, range });
  const response = await fetch(`/api/analysis/combat-power/history/repair?${params}`, { signal });
  if (!response.ok) throw new Error(`다시 계산하지 못했습니다 (${response.status})`);
  const body = (await response.json()) as { points?: HistoryPoint[] };
  return body.points ?? [];
}
