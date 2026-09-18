import { describe, expect, it } from 'vitest';
import { applyHistoryEvent, isPending, latestLoadedPoint, loadingHistoryState } from './history';
import type { HistoryMeta } from '../api/types';

const meta: HistoryMeta = {
  ocid: 'o', range: 'monthly',
  characterInfo: { name: '뚠버리', className: '플레임위자드', level: 290, guild: null, world: '챌린저스3', image: null, mainStats: ['INT'], subStats: ['LUK'], usesMagic: true }, preset: null,
  requestedCount: 12, plannedCount: 3, truncated: true, truncatedFrom: '2026-06-01',
  dates: ['2026-09-01', '2026-08-01', '2026-07-01'], concurrent: 1, breakdown: null,
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
      data: { index: 1, total: 3, point: { date: '2026-09-01', level: 290, combatPower: 100, apiCombatPower: 100, solErdaFragments: 500, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null, awaiting: false } , concurrent: 1 },
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

describe('집계 대기 지점', () => {
  it('값이 없어도 받은 자리라 pending 이 아니다 - 다 받은 뒤 "기록 없음"으로 보이면 안 된다', () => {
    const awaiting = { date: '2026-09-15', level: null, combatPower: null, apiCombatPower: null, solErdaFragments: null, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null, awaiting: true };
    expect(isPending(awaiting)).toBe(false);
    expect(isPending({ ...awaiting, awaiting: false })).toBe(true);
  });
});

describe('reset', () => {
  const shown = () => {
    let state = applyHistoryEvent(loadingHistoryState(), { type: 'meta', data: meta });
    state = applyHistoryEvent(state, { type: 'point', data: { index: 1, total: 3, point: { date: '2026-09-01', level: 290, combatPower: 100, apiCombatPower: 100, solErdaFragments: null, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null, awaiting: false }, concurrent: 1 } });
    return applyHistoryEvent(state, { type: 'done', data: {} as never });
  };

  it('같은 캐릭터의 다른 구간이면 보여 주던 것을 들고 있는다', () => {
    const state = applyHistoryEvent(shown(), { type: 'reset' });
    expect(state.status).toBe('loading');
    expect(state.meta).not.toBeNull();
    expect(state.points).toHaveLength(3);
  });

  it('다른 캐릭터로 넘어가면 옛 이름·전투력을 남기지 않는다', () => {
    const state = applyHistoryEvent(shown(), { type: 'reset', hard: true });
    expect(state.status).toBe('loading');
    expect(state.meta).toBeNull();
    expect(state.points).toHaveLength(0);
  });
});

describe('latestLoadedPoint', () => {
  it('계산값이 null 인 최신 지점은 건너뛴다', () => {
    const point = latestLoadedPoint([
      { date: '2026-09-01', level: 1, combatPower: 5, apiCombatPower: 5, solErdaFragments: 10, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null, awaiting: false },
      { date: '2026-09-02', level: 1, combatPower: null, apiCombatPower: 7, solErdaFragments: null, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null, awaiting: false },
    ]);
    expect(point?.date).toBe('2026-09-01');
  });
});

describe('전투력 0 인 지점', () => {
  it('캐릭터가 없던 날짜의 0 은 값으로 세지 않는다 - 그래야 선이 끊기고 축이 음수로 안 벌어진다', () => {
    const state = applyHistoryEvent(loadingHistoryState(), {
      type: 'point',
      data: {
        index: 1,
        total: 1,
        point: {
          date: '2026-01-01', level: 210, combatPower: 0, apiCombatPower: 0,
          solErdaFragments: null, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null, awaiting: false,
        },
        concurrent: 1,
      },
    });

    expect(state.points[0].combatPower).toBeNull();
    expect(state.points[0].apiCombatPower).toBeNull();
    // 레벨은 남는다. 아직 안 받은 자리(pending)와는 다른 것이다.
    expect(state.points[0].level).toBe(210);
  });
});

describe('repaired', () => {
  it('되돌린 지점만 바꾸고 기록 없는 자리는 남긴다 - 회색 구간이 사라지면 안 된다', () => {
    let state = applyHistoryEvent(loadingHistoryState(), { type: 'meta', data: meta });
    // 3자리 중 최신 2개만 받고 done (가장 오래된 자리는 기록 없음)
    for (const [index, date] of [[1, '2026-09-01'], [2, '2026-08-01']] as const) {
      state = applyHistoryEvent(state, { type: 'point', data: { index, total: 3, point: { date, level: 290, combatPower: 100, apiCombatPower: 100, solErdaFragments: null, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: 1, expired: null, awaiting: false }, concurrent: 1 } });
    }
    state = applyHistoryEvent(state, { type: 'done', data: { loadedCount: 2, truncated: false, truncatedFrom: null } });
    expect(state.points).toHaveLength(3);

    state = applyHistoryEvent(state, { type: 'repaired', points: [
      { date: '2026-09-01', level: 290, combatPower: 120, apiCombatPower: 100, solErdaFragments: null, solErdaFragmentsRequired: null, cooldownSecond: null, cooldownSkipPercent: null, itemPreset: 2, expired: null, awaiting: false },
    ] });
    expect(state.points).toHaveLength(3);
    expect(state.points.map((p) => p.combatPower)).toEqual([null, 100, 120]);
  });
});
