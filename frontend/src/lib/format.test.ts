import { describe, expect, it } from 'vitest';
import { formatAxisDate, formatCompact, formatGameNumber, formatPercentChange, formatSigned, formatSignedGame, formatStatDelta } from './format';

describe('format', () => {
  it('compact', () => {
    expect(formatCompact(169949984)).toBe('1.7억');
    expect(formatCompact(1500000000)).toBe('15억');
    expect(formatCompact(84200)).toBe('8.42만');
    expect(formatCompact(999)).toBe('999');
  });
  it('signed / percent', () => {
    expect(formatSigned(8745674)).toBe('+8,745,674');
    expect(formatSigned(-12)).toBe('-12');
    expect(formatPercentChange(161204310, 169949984)).toBe('+5.4%');
  });
  it('stat delta', () => {
    expect(formatStatDelta('STR', 12)).toBe('+12');
    expect(formatStatDelta('BOSS_DAMAGE', 10)).toBe('+10%');
    expect(formatStatDelta('ATTACK_POWER', -3.456)).toBe('-3.46');
  });
  it('axis date', () => {
    expect(formatAxisDate('2026-09-04', 'daily')).toBe('9/4');
    expect(formatAxisDate('2026-09-01', 'monthly')).toBe('2026.09');
  });
});

describe('formatGameNumber', () => {
  it('게임 스탯창처럼 억 · 만 · 나머지로 끊는다', () => {
    expect(formatGameNumber(156536528)).toBe('1억 5653만 6528');
    expect(formatGameNumber(1232773)).toBe('123만 2773');
    expect(formatGameNumber(9301499)).toBe('930만 1499');
  });

  it('비는 자리는 적지 않는다', () => {
    expect(formatGameNumber(100000000)).toBe('1억');
    expect(formatGameNumber(100010000)).toBe('1억 1만');
    expect(formatGameNumber(100000001)).toBe('1억 1');
    expect(formatGameNumber(50000)).toBe('5만');
  });

  it('만 미만은 그대로, 음수는 부호를 앞에 둔다', () => {
    expect(formatGameNumber(0)).toBe('0');
    expect(formatGameNumber(9999)).toBe('9999');
    expect(formatGameNumber(-1232773)).toBe('-123만 2773');
    expect(formatSignedGame(1232773)).toBe('+123만 2773');
    expect(formatSignedGame(-1232773)).toBe('-123만 2773');
  });
});

describe('formatStatDelta 단위', () => {
  it('재사용 감소는 초, 미적용은 %', () => {
    expect(formatStatDelta('COOLDOWN_SECOND', 2)).toBe('+2초');
    expect(formatStatDelta('COOLDOWN_SECOND', -1)).toBe('-1초');
    expect(formatStatDelta('COOLDOWN_SKIP_PERCENT', 20)).toBe('+20%');
    expect(formatStatDelta('ATTACK_POWER', 30)).toBe('+30');
  });
});
