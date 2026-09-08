import type { ExpiredItems, HistoryPoint } from '../api/types';

/**
 * "만료됨" 줄에 적을 문구. 만료된 것이 없으면 null.
 *
 * <p>기간이 지나 스탯이 빠진 것들이다. 게임에는 아직 붙어 보일 수 있지만 계산에서는
 * 빠졌고, 곧 전투력이 떨어질 상태라 눈에 띄게 알린다.
 */
export function expiredLabel(expired: ExpiredItems | null | undefined): string | null {
  if (!expired) return null;
  const parts: string[] = [];
  if (expired.titleOption) parts.push('칭호 옵션');
  if (expired.cashItems > 0) parts.push(`캐시 장비 ${expired.cashItems}개`);
  if (expired.petEquipments > 0) parts.push(`펫 장비 ${expired.petEquipments}개`);
  if (expired.artifactCrystals > 0) parts.push(`유니온 아티팩트 크리스탈 ${expired.artifactCrystals}개`);
  return parts.length ? parts.join(' · ') : null;
}

/** 값이 있는 가장 최근 지점의 만료 정보. 화면 위쪽 요약에 쓴다. */
export function latestExpired(points: HistoryPoint[]): ExpiredItems | null {
  for (let i = points.length - 1; i >= 0; i -= 1) {
    if (points[i].combatPower !== null) return points[i].expired ?? null;
  }
  return null;
}
