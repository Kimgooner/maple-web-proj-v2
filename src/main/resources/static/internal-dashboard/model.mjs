// Contract reference: frontend/src/api/types.ts and frontend/src/lib/{history,labels}.ts.
export const isNumber = value => typeof value === 'number' && Number.isFinite(value);
export const number = value => isNumber(value) ? new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(value) : '—';
export const signed = value => isNumber(value) ? `${value > 0 ? '+' : ''}${number(value)}` : '—';
export const tone = value => !isNumber(value) || value === 0 ? 'neutral' : value > 0 ? 'positive' : 'negative';
export function percentage(before, after) {
  if (!isNumber(before) || !isNumber(after) || before === 0) return '—';
  const value = (after - before) / before * 100;
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}
export function compact(value) {
  if (!isNumber(value)) return '—';
  const divisor = Math.abs(value) >= 1e8 ? 1e8 : Math.abs(value) >= 1e4 ? 1e4 : 1;
  return `${number(value / divisor)}${divisor === 1e8 ? '억' : divisor === 1e4 ? '만' : ''}`;
}
export const dateLabel = date => date ? date.replaceAll('-', '.') : '—';
export const shortDate = date => date ? date.slice(5).replace('-', '.') : '—';
export const validPoints = points => points.filter(point => isNumber(point.combatPower));
export function orderedInterval(first, second) {
  if (!first || !second || first === second) return null;
  const [previousDate, currentDate] = [first, second].sort();
  return { previousDate, currentDate };
}
export function intervalEndingAt(points, date) {
  const valid = validPoints(points);
  const index = valid.findIndex(point => point.date === date);
  return index > 0 ? orderedInterval(valid[index - 1].date, date) : null;
}
export function defaultInterval(points) {
  const valid = validPoints(points);
  if (valid.length < 2) return null;
  for (let i = valid.length - 1; i > 0; i--) {
    if (valid[i].combatPower !== valid[i - 1].combatPower) return orderedInterval(valid[i - 1].date, valid[i].date);
  }
  return orderedInterval(valid.at(-2).date, valid.at(-1).date);
}
// Keep missing and pending dates in place: neither line may bridge their gaps.
export function lineSegments(points, key, x, y) {
  const segments = [];
  let current = [];
  points.forEach((point, index) => {
    if (isNumber(point[key])) current.push([x(index), y(point[key])]);
    else if (current.length) { segments.push(current); current = []; }
  });
  if (current.length) segments.push(current);
  return segments;
}
export function chartDomain(points) {
  const values = points.flatMap(point => [point.combatPower, point.apiCombatPower]).filter(isNumber);
  if (!values.length) return [0, 1];
  const min = Math.min(...values), max = Math.max(...values);
  const padding = Math.max((max - min) * .2, Math.abs(max) * .02, 1);
  return [Math.max(0, min - padding), max + padding];
}
const sources = { abilityPoint: 'AP 배분', symbol: '심볼', skill: '스킬', hexaStat: '헥사스탯', hexaCore: '헥사 코어', ability: '어빌리티', hyperStat: '하이퍼스탯', setEffect: '세트효과', unionOccupied: '유니온 점령', unionRaider: '유니온 공격대', unionArtifact: '유니온 아티팩트', unionChampion: '유니온 챔피언' };
export const sourceLabel = source => sources[source] ?? source;
const stats = { ATTACK_POWER: '공격력', MAGIC_POWER: '마력', ALL_STAT: '올스탯', DAMAGE: '데미지', BOSS_DAMAGE: '보공', CRITICAL_DAMAGE: '크뎀', FINAL_DAMAGE: '최종뎀' };
export function statText({ statName, delta }) {
  const base = statName.replace(/_NO_PERCENT$/, '').replace(/_PERCENT$/, '').replace(/_PER_LEVEL9$/, '');
  const suffix = statName.endsWith('_NO_PERCENT') ? '(고정)' : statName.endsWith('_PER_LEVEL9') ? '/9렙' : '';
  const percent = (statName.endsWith('_PERCENT') && !statName.endsWith('_NO_PERCENT')) || ['DAMAGE', 'BOSS_DAMAGE', 'CRITICAL_DAMAGE', 'FINAL_DAMAGE'].includes(statName);
  return `${stats[base] ?? base}${suffix} ${signed(delta)}${percent ? '%' : ''}`;
}
export const changeLabel = type => ({ ADDED: '장착', REMOVED: '해제', REPLACED: '교체', STAT_CHANGED: '옵션 변경' })[type] ?? type;
export function changeRows(summary) {
  if (!summary) return [];
  const slots = (key, category, label) => (summary[key] ?? []).map(change => ({ kind: 'slot', category, label, change }));
  return [
    ...slots('itemChanges', 'items', '장비'),
    ...(summary.coreChanges ?? []).map(change => ({ kind: 'source', category: 'core', label: sourceLabel(change.source), change })),
    ...slots('cashChanges', 'other', '캐시'), ...slots('petChanges', 'other', '펫'),
  ];
}
export function safeImageUrl(value) {
  if (!value) return null;
  try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : null; } catch { return null; }
}
