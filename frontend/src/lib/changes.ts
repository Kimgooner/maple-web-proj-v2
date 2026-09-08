import type { ChangeSummary, EntryChange, SlotChange, SourceChange, StatDelta } from '../api/types';

export type ChangeCategory =
  | 'items' | 'cash' | 'pet' | 'skill' | 'hexa' | 'union'
  | 'ability' | 'hyperStat' | 'symbol' | 'setEffect' | 'abilityPoint';

/** 탭에 놓이는 순서와 이름. 여기 없는 소스가 생기면 'etc' 로 떨어져 맨 뒤에 붙는다. */
export const CATEGORIES: [ChangeCategory, string][] = [
  ['items', '장비'], ['cash', '캐시'], ['pet', '펫'], ['skill', '스킬'],
  ['hexa', '헥사'], ['union', '유니온'],
  ['ability', '어빌리티'], ['hyperStat', '하이퍼스탯'], ['symbol', '심볼'],
  ['setEffect', '세트효과'], ['abilityPoint', 'AP 배분'],
];

/**
 * 핵심 소스를 탭으로 묶는 규칙.
 *
 * <p>헥사는 헥사 스탯만 온다 — 헥사 스킬(코어) 강화는 아직 변경 내역으로 잡지 않는다.
 * 유니온은 넷을 한 탭에 둔다. 따로 두면 한 건짜리 탭만 늘어난다.
 */
const SOURCE_CATEGORY: Record<string, ChangeCategory> = {
  skill: 'skill',
  hexaStat: 'hexa',
  unionRaider: 'union', unionOccupied: 'union', unionArtifact: 'union', unionChampion: 'union',
  ability: 'ability', hyperStat: 'hyperStat', symbol: 'symbol',
  setEffect: 'setEffect', abilityPoint: 'abilityPoint',
};

/** 표에 한 줄로 놓이는 변화. 장비·캐시·펫은 슬롯, 스킬·심볼 등은 소스다. */
export type ChangeRow =
  | { kind: 'slot'; category: ChangeCategory; label: string; key: string; change: SlotChange }
  | { kind: 'source'; category: ChangeCategory; label: string; key: string; change: SourceChange };

export function changeRows(summary: ChangeSummary | null): ChangeRow[] {
  if (!summary) return [];
  return [
    ...summary.itemChanges.map((change): ChangeRow => (
      { kind: 'slot', category: 'items', label: '장비', key: `item:${change.slot}`, change })),
    ...summary.coreChanges.map((change): ChangeRow => ({
      kind: 'source',
      category: SOURCE_CATEGORY[change.source] ?? 'skill',
      label: '핵심',
      key: `core:${change.source}`,
      change,
    })),
    ...summary.cashChanges.map((change): ChangeRow => (
      { kind: 'slot', category: 'cash', label: '캐시', key: `cash:${change.slot}`, change })),
    ...summary.petChanges.map((change): ChangeRow => (
      { kind: 'slot', category: 'pet', label: '펫', key: `pet:${change.slot}`, change })),
  ];
}

/**
 * 구간 요약에 쓰는 굵은 셋. 탭은 잘게 갈렸지만 차트 옆 요약까지 열한 줄로 늘리면 읽히지 않는다.
 * core 는 장비·캐시·펫이 아닌 나머지 전부다.
 */
export function changeCounts(summary: ChangeSummary | null): { items: number; core: number; other: number } {
  const rows = changeRows(summary);
  const count = (match: (row: ChangeRow) => boolean) => rows.filter(match).length;
  return {
    items: count((row) => row.category === 'items'),
    other: count((row) => row.category === 'cash' || row.category === 'pet'),
    core: count((row) => !['items', 'cash', 'pet'].includes(row.category)),
  };
}

export function rowEntries(row: ChangeRow): EntryChange[] {
  return row.kind === 'source' ? row.change.entries ?? [] : [];
}

export function rowDeltas(row: ChangeRow): StatDelta[] {
  return row.change.deltas ?? [];
}
