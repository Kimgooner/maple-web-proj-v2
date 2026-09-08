import test from 'node:test';
import assert from 'node:assert/strict';
import { number, percentage, validPoints, orderedInterval, intervalEndingAt, defaultInterval, lineSegments, chartDomain, changeRows, statText, safeImageUrl } from '../../main/resources/static/internal-dashboard/model.mjs';
const point = (date, combatPower, apiCombatPower = combatPower) => ({ date, combatPower, apiCombatPower });
test('missing values differ from genuine zero', () => {
  assert.equal(number(null), '—'); assert.equal(number(0), '0'); assert.equal(number(NaN), '—');
  assert.equal(percentage(0, 100), '—'); assert.equal(percentage(100, 90), '-10.00%');
  assert.equal(validPoints([point('a', null), point('b', 0), point('c', NaN)]).length, 1);
});
test('comparison normalizes reverse selection and rejects repeated date', () => {
  assert.equal(orderedInterval('2026-09-01', '2026-09-01'), null);
  assert.deepEqual(orderedInterval('2026-09-03', '2026-09-01'), { previousDate: '2026-09-01', currentDate: '2026-09-03' });
});
test('default selection uses last changed pair, falling back to latest flat pair', () => {
  const points = [point('01', 100), point('02', 110), point('03', null), point('04', 110)];
  assert.deepEqual(defaultInterval(points), { previousDate: '01', currentDate: '02' });
  assert.deepEqual(intervalEndingAt(points, '04'), { previousDate: '02', currentDate: '04' });
  assert.equal(intervalEndingAt(points, '01'), null);
  assert.equal(intervalEndingAt(points, '03'), null);
  assert.equal(defaultInterval([point('01', 0)]), null);
  assert.equal(defaultInterval([]), null);
  assert.deepEqual(defaultInterval([point('01', 0), point('02', 0)]), { previousDate: '01', currentDate: '02' });
});
test('each series independently breaks at missing dates', () => {
  const points = [point('01', 100, null), point('02', null, 80), point('03', 120, 90)];
  assert.deepEqual(lineSegments(points, 'combatPower', x => x * 10, y => y), [[[0, 100]], [[20, 120]]]);
  assert.deepEqual(lineSegments(points, 'apiCombatPower', x => x * 10, y => y), [[[10, 80], [20, 90]]]);
});
test('chart domain stays finite and nonzero for flat, zero, empty and partial series', () => {
  for (const points of [[], [point('01', 0)], [point('01', null, 100)], [point('01', 100), point('02', 100)]]) {
    const [low, high] = chartDomain(points); assert.ok(Number.isFinite(low) && Number.isFinite(high)); assert.ok(high > low);
  }
});
test('source counts do not duplicate aggregate deltas for every entry', () => {
  const rows = changeRows({ itemChanges: [{}], coreChanges: [{ source: 'symbol', entries: [{ name: 'a' }, { name: 'b' }] }], cashChanges: [{}], petChanges: [{}] });
  assert.equal(rows.length, 4); assert.equal(rows.filter(r => r.category === 'core').length, 1);
  assert.equal(rows.filter(r => r.category === 'other').length, 2);
  assert.equal(rows[1].label, '심볼'); assert.deepEqual(changeRows(null), []);
});
test('stat labels preserve percentage and fixed distinctions', () => {
  assert.equal(statText({ statName: 'ATTACK_POWER_PERCENT', delta: 6 }), '공격력 +6%');
  assert.equal(statText({ statName: 'LUK_NO_PERCENT', delta: 100 }), 'LUK(고정) +100');
  assert.equal(statText({ statName: 'BOSS_DAMAGE', delta: -3 }), '보공 -3%');
});
test('images reject executable and malformed URL schemes', () => {
  for (const value of ['javascript:alert(1)', 'data:image/svg+xml,<svg/>', '//example.com/x', 'oops', null]) assert.equal(safeImageUrl(value), null);
  assert.equal(safeImageUrl('https://example.com/item.png'), 'https://example.com/item.png');
});
