import { describe, expect, it } from 'vitest';
import type { LevelBandWeek } from '../api/levelBands';
import { bandAt } from './levelBands';

const band = (from: number, to: number, median: number) => ({
  from, to, sampleSize: 900,
  top1Percent: median * 8, top10Percent: median * 4, top30Percent: median * 2,
  median, mean: median * 1.2,
});

const weeks: LevelBandWeek[] = [
  { weekOf: '2026-08-31', rankingDate: '2026-08-30', bands: [band(270, 274, 900), band(275, 279, 1500)] },
  { weekOf: '2026-09-07', rankingDate: '2026-09-06', bands: [band(270, 274, 920), band(275, 279, 1611)] },
];

describe('bandAt', () => {
  it('지점 날짜 이전의 가장 가까운 주를 쓴다', () => {
    expect(bandAt(weeks, '2026-09-03', 272)?.stats.median).toBe(900);
    expect(bandAt(weeks, '2026-09-09', 272)?.stats.median).toBe(920);
  });

  it('레벨이 구간에 없거나 주가 없으면 기준선을 그리지 않는다', () => {
    expect(bandAt(weeks, '2026-09-09', 250)).toBeNull();
    expect(bandAt(weeks, '2026-09-09', null)).toBeNull();
    expect(bandAt([], '2026-09-09', 272)).toBeNull();
  });

  it('가진 주보다 이른 지점은 가장 오래된 주로 대신하되, 한참 이르면 추정으로 표시한다', () => {
    expect(bandAt(weeks, '2026-08-20', 272)?.stats.median).toBe(900);
    expect(bandAt(weeks, '2026-08-20', 272)?.estimated).toBe(false);
    expect(bandAt(weeks, '2025-11-01', 272)?.estimated).toBe(true);
  });
});
