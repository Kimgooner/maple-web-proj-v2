import { describe, expect, it } from 'vitest';
import { applyHistoryEvent, latestLoadedPoint, loadingHistoryState } from './history';
import type { HistoryMeta } from '../api/types';

const meta: HistoryMeta = {
  ocid: 'o', range: 'monthly',
  characterInfo: { name: '뚠버리', className: '플레임위자드', level: 290, guild: null, world: '챌린저스3', image: null },
  requestedCount: 12, plannedCount: 3, truncated: true, truncatedFrom: '2026-06-01',
  dates: ['2026-09-01', '2026-08-01', '2026-07-01'],
};

describe('applyHistoryEvent', () => {
  it('meta 는 날짜 자리를 과거 → 최신 순으로 만든다', () => {
    const state = applyHistoryEvent(loadingHistoryState(), { type: 'meta', data: meta });
    expect(state.points.map((p) => p.date)).toEqual(['2026-07-01', '2026-08-01', '2026-09-01']);
    expect(state.total).toBe(3);
    expect(state.points.every((p) => p.combatPower === null)).toBe(true);
  });

  it('point 는 자기 자리를 채우고 진행률을 올린다', () => {
    let state = applyHistoryEvent(loadingHistoryState(), { type: 'meta', data: meta });
    state = applyHistoryEvent(state, {
      type: 'point',
      data: { index: 1, total: 3, point: { date: '2026-09-01', level: 290, combatPower: 100, apiCombatPower: 100, solErdaFragments: 500 } },
    });
    expect(state.points[2].combatPower).toBe(100);
    expect(state.points[1].combatPower).toBeNull();
    expect(state.received).toBe(1);
  });

  it('done 은 상태를 끝내고, error 는 메시지를 남긴다', () => {
    let state = applyHistoryEvent(loadingHistoryState(), { type: 'meta', data: meta });
    expect(applyHistoryEvent(state, { type: 'done', data: { loadedCount: 3, truncated: true, truncatedFrom: null } }).status).toBe('done');
    state = applyHistoryEvent(state, { type: 'error', data: { code: 'ERROR', message: '없음' } });
    expect(state.status).toBe('error');
    expect(state.error).toBe('없음');
  });
});

describe('latestLoadedPoint', () => {
  it('계산값이 null 인 최신 지점은 건너뛴다', () => {
    const point = latestLoadedPoint([
      { date: '2026-09-01', level: 1, combatPower: 5, apiCombatPower: 5, solErdaFragments: 10 },
      { date: '2026-09-02', level: 1, combatPower: null, apiCombatPower: 7, solErdaFragments: null },
    ]);
    expect(point?.date).toBe('2026-09-01');
  });
});
