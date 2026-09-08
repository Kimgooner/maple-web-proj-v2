import { describe, expect, it } from 'vitest';
import { expiredLabel, latestExpired } from './expired';
import type { HistoryPoint } from '../api/types';

const point = (date: string, combatPower: number | null, expired: HistoryPoint['expired'] = null): HistoryPoint =>
  ({ date, level: 1, combatPower, apiCombatPower: null, solErdaFragments: null, expired });

describe('expiredLabel', () => {
  it('만료된 것만 골라 이어 붙인다', () => {
    expect(expiredLabel({ artifactCrystals: 3, cashItems: 0, petEquipments: 1, titleOption: true }))
      .toBe('칭호 옵션 · 펫 장비 1개 · 유니온 아티팩트 크리스탈 3개');
  });

  it('없으면 null 이라 줄 자체가 안 뜬다', () => {
    expect(expiredLabel(null)).toBeNull();
    expect(expiredLabel({ artifactCrystals: 0, cashItems: 0, petEquipments: 0, titleOption: false })).toBeNull();
  });
});

describe('latestExpired', () => {
  it('값이 있는 마지막 지점을 본다. 아직 안 온 지점은 건너뛴다', () => {
    const expired = { artifactCrystals: 0, cashItems: 2, petEquipments: 0, titleOption: false };
    expect(latestExpired([point('01', 10, expired), point('02', null)])).toEqual(expired);
  });
});
