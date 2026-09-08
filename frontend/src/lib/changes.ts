import type { ChangeSummary, EntryChange, SlotChange, SourceChange, StatDelta } from '../api/types';

export type ChangeCategory = 'items' | 'core' | 'other';

/** 표에 한 줄로 놓이는 변화. 장비·캐시·펫은 슬롯, 스킬·심볼 등은 소스다. */
export type ChangeRow =
  | { kind: 'slot'; category: ChangeCategory; label: string; key: string; change: SlotChange }
  | { kind: 'source'; category: 'core'; label: string; key: string; change: SourceChange };

export function changeRows(summary: ChangeSummary | null): ChangeRow[] {
  if (!summary) return [];
  return [
    ...summary.itemChanges.map((change): ChangeRow => (
      { kind: 'slot', category: 'items', label: '장비', key: `item:${change.slot}`, change })),
    ...summary.coreChanges.map((change): ChangeRow => (
      { kind: 'source', category: 'core', label: '핵심', key: `core:${change.source}`, change })),
    ...summary.cashChanges.map((change): ChangeRow => (
      { kind: 'slot', category: 'other', label: '캐시', key: `cash:${change.slot}`, change })),
    ...summary.petChanges.map((change): ChangeRow => (
      { kind: 'slot', category: 'other', label: '펫', key: `pet:${change.slot}`, change })),
  ];
}

export function changeCounts(summary: ChangeSummary | null): Record<ChangeCategory, number> {
  const rows = changeRows(summary);
  return {
    items: rows.filter((row) => row.category === 'items').length,
    core: rows.filter((row) => row.category === 'core').length,
    other: rows.filter((row) => row.category === 'other').length,
  };
}

export function rowEntries(row: ChangeRow): EntryChange[] {
  return row.kind === 'source' ? row.change.entries ?? [] : [];
}

export function rowDeltas(row: ChangeRow): StatDelta[] {
  return row.change.deltas ?? [];
}
