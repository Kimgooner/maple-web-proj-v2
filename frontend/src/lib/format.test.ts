import { describe, expect, it } from 'vitest';
import { formatAxisDate, formatCompact, formatPercentChange, formatSigned, formatStatDelta } from './format';

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
