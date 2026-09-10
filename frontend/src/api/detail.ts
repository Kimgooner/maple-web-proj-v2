import type { DetailResponse } from './types';

export async function fetchDetail(
  ocid: string,
  previousDate: string,
  currentDate: string,
  signal?: AbortSignal,
  /** 차트에서 프리셋을 되돌렸으면 그 번호. 변경 내역도 같은 번호로 맞춰 비교한다 */
  itemPreset?: number | null,
): Promise<DetailResponse> {
  const params = new URLSearchParams({ ocid, previousDate, currentDate });
  if (itemPreset != null) params.set('itemPreset', String(itemPreset));
  const response = await fetch(`/api/analysis/combat-power/detail?${params}`, { signal });
  if (!response.ok) {
    let message = `변경 내역을 불러오지 못했습니다 (${response.status})`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // 본문이 JSON 이 아니면 상태 코드만 알린다
    }
    throw new Error(message);
  }
  return (await response.json()) as DetailResponse;
}
