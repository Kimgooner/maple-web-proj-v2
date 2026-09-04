import { describe, expect, it } from 'vitest';
import { changedDates, defaultInterval, intervalEndingAt, pickPoint } from './selection';
import type { HistoryPoint } from '../api/types';

const p = (date: string, combatPower: number | null): HistoryPoint => ({ date, level: 1, combatPower, apiCombatPower: null });

describe('defaultInterval', () => {
  it('마지막으로 변한 인접 두 지점을 고른다', () => {
    const points = [p('01', 10), p('02', 10), p('03', 12), p('04', 12)];
    expect(defaultInterval(points)).toEqual({ previousDate: '02', currentDate: '03' });
  });

  it('변한 곳이 없으면 최신 두 지점', () => {
    expect(defaultInterval([p('01', 10), p('02', 10), p('03', 10)])).toEqual({ previousDate: '02', currentDate: '03' });
  });

  it('null 지점은 비교에서 뺀다', () => {
    const points = [p('01', 10), p('02', null), p('03', 12)];
    expect(defaultInterval(points)).toEqual({ previousDate: '01', currentDate: '03' });
  });

  it('값 있는 지점이 둘 미만이면 null', () => {
    expect(defaultInterval([p('01', 10), p('02', null)])).toBeNull();
  });
});

describe('changedDates / intervalEndingAt', () => {
  const points = [p('01', 10), p('02', 11), p('03', 11), p('04', 9)];
  it('변한 지점만 핀으로 뽑는다', () => {
    expect(changedDates(points)).toEqual(['02', '04']);
  });
  it('핀은 직전 지점과 짝을 이룬다', () => {
    expect(intervalEndingAt(points, '04')).toEqual({ previousDate: '03', currentDate: '04' });
    expect(intervalEndingAt(points, '01')).toBeNull();
  });
});

describe('pickPoint', () => {
  it('두 번 골라야 구간이 되고 날짜 순으로 정렬한다', () => {
    const first = pickPoint(null, '05');
    expect(first).toEqual({ anchor: '05', interval: null });
    expect(pickPoint(first.anchor, '02')).toEqual({ anchor: null, interval: { previousDate: '02', currentDate: '05' } });
  });
  it('같은 점을 다시 누르면 시작점만 남는다', () => {
    expect(pickPoint('05', '05')).toEqual({ anchor: '05', interval: null });
  });
});
