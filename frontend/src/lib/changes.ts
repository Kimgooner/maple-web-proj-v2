import type { ChangeSummary, EntryChange, SlotChange, SourceChange, StatDelta } from '../api/types';

export type ChangeCategory =
  | 'items' | 'setEffect' | 'cash' | 'pet' | 'skill' | 'hexa'
  | 'symbol' | 'hyperStat' | 'ability' | 'union' | 'etc';

/**
 * 탭에 놓이는 순서와 이름. 전투력에 크게 걸리는 것부터다.
 *
 * <p>세트효과는 장비 바로 뒤다 — 장비를 갈아 끼운 결과로 따라 움직이는 값이라 같이 읽힌다.
 * 레벨 상승과 기타 능력치는 한 탭('나머지')에 둔다. 따로 두면 한 건짜리 탭만 늘어난다.
 *
 * <p>여기 없는 소스가 생기면 'etc' 로 떨어져 맨 뒤에 붙는다.
 */
export const CATEGORIES: [ChangeCategory, string][] = [
  ['items', '장비'], ['setEffect', '세트효과'], ['cash', '캐시 장비'], ['pet', '펫 장비'],
  ['skill', '스킬'], ['hexa', '헥사'], ['symbol', '심볼'], ['hyperStat', '하이퍼스탯'],
  ['ability', '어빌리티'], ['union', '유니온'], ['etc', '나머지'],
];

/**
 * 소스를 탭으로 묶는 규칙.
 *
 * <p>헥사는 스탯과 코어를 한 탭에 둔다. 유니온은 넷(공격대·점령·아티팩트·챔피언)을 한 탭에
 * 둔다 — 따로 두면 한 건짜리 탭만 늘어난다.
 */
const SOURCE_CATEGORY: Record<string, ChangeCategory> = {
  skill: 'skill',
  hexaStat: 'hexa', hexaCore: 'hexa',
  unionRaider: 'union', unionOccupied: 'union', unionArtifact: 'union', unionChampion: 'union',
  ability: 'ability', hyperStat: 'hyperStat', symbol: 'symbol',
  setEffect: 'setEffect', abilityPoint: 'etc', otherStat: 'etc',
};

/** 분류가 놓이는 자리. 탭에서든 '전체' 목록에서든 같은 차례여야 눈이 자리를 다시 안 찾는다. */
const CATEGORY_RANK = new Map(CATEGORIES.map(([key], index) => [key, index]));

/** 표에 한 줄로 놓이는 변화. 장비·캐시·펫은 슬롯, 스킬·심볼 등은 소스다. */
export type ChangeRow =
  | { kind: 'slot'; category: ChangeCategory; label: string; key: string; change: SlotChange }
  | { kind: 'source'; category: ChangeCategory; label: string; key: string; change: SourceChange };

/**
 * 표에 놓을 줄. <b>'전체' 탭도 탭과 같은 차례로 놓는다.</b>
 *
 * <p>전에는 서버가 준 묶음 차례(장비 → 소스 전부 → 캐시 → 펫)를 그대로 썼는데, 탭은
 * 장비 다음이 캐시효과라 두 곳의 차례가 어긋났다. 분류 안에서는 서버가 준 차례를 지킨다 —
 * 장비는 장비창을 훑는 순서, 심볼은 지역 순으로 이미 정렬해 온다.
 */
export function changeRows(summary: ChangeSummary | null): ChangeRow[] {
  if (!summary) return [];
  return rowsOf(summary)
    .map((row, index) => ({ row, index, rank: CATEGORY_RANK.get(row.category) ?? CATEGORIES.length }))
    .sort((left, right) => left.rank - right.rank || left.index - right.index)
    .map((entry) => entry.row);
}

function rowsOf(summary: ChangeSummary): ChangeRow[] {
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
 * 구간 요약에 쓰는 분류별 건수. 탭과 같은 이름, 같은 순서다.
 *
 * <p><b>0건은 빼고 준다.</b> 열두 분류를 모두 늘어놓으면 정작 무엇이 바뀌었는지가 0 사이에
 * 묻힌다. 한 구간에 실제로 걸리는 분류는 보통 한둘이라, 빼고 나면 화면이 짧게 유지된다.
 * ChangesPanel 이 빈 탭을 숨기는 것과 같은 규칙이다.
 */
export function changeCounts(summary: ChangeSummary | null): { key: ChangeCategory; label: string; count: number }[] {
  const rows = changeRows(summary);
  return CATEGORIES
    .map(([key, label]) => ({ key, label, count: rows.filter((row) => row.category === key).length }))
    .filter((entry) => entry.count > 0);
}

/**
 * 구간 전체의 스탯 증감. 모든 줄의 증감을 스탯별로 더한다.
 *
 * <p>각 줄의 값이 그 소스의 시트를 이전·이후로 뺀 것이라, 더하면 캐릭터 전체가 그 구간에
 * 얼마나 움직였는지가 된다. 장비에서 보공 3%가 붙고 유니온에서 5%가 붙었으면 8%다.
 *
 * <p>0 이 된 것은 뺀다 — 한쪽에서 붙고 다른 쪽에서 같은 만큼 빠진 스탯을 "유지"로 적으면
 * 아무 일도 없었던 것처럼 읽힌다. 줄 단위로는 그 사정이 각각 남아 있다.
 */
export function totalDeltas(summary: ChangeSummary | null): StatDelta[] {
  const sum = new Map<string, number>();
  for (const row of changeRows(summary)) {
    for (const delta of row.change.deltas ?? []) {
      sum.set(delta.statName, (sum.get(delta.statName) ?? 0) + delta.delta);
    }
  }
  return [...sum]
    .filter(([, delta]) => delta !== 0)
    .map(([statName, delta]) => ({ statName, delta }));
}

export function rowEntries(row: ChangeRow): EntryChange[] {
  return row.kind === 'source' ? row.change.entries ?? [] : [];
}

export function rowDeltas(row: ChangeRow): StatDelta[] {
  return row.change.deltas ?? [];
}
