// Run against Spring bootRun. Requires Playwright and a Chromium installation.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.DASHBOARD_URL || 'http://localhost:8080';
const out = process.env.DASHBOARD_SCREENSHOT_DIR || '/tmp/mapledelta-dashboard-qa';
fs.mkdirSync(out, { recursive: true });
const info = { name: '메이플용사', world: '스카니아', className: '나이트로드', level: 285, guild: '성장의 기록', image: null };
const points = Array.from({ length: 30 }, (_, i) => ({ date: `2026-${i < 23 ? '08' : '09'}-${String(i < 23 ? i + 9 : i - 22).padStart(2, '0')}`, combatPower: 161746000 + Math.round(i / 29 * 20704320), apiCombatPower: 156000000 + Math.round(i / 29 * 20820150), level: 285, solErdaFragments: null }));
const detail = { itemChanges: [
  { slot: '장갑', changeType: 'STAT_CHANGED', previousItemName: '아케인셰이드 시프글러브', currentItemName: '아케인셰이드 시프글러브', deltas: [{ statName: 'ATTACK_POWER', delta: 12 }, { statName: 'LUK', delta: 15 }], deltaGroups: [{ category: '옵션', deltas: [{ statName: 'ATTACK_POWER', delta: 12 }, { statName: 'LUK', delta: 15 }] }] },
  { slot: '펜던트', changeType: 'STAT_CHANGED', previousItemName: '도미네이터 펜던트', currentItemName: '도미네이터 펜던트', deltas: [{ statName: 'LUK_PERCENT', delta: 6 }] },
], coreChanges: [
  { source: 'symbol', entries: [{ name: '어센틱심볼 : 아르테리아', previous: 'Lv. 7', current: 'Lv. 8' }], deltas: [{ statName: 'LUK_NO_PERCENT', delta: 100 }] },
  { source: 'skill', entries: [{ name: '쇼다운 챌린지', previous: 'Lv. 29', current: 'Lv. 30' }], deltas: [] },
  { source: 'unionRaider', entries: [{ name: '공격대원', previous: '8,850', current: '8,900' }], deltas: [{ statName: 'CRITICAL_DAMAGE', delta: .5 }] },
], cashChanges: [], petChanges: [] };
function sse(points, infoOverride = {}, done = true, extra = {}) {
  const event = (name, data) => `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`;
  return event('meta', { ocid: 'test-ocid', range: 'daily', characterInfo: { ...info, ...infoOverride }, plannedCount: points.length, requestedCount: points.length, dates: points.map(p => p.date).reverse(), ...extra }) + [...points].reverse().map((point, i) => event('point', { index: i + 1, total: points.length, point })).join('') + (done ? event('done', { loadedCount: points.length, truncated: !!extra.truncated }) : '');
}
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1120 }, deviceScaleFactor: 1 });
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    let mode = 'normal', detailFail = false, delayDetail = false, detailRequests = [];
    await page.route('**/api/analysis/combat-power/history/stream?**', async route => {
      const name = new URL(route.request().url()).searchParams.get('characterName');
      if (name === 'slow') await new Promise(resolve => setTimeout(resolve, 450));
      if (mode === 'disconnect') return route.abort();
      if (mode === 'malformed') return route.fulfill({ contentType: 'text/event-stream', body: 'event: meta\ndata: nope\n\n' });
      if (mode === 'notfound') return route.fulfill({ contentType: 'text/event-stream', body: 'event: error\ndata: {"code":"NOT_FOUND","message":"missing"}\n\n' });
      let sample = points;
      if (mode === 'one') sample = [points[0]];
      if (mode === 'empty') sample = [];
      if (mode === 'missing') sample = points.map((p, i) => ({ ...p, combatPower: i === 15 ? null : p.combatPower }));
      if (mode === 'zero') sample = points.map(p => ({ ...p, combatPower: 0, apiCombatPower: 0 }));
      const overrides = { name: name === 'slow' ? 'STALE' : name, ...(mode === 'demon' ? { className: '데몬어벤져' } : {}) };
      return route.fulfill({ contentType: 'text/event-stream', body: sse(sample, overrides, true, mode === 'missing' ? { truncated: true } : {}) });
    });
    await page.route('**/api/analysis/combat-power/detail?**', async route => {
      const query = new URL(route.request().url()).searchParams;
      detailRequests.push([query.get('previousDate'), query.get('currentDate')]);
      const slow = delayDetail;
      if (slow) await new Promise(resolve => setTimeout(resolve, 500));
      if (detailFail) return route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
      const copy = structuredClone(detail);
      if (slow) copy.itemChanges[0].currentItemName = 'STALE DETAIL';
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ changeSummary: copy }) });
    });
    const search = async name => { await page.locator('#character-input').fill(name); await page.locator('#search-form button').click(); };
    await page.goto(`${base}/index6.html`);
    await page.locator('#welcome').waitFor({ state: 'visible' });
    await page.screenshot({ path: path.join(out, 'welcome.png'), fullPage: true });
    await search('메이플용사');
    await page.locator('.change-row').first().waitFor();
    assert.equal(await page.locator('#combat-power').innerText(), '182,450,320');
    assert.equal(await page.locator('.change-row').count(), 5);
    assert.equal(await page.locator('#count-core').innerText(), '3건');
    await page.screenshot({ path: path.join(out, 'desktop.png'), fullPage: true });
    await page.locator('.change-summary').first().click();
    assert.ok(await page.locator('.change-row').first().getAttribute('open') !== null);
    assert.match(await page.locator('.change-body').first().innerText(), /공격력 \+12/);
    await page.locator('[data-filter="core"]').click(); assert.equal(await page.locator('.change-row').count(), 3);
    await page.locator('[data-filter="other"]').click(); assert.match(await page.locator('#detail-notice').innerText(), /변경 내역이 없/);
    await page.locator('[data-filter="all"]').click();
    await page.locator('#previous-date').selectOption(points[20].date); await page.locator('#current-date').selectOption(points[4].date); await page.locator('#compare-dates').click();
    await page.waitForFunction(() => document.querySelector('#change-list').getAttribute('aria-busy') === 'false');
    assert.deepEqual(detailRequests.at(-1), [points[4].date, points[20].date]);
    const count = detailRequests.length;
    await page.locator('#previous-date').selectOption(points[4].date); await page.locator('#current-date').selectOption(points[4].date); await page.locator('#compare-dates').click();
    assert.equal(detailRequests.length, count); assert.match(await page.locator('#selection-hint').innerText(), /서로 다른/);
    await page.locator('#compare-mode').check();
    await page.locator(`[data-date="${points[5].date}"]`).focus(); await page.keyboard.press('Enter');
    assert.match(await page.locator('#selection-hint').innerText(), /다른 날짜/);
    await page.locator(`[data-date="${points[10].date}"]`).focus(); await page.keyboard.press('Enter');
    await page.waitForFunction(() => document.querySelector('#change-list').getAttribute('aria-busy') === 'false');
    assert.deepEqual(detailRequests.at(-1), [points[5].date, points[10].date]);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(out, 'mobile.png'), fullPage: true });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.setViewportSize({ width: 1440, height: 1120 });
    detailFail = true;
    await page.locator('#compare-dates').click(); await page.locator('#detail-retry').waitFor({ state: 'visible' });
    assert.match(await page.locator('#detail-notice').innerText(), /HTTP 500/);
    assert.ok(await page.locator('[data-filter="core"]').isDisabled());
    detailFail = false; await page.locator('#detail-retry').click(); await page.locator('.change-row').first().waitFor();
    delayDetail = true; await page.locator('#compare-dates').click();
    await page.waitForTimeout(50); delayDetail = false;
    await page.locator('#previous-date').selectOption(points[1].date); await page.locator('#current-date').selectOption(points[2].date); await page.locator('#compare-dates').click();
    await page.waitForTimeout(650); assert.ok(!(await page.locator('#change-list').innerText()).includes('STALE DETAIL'));
    await search('slow'); await search('새캐릭터'); await page.waitForTimeout(650);
    assert.equal(await page.locator('#character-name').innerText(), '새캐릭터');
    await page.locator('[data-range="monthly"]').click(); await page.locator('.change-row').first().waitFor();
    assert.equal(await page.locator('[data-range="monthly"]').getAttribute('aria-pressed'), 'true');
    for (const scenario of ['notfound', 'malformed', 'disconnect']) {
      mode = scenario; await search('테스트'); await page.locator('#history-retry').waitFor({ state: 'visible' });
      assert.ok(await page.locator('#compare-dates').isDisabled());
    }
    for (const scenario of ['empty', 'one', 'missing', 'zero', 'demon']) {
      mode = scenario; await search('테스트'); await page.waitForFunction(() => document.querySelector('#chart-progress').textContent.includes('조회 완료'));
      if (scenario === 'empty' || scenario === 'one') assert.ok(await page.locator('#compare-dates').isDisabled());
      if (scenario === 'missing') assert.match(await page.locator('#history-notice').innerText(), /1개 지점/);
      if (scenario === 'zero') assert.equal(await page.locator('#combat-power').innerText(), '0');
      if (scenario === 'demon') assert.ok(await page.locator('#accuracy-warning').isVisible());
    }
    mode = 'normal'; await search('<img src=x onerror=alert(1)>'); await page.locator('.change-row').first().waitFor();
    assert.equal(await page.locator('#character-name img').count(), 0);
    assert.equal(await page.locator('#character-name').innerText(), '<img src=x onerror=alert(1)>');
    assert.deepEqual(errors, []);
    console.log('PASS: layout, filters, expanded rows, date normalization, keyboard selection, mobile overflow, detail retry/race, rapid search, range change, SSE errors, missing/zero/single/empty data, accuracy badge, text safety.');
    console.log(`Screenshots: ${out}`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
