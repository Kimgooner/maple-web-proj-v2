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
  it('분류별로 풀어 세고, 탭과 같은 순서로 준다', () => {
    expect(changeCounts(summary)).toEqual([
      { key: 'items', label: '장비', count: 2 },
      { key: 'cash', label: '캐시 장비', count: 1 },
      { key: 'skill', label: '스킬', count: 1 },
    ]);
  });

  it('0건인 분류는 빼고 준다. 열두 줄이 0 으로 늘어서면 바뀐 것이 묻힌다', () => {
    expect(changeCounts(summary).map((entry) => entry.key)).not.toContain('pet');
  });

  it('전체 목록도 탭과 같은 차례다. 서버가 준 묶음 차례(장비 → 소스 → 캐시)가 아니다', () => {
    expect(changeRows(summary).map((row) => row.category)).toEqual(['items', 'items', 'cash', 'skill']);
  });

  it('레벨 상승과 기타 능력치는 나머지 한 탭으로 간다', () => {
    const only = { itemChanges: [], cashChanges: [], petChanges: [],
      coreChanges: [{ source: 'abilityPoint' }, { source: 'otherStat' }] } as unknown as ChangeSummary;
    expect(changeRows(only).map((row) => row.category)).toEqual(['etc', 'etc']);
  });

  it('요약이 없으면 빈 목록이다', () => {
    expect(changeRows(null)).toEqual([]);
    expect(changeCounts(null)).toEqual([]);
  });
});
