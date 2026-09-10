const PERCENT_STATS = new Set([
  'STR_PERCENT', 'DEX_PERCENT', 'INT_PERCENT', 'LUK_PERCENT', 'HP_PERCENT', 'ALL_STAT_PERCENT',
  'ATTACK_POWER_PERCENT', 'MAGIC_POWER_PERCENT',
  'DAMAGE', 'BOSS_DAMAGE', 'CRITICAL_DAMAGE', 'FINAL_DAMAGE',
  'COOLDOWN_SKIP_PERCENT',
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

/**
 * 인게임 표기. {@code 156536528 → "1억 5653만 6528"}
 *
 * <p>게임 안 스탯창이 전투력을 이렇게 적는다. 세 자리씩 끊은 {@code 156,536,528} 은
 * 자릿수를 세어야 크기를 알 수 있는데, 이쪽은 "1억 5천대"가 바로 읽힌다. 자리를 줄이지
 * 않아 값은 그대로다 — 요약이 아니라 표기 방식만 바꾼 것이다.
 */
export function formatGameNumber(value: number): string {
  const abs = Math.abs(Math.trunc(value));
  const sign = value < 0 ? '-' : '';
  if (abs < 10000) return `${sign}${abs}`;

  const parts: string[] = [];
  const eok = Math.floor(abs / 1e8);
  const man = Math.floor((abs % 1e8) / 1e4);
  const rest = abs % 1e4;
  if (eok) parts.push(`${formatNumber(eok)}억`);
  if (man) parts.push(`${man}만`);
  if (rest) parts.push(`${rest}`);
  return sign + parts.join(' ');
}

/** 인게임 표기에 부호를 붙인다. 증감 칸에 쓴다. */
export function formatSignedGame(value: number): string {
  return `${value > 0 ? '+' : ''}${formatGameNumber(value)}`;
}

export function formatSigned(value: number): string {
  return `${value > 0 ? '+' : value < 0 ? '-' : ''}${formatNumber(Math.abs(value))}`;
}

export function formatPercentChange(previous: number, current: number): string {
  if (previous === 0) return '';
  const pct = ((current - previous) / previous) * 100;
  return `${pct > 0 ? '+' : ''}${pct.toFixed(1)}%`;
}

/** 뒤에 단위가 붙는 스탯. 나머지는 숫자만 적는다. */
function unitOf(statName: string): string {
  if (PERCENT_STATS.has(statName)) return '%';
  if (statName === 'COOLDOWN_SECOND') return '초';
  return '';
}

/** 스탯 delta. 정수는 그대로, 소수는 둘째 자리까지. % 계열은 뒤에 %, 재사용 감소는 초 */
export function formatStatDelta(statName: string, delta: number): string {
  const rounded = Math.round(delta * 100) / 100;
  const body = Number.isInteger(rounded) ? formatNumber(Math.abs(rounded)) : Math.abs(rounded).toString();
  const sign = rounded > 0 ? '+' : rounded < 0 ? '-' : '';
  return `${sign}${body}${unitOf(statName)}`;
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
