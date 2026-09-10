import { describe, expect, it } from 'vitest';
import type { CharacterInfo, StatDelta } from '../api/types';
import { presentDeltas } from './stats';

const mage = {
  name: '테스트', className: '비숍', level: 285, guild: null, world: null, image: null,
  mainStats: ['INT'], subStats: ['LUK'], usesMagic: true,
} as CharacterInfo;

const d = (statName: string, delta: number): StatDelta => ({ statName, delta });
const names = (deltas: StatDelta[], info: CharacterInfo | null = mage) =>
  presentDeltas(deltas, info).map((delta) => delta.statName);

describe('presentDeltas', () => {
  it('중요한 순으로 놓는다', () => {
    expect(names([d('INT', 100), d('BOSS_DAMAGE', 5), d('MAGIC_POWER_PERCENT', 3)]))
      .toEqual(['MAGIC_POWER_PERCENT', 'BOSS_DAMAGE', 'INT']);
  });

  it('이 직업이 안 쓰는 스탯은 지운다', () => {
    expect(names([d('STR', 100), d('ATTACK_POWER', 10), d('INT', 50)])).toEqual(['INT']);
  });

  it('솔 에르다 조각은 언제나 맨 뒤다. 전투력 스탯이 아니라 "얼마나 부었나"다', () => {
    expect(names([d('SOL_ERDA_FRAGMENT', 150), d('BOSS_DAMAGE', 5)]))
      .toEqual(['BOSS_DAMAGE', 'SOL_ERDA_FRAGMENT']);
    // 순서에 없는 스탯들 사이에서도 뒤에 선다
    expect(names([d('SOL_ERDA_FRAGMENT', 150), d('HP', 4000), d('FINAL_DAMAGE', 2)]))
      .toEqual(['HP', 'FINAL_DAMAGE', 'SOL_ERDA_FRAGMENT']);
  });

  it('직업을 모르면 지우지 않는다. 잘못 지우느니 많이 보여주는 쪽이 낫다', () => {
    expect(names([d('STR', 100), d('INT', 50)], null)).toEqual(['STR', 'INT']);
  });
});
