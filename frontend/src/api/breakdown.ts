import type { CombatPowerBreakdown } from './types';

/** 오늘 전투력의 계산 과정. 시트를 펼칠 때만 부른다 — 서버가 그때 소스 조합을 돌려 비율을 잰다. */
export async function fetchBreakdown(characterName: string, signal?: AbortSignal): Promise<CombatPowerBreakdown> {
  const params = new URLSearchParams({ characterName });
  const response = await fetch(`/api/analysis/combat-power/breakdown?${params}`, { signal });
  if (!response.ok) {
    let message = `계산 과정을 불러오지 못했습니다 (${response.status})`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // 본문이 JSON 이 아니면 상태 코드만 알린다
    }
    throw new Error(message);
  }
  return (await response.json()) as CombatPowerBreakdown;
}
