const PERCENT_STATS = new Set([
  'STR_PERCENT', 'DEX_PERCENT', 'INT_PERCENT', 'LUK_PERCENT', 'HP_PERCENT', 'ALL_STAT_PERCENT',
  'ATTACK_POWER_PERCENT', 'MAGIC_POWER_PERCENT',
  'DAMAGE', 'BOSS_DAMAGE', 'CRITICAL_DAMAGE', 'FINAL_DAMAGE',
]);

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('ko-KR').format(value);
}

/** 1억 이상은 억, 1만 이상은 만 단위로 줄인다. 차트 축과 요약에 쓴다 */
export function formatCompact(value: number): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1e8) return `${sign}${trim(abs / 1e8)}억`;
  if (abs >= 1e4) return `${sign}${trim(abs / 1e4)}만`;
  return `${sign}${formatNumber(abs)}`;
}

function trim(n: number): string {
  return n >= 100 ? Math.round(n).toString() : n.toFixed(n >= 10 ? 1 : 2).replace(/\.?0+$/, '');
}

export function formatSigned(value: number): string {
  return `${value > 0 ? '+' : value < 0 ? '-' : ''}${formatNumber(Math.abs(value))}`;
}

export function formatPercentChange(previous: number, current: number): string {
  if (previous === 0) return '';
  const pct = ((current - previous) / previous) * 100;
  return `${pct > 0 ? '+' : ''}${pct.toFixed(1)}%`;
}

/** 스탯 delta. 정수는 그대로, 소수는 둘째 자리까지. % 계열은 뒤에 % */
export function formatStatDelta(statName: string, delta: number): string {
  const rounded = Math.round(delta * 100) / 100;
  const body = Number.isInteger(rounded) ? formatNumber(Math.abs(rounded)) : Math.abs(rounded).toString();
  const sign = rounded > 0 ? '+' : rounded < 0 ? '-' : '';
  return `${sign}${body}${PERCENT_STATS.has(statName) ? '%' : ''}`;
}

/** "2026-09-04" → "2026.09.04" */
export function dateLabel(date: string): string {
  return date ? date.replaceAll('-', '.') : '—';
}

/** "2026-09-04" → "09.04" */
export function shortDate(date: string): string {
  return date ? date.slice(5).replace('-', '.') : '—';
}

/** 차트 축용. formatCompact 의 별칭 — 원본 대시보드가 쓰던 이름이다. */
export const compact = formatCompact;

/** "2026-09-04" → "9/4", 월간이면 "2026.09" */
export function formatAxisDate(date: string, range: 'daily' | 'monthly'): string {
  const [y, m, d] = date.split('-');
  return range === 'monthly' ? `${y}.${m}` : `${Number(m)}/${Number(d)}`;
}

/** "2026-09-04" → "2026년 9월 4일" */
export function formatLongDate(date: string): string {
  const [y, m, d] = date.split('-');
  return `${y}년 ${Number(m)}월 ${Number(d)}일`;
}
