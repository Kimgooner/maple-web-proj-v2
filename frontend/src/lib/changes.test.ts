import { describe, expect, it } from 'vitest';
import { changeCounts, changeRows } from './changes';
import type { ChangeSummary } from '../api/types';

const summary = {
  itemChanges: [{ slot: '장비 - 모자' }, { slot: '장비 - 무기' }],
  coreChanges: [{ source: 'skill' }],
  cashChanges: [{ slot: '캐시 장비 - 모자' }],
  petChanges: [],
} as unknown as ChangeSummary;

describe('changeRows', () => {
  it('장비·핵심·캐시/펫으로 갈라 센다', () => {
    expect(changeCounts(summary)).toEqual({ items: 2, core: 1, other: 1 });
  });

  it('캐시와 펫은 한 묶음이다', () => {
    const rows = changeRows(summary);
    expect(rows.filter((row) => row.category === 'other').map((row) => row.label)).toEqual(['캐시']);
  });

  it('요약이 없으면 빈 목록이다', () => {
    expect(changeRows(null)).toEqual([]);
    expect(changeCounts(null)).toEqual({ items: 0, core: 0, other: 0 });
  });
});
