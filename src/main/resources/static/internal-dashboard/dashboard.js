import { isNumber, number, signed, tone, percentage, compact, dateLabel, shortDate, validPoints, orderedInterval, intervalEndingAt, defaultInterval, lineSegments, chartDomain, sourceLabel, statText, changeLabel, changeRows, safeImageUrl } from './model.mjs';

const $ = id => document.getElementById(id);
const state = { name: '', range: 'daily', meta: null, points: [], status: 'idle', received: 0, interval: null, anchor: null, summary: null, filter: 'all' };
let stream = null, historyVersion = 0, detailVersion = 0, detailController = null;
const groups = [['all', '전체'], ['items', '장비'], ['core', '핵심'], ['other', '캐시 · 펫']];
function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}
function setText(id, text) { $(id).textContent = text; }
function setMetric(id, text, value) { const node = $(id); node.textContent = text; node.classList.remove('positive', 'negative', 'neutral'); node.classList.add(tone(value)); }
function image(url, alt = '') {
  const safe = safeImageUrl(url);
  if (!safe) return null;
  const img = element('img'); img.src = safe; img.alt = alt; img.loading = 'lazy';
  img.addEventListener('error', () => img.remove(), { once: true });
  return img;
}
function showHistoryNotice(text) { setText('history-notice', text); $('history-notice').hidden = !text; }
function cancelDetail() { detailVersion++; detailController?.abort(); detailController = null; }
function resetDetail() {
  cancelDetail(); state.summary = null; state.interval = null; state.anchor = null; state.filter = 'all';
  setText('interval-title', '두 시점을 선택해 주세요');
  setMetric('interval-delta', '—', null); setMetric('interval-percent', '', null);
  setText('interval-values', '전투력의 변화를 비교합니다'); setText('changes-dates', '');
  $('detail-retry').hidden = true; $('change-list').setAttribute('aria-busy', 'false'); renderChanges();
}
function setSelectionEnabled() {
  const enabled = state.status === 'done' && validPoints(state.points).length >= 2;
  for (const id of ['compare-mode', 'previous-date', 'current-date', 'compare-dates']) $(id).disabled = !enabled;
}
function renderProfile() {
  const info = state.meta?.characterInfo;
  setText('character-name', info?.name || state.name || '—');
  setText('world', info?.world || ''); $('world').hidden = !info?.world;
  const latest = validPoints(state.points).at(-1);
  const chronologicalLatest = state.points.at(-1);
  const level = latest?.level ?? info?.level;
  setText('character-meta', [level == null ? null : `Lv. ${level}`, info?.className, info?.guild].filter(Boolean).join(' · '));
  $('accuracy-warning').hidden = info?.className !== '데몬어벤져';
  setText('combat-power', number(latest?.combatPower));
  // Match the reference value to the same date as the displayed computed value.
  setText('api-power', number((latest ?? chronologicalLatest)?.apiCombatPower));
  setText('power-date', latest ? `${dateLabel(latest.date)} 기준` : '계산 데이터를 기다리고 있어요');
  const valid = validPoints(state.points), first = valid[0];
  const comparable = state.status === 'done' && valid.length >= 2;
  const delta = comparable ? latest.combatPower - first.combatPower : null;
  setText('period-label', state.status === 'loading' ? '조회 중' : '조회 구간 변화');
  setMetric('period-percent', comparable ? percentage(first.combatPower, latest.combatPower) : '—', delta);
  setMetric('period-delta', signed(delta), delta);
  setText('period-dates', comparable ? `${shortDate(first.date)} — ${shortDate(latest.date)}` : '');
}
function setAvatar() {
  const avatar = $('avatar'); avatar.replaceChildren();
  const img = image(state.meta?.characterInfo?.image);
  avatar.append(img ?? document.createTextNode('✦'));
}
function renderDates() {
  const valid = validPoints(state.points);
  for (const id of ['previous-date', 'current-date']) {
    const select = $(id); select.replaceChildren();
    valid.forEach(point => { const option = element('option', '', dateLabel(point.date)); option.value = point.date; select.append(option); });
  }
  if (state.interval) {
    $('previous-date').value = state.interval.previousDate;
    $('current-date').value = state.interval.currentDate;
  } else if (valid.length) { $('previous-date').value = valid[0].date; $('current-date').value = valid.at(-1).date; }
  setSelectionEnabled();
}
function svgNode(tag, attributes = {}, text) {
  const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [key, value] of Object.entries(attributes)) node.setAttribute(key, value);
  if (text != null) node.textContent = text;
  return node;
}
function renderChart() {
  const container = $('chart'); container.replaceChildren();
  const points = state.points;
  setText('chart-dates', points.length ? `${dateLabel(points[0].date)} — ${dateLabel(points.at(-1).date)}` : '');
  if (!points.some(point => isNumber(point.combatPower) || isNumber(point.apiCombatPower))) {
    container.append(element('div', 'chart-empty', state.status === 'loading' ? '캐릭터의 성장 기록을 불러오고 있어요…' : '표시할 전투력 데이터가 없어요.'));
    return;
  }
  const width = Math.max(300, container.clientWidth), height = width < 520 ? 230 : 280;
  const left = width < 520 ? 47 : 65, right = 20, top = 24, bottom = 34;
  const plotWidth = width - left - right, plotHeight = height - top - bottom;
  const [low, high] = chartDomain(points);
  const x = index => left + (points.length === 1 ? plotWidth / 2 : index * plotWidth / (points.length - 1));
  const y = value => top + (high - value) / (high - low) * plotHeight;
  const svg = svgNode('svg', { viewBox: `0 0 ${width} ${height}`, role: 'group', 'aria-label': '전투력 추이. 날짜 지점을 선택하거나 아래 날짜 선택기를 사용하세요.' });
  svg.append(svgNode('title', {}, '실전 전투력과 넥슨 전투력의 추이'));
  for (let i = 0; i < 5; i++) {
    const value = low + (high - low) * i / 4, yy = y(value);
    svg.append(svgNode('line', { x1: left, x2: width - right, y1: yy, y2: yy, stroke: '#eef0f4', 'stroke-width': 1 }));
    svg.append(svgNode('text', { x: left - 10, y: yy + 4, 'text-anchor': 'end' }, compact(value)));
  }
  const interval = state.interval;
  if (interval) {
    const start = points.findIndex(point => point.date === interval.previousDate), end = points.findIndex(point => point.date === interval.currentDate);
    if (start >= 0 && end >= 0) {
      svg.append(svgNode('rect', { x: x(start), y: top, width: x(end) - x(start), height: plotHeight, fill: '#fff1e7' }));
      for (const index of [start, end]) svg.append(svgNode('line', { x1: x(index), x2: x(index), y1: top, y2: top + plotHeight, stroke: '#ee925f', 'stroke-dasharray': '3 4' }));
    }
  }
  for (const [key, stroke, dashed] of [['apiCombatPower', '#9ba3af', true], ['combatPower', '#ed702e', false]]) {
    for (const segment of lineSegments(points, key, x, y)) {
      const coordinates = segment.map(pair => pair.join(',')).join(' ');
      if (!dashed && segment.length > 1) svg.append(svgNode('polygon', { points: `${segment[0][0]},${top + plotHeight} ${coordinates} ${segment.at(-1)[0]},${top + plotHeight}`, fill: '#ffefe2', opacity: '.4' }));
      svg.append(svgNode('polyline', { points: coordinates, fill: 'none', stroke, 'stroke-width': dashed ? 1.8 : 2.6, 'stroke-linejoin': 'round', 'stroke-dasharray': dashed ? '6 5' : 'none' }));
      if (segment.length === 1) svg.append(svgNode('circle', { cx: segment[0][0], cy: segment[0][1], r: 3, fill: stroke }));
    }
  }
  const tickCount = Math.min(width < 520 ? 4 : 6, points.length);
  const ticks = [...new Set(Array.from({ length: tickCount }, (_, i) => Math.round(i * (points.length - 1) / Math.max(1, tickCount - 1))))];
  for (const index of ticks) svg.append(svgNode('text', { x: x(index), y: height - 10, 'text-anchor': 'middle' }, state.range === 'monthly' ? points[index].date.slice(0, 7).replace('-', '.') : shortDate(points[index].date)));
  points.forEach((point, index) => {
    if (!isNumber(point.combatPower)) return;
    const selected = point.date === interval?.previousDate || point.date === interval?.currentDate || point.date === state.anchor;
    const interactive = state.status === 'done' && validPoints(points).length >= 2;
    const group = svgNode('g', { class: 'chart-point', ...(interactive ? { role: 'button', tabindex: 0, 'aria-label': `${dateLabel(point.date)}, 실전 전투력 ${number(point.combatPower)}, 비교 지점 선택`, 'aria-pressed': selected } : {}) });
    group.append(svgNode('title', {}, `${dateLabel(point.date)} · ${number(point.combatPower)}`));
    group.append(svgNode('circle', { cx: x(index), cy: y(point.combatPower), r: selected ? 5 : 2.6, fill: selected ? '#ed702e' : '#fff', stroke: '#ed702e', 'stroke-width': 1.5 }));
    group.append(svgNode('rect', { x: x(index) - 10, y: top, width: 20, height: plotHeight, fill: 'transparent' }));
    if (interactive) {
      group.addEventListener('click', () => pickDate(point.date));
      group.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); pickDate(point.date); focusChartDate(point.date); } });
    }
    group.dataset.date = point.date; svg.append(group);
  });
  container.append(svg);
}
function focusChartDate(date) { [...$('chart').querySelectorAll('[data-date]')].find(node => node.dataset.date === date)?.focus(); }
function pickDate(date) {
  if (state.status !== 'done') return;
  if ($('compare-mode').checked) {
    if (!state.anchor || state.anchor === date) {
      state.anchor = date;
      setText('selection-hint', `${dateLabel(date)} 선택 · 비교할 다른 날짜를 선택하세요.`); renderChart(); return;
    }
    const interval = orderedInterval(state.anchor, date); state.anchor = null; selectInterval(interval);
  } else {
    const interval = intervalEndingAt(state.points, date);
    if (interval) selectInterval(interval);
    else setText('selection-hint', '첫 지점에는 이전 기록이 없어요. 다음 지점을 선택해 주세요.');
  }
}
function selectInterval(interval) {
  if (!interval || state.status !== 'done') return;
  const previous = state.points.find(point => point.date === interval.previousDate), current = state.points.find(point => point.date === interval.currentDate);
  if (!isNumber(previous?.combatPower) || !isNumber(current?.combatPower)) return;
  state.interval = interval; state.anchor = null; state.summary = null;
  const delta = current.combatPower - previous.combatPower;
  const intervalLabel = state.range === 'monthly' ? `${dateLabel(previous.date)} → ${dateLabel(current.date)}` : `${shortDate(previous.date)} → ${shortDate(current.date)}`;
  setText('interval-title', intervalLabel); setMetric('interval-delta', signed(delta), delta); setMetric('interval-percent', percentage(previous.combatPower, current.combatPower), delta);
  setText('interval-values', `${number(previous.combatPower)} → ${number(current.combatPower)}`);
  setText('changes-dates', `${dateLabel(previous.date)} → ${dateLabel(current.date)}`);
  setText('selection-hint', '선택한 구간의 변경 내역을 아래에서 확인하세요.');
  renderDates(); renderChart(); loadDetail();
}
function statSpans(deltas, limit = Infinity) {
  const fragment = document.createDocumentFragment();
  (deltas ?? []).slice(0, limit).forEach(delta => fragment.append(element('span', tone(delta.delta), statText(delta))));
  if ((deltas?.length ?? 0) > limit) fragment.append(element('span', 'muted', `외 ${deltas.length - limit}개`));
  return fragment;
}
function itemIcon(url, fallback = '◇') {
  const box = element('span', 'item-icon'); box.setAttribute('aria-hidden', 'true');
  box.append(image(url) ?? document.createTextNode(fallback)); return box;
}
function entryTable(entries) {
  const table = element('table', 'entry-table'), head = element('thead'), row = element('tr'), body = element('tbody');
  for (const title of ['항목', '이전', '이후']) { const th = element('th', '', title); th.scope = 'col'; row.append(th); }
  head.append(row); table.append(head, body);
  entries.forEach(entry => {
    const tr = element('tr'), name = element('td'), label = element('span', 'entry-label');
    const img = image(entry.icon); if (img) label.append(img);
    label.append(document.createTextNode(entry.name)); name.append(label);
    tr.append(name, element('td', 'before', entry.previous ?? '없음'), element('td', 'after', entry.current ?? '없음')); body.append(tr);
  });
  return table;
}
function renderRow(row) {
  const { change, kind, label } = row;
  const details = element('details', 'change-row'), summary = element('summary', 'change-summary');
  const name = element('div', 'change-name'), words = element('div');
  const entries = change.entries ?? [];
  const title = kind === 'slot' ? change.currentItemName ?? change.previousItemName ?? '이름 없는 항목' : sourceLabel(change.source);
  const subtitle = kind === 'slot' ? `${label} · ${change.currentSlot ?? change.slot ?? change.previousSlot ?? ''} · ${changeLabel(change.changeType)}` : entries.length ? `${entries.length}개 항목 변경` : '스탯 변화';
  name.append(itemIcon(kind === 'slot' ? change.currentItemIcon ?? change.previousItemIcon : entries[0]?.icon, kind === 'slot' ? '◇' : '✦'));
  words.append(element('div', 'item-title', title), element('div', 'item-subtitle', subtitle)); name.append(words);
  let before, after;
  if (kind === 'slot') { before = change.previousItemName ?? '미장착'; after = change.currentItemName ?? '미장착'; }
  else if (entries.length === 1) { before = `${entries[0].name} · ${entries[0].previous ?? '없음'}`; after = `${entries[0].name} · ${entries[0].current ?? '없음'}`; }
  else { before = entries.length ? entries.map(entry => `${entry.name}: ${entry.previous ?? '없음'}`).slice(0, 2).join(' / ') : '—'; after = entries.length ? entries.map(entry => `${entry.name}: ${entry.current ?? '없음'}`).slice(0, 2).join(' / ') : '—'; }
  const preview = element('div', 'stat-preview');
  if (change.deltas?.length) preview.append(statSpans(change.deltas, 2));
  else preview.append(element('span', 'muted', entries.length ? '항목 변경 · 상세 보기' : '세부 변경 보기'));
  const chevron = element('span', 'chevron', '›'); chevron.setAttribute('aria-hidden', 'true');
  summary.append(name, element('div', 'row-value before', before), element('div', 'row-value after', after), preview, chevron);
  const body = element('div', 'change-body');
  if (kind === 'source' && entries.length) { body.append(element('h3', '', '항목별 이전 · 이후'), entryTable(entries)); }
  const statGroups = kind === 'slot' && change.deltaGroups?.length ? change.deltaGroups : [{ category: '스탯 증감', deltas: change.deltas ?? [] }];
  for (const group of statGroups) {
    if (!group.deltas?.length) continue;
    const values = element('div', 'all-stats'); values.append(statSpans(group.deltas)); body.append(element('h3', '', group.category), values);
  }
  if (kind === 'slot') body.append(element('p', 'muted', '이전과 이후 시점의 스탯 증감입니다.'));
  if (!body.childNodes.length) body.append(element('p', 'muted', '추가로 표시할 스탯 변화가 없습니다.'));
  details.append(summary, body); return details;
}
function renderChanges(message) {
  const rows = changeRows(state.summary), filters = $('filters'); filters.replaceChildren();
  for (const [key, title] of groups) {
    const count = key === 'all' ? rows.length : rows.filter(row => row.category === key).length;
    const button = element('button', '', title); button.type = 'button'; button.dataset.filter = key; button.disabled = !state.summary;
    button.setAttribute('aria-pressed', state.filter === key); button.append(element('span', 'filter-count', state.summary ? String(count) : '—'));
    button.addEventListener('click', () => { state.filter = key; renderChanges(); [...filters.children].find(node => node.dataset.filter === key)?.focus(); }); filters.append(button);
  }
  for (const [id, category] of [['count-items', 'items'], ['count-core', 'core'], ['count-other', 'other']]) setText(id, state.summary ? `${rows.filter(row => row.category === category).length}건` : '—');
  const list = $('change-list'); list.replaceChildren();
  const filtered = rows.filter(row => state.filter === 'all' || row.category === state.filter);
  if (message) { setText('detail-notice', message); return; }
  if (!state.summary) { setText('detail-notice', state.interval ? '변경 내역을 불러오고 있어요…' : '두 시점을 선택하면 장비와 스킬의 변경 내역을 보여드려요.'); return; }
  setText('detail-notice', filtered.length ? '' : '이 구간에 확인된 변경 내역이 없어요.');
  if (!filtered.length) return;
  const head = element('div', 'table-head'); head.setAttribute('aria-hidden', 'true');
  for (const text of ['변경 항목', '이전', '이후', '주요 변화', '']) head.append(element('span', '', text));
  list.append(head, ...filtered.map(renderRow));
}
async function loadDetail() {
  cancelDetail(); const version = detailVersion;
  if (!state.interval || !state.meta) return;
  detailController = new AbortController(); state.summary = null;
  $('detail-retry').hidden = true; $('change-list').setAttribute('aria-busy', 'true'); renderChanges();
  try {
    const params = new URLSearchParams({ ocid: state.meta.ocid, ...state.interval });
    const response = await fetch(`/api/analysis/combat-power/detail?${params}`, { signal: detailController.signal });
    if (!response.ok) throw new Error(`변경 내역을 불러오지 못했어요. (HTTP ${response.status})`);
    const data = await response.json();
    if (version !== detailVersion) return;
    if (!data.changeSummary) throw new Error('변경 내역 응답을 읽을 수 없어요.');
    state.summary = data.changeSummary; renderChanges();
  } catch (error) {
    if (version !== detailVersion || error.name === 'AbortError') return;
    renderChanges(error.message || '변경 내역을 불러오지 못했어요.'); $('detail-retry').hidden = false;
  } finally { if (version === detailVersion) $('change-list').setAttribute('aria-busy', 'false'); }
}
function startHistory(name, range = state.range) {
  if (!name.trim()) return;
  stream?.close(); const version = ++historyVersion;
  state.name = name.trim(); state.range = range; state.meta = null; state.points = []; state.status = 'loading'; state.received = 0;
  resetDetail(); setAvatar(); renderProfile(); renderDates(); renderChart();
  $('welcome').hidden = true; $('dashboard').hidden = false; $('history-retry').hidden = true;
  $('character-input').value = state.name;
  for (const button of document.querySelectorAll('[data-range]')) button.setAttribute('aria-pressed', button.dataset.range === range);
  showHistoryNotice('캐릭터를 확인하고 성장 기록을 불러오는 중이에요.');
  setText('chart-progress', '조회 준비 중'); setText('selection-hint', '조회가 완료되면 두 시점을 비교할 수 있어요.');
  document.title = `${state.name} · MapleDelta`;
  const url = new URL(location.href); url.searchParams.set('characterName', state.name); url.searchParams.set('range', range); history.replaceState(null, '', url);
  const source = new EventSource(`/api/analysis/combat-power/history/stream?${new URLSearchParams({ characterName: state.name, range })}`); stream = source;
  let terminal = false;
  const finishError = message => {
    if (version !== historyVersion || terminal) return;
    terminal = true; source.close(); state.status = 'error';
    showHistoryNotice(message); $('history-retry').hidden = false; setText('chart-progress', '조회 중단');
    setText('selection-hint', '조회가 중단됐어요. 다시 시도해 주세요.'); renderProfile(); renderDates(); renderChart();
  };
  const listen = (type, handler) => source.addEventListener(type, event => {
    if (version !== historyVersion || terminal) return;
    try { handler(JSON.parse(event.data)); } catch { finishError('서버 응답을 읽지 못했어요. 다시 시도해 주세요.'); }
  });
  listen('meta', meta => {
    state.meta = meta;
    state.points = [...meta.dates].sort().map(date => ({ date, combatPower: null, apiCombatPower: null, level: null, received: false }));
    setAvatar(); renderProfile(); renderDates(); renderChart();
    setText('chart-progress', `0 / ${meta.plannedCount}`);
  });
  listen('point', data => {
    const point = { ...data.point, received: true };
    if (state.points.some(p => p.date === point.date)) state.points = state.points.map(p => p.date === point.date ? point : p);
    else state.points = [...state.points, point].sort((a, b) => a.date.localeCompare(b.date));
    state.received = data.index;
    setText('chart-progress', `${data.index} / ${data.total}`);
    showHistoryNotice(`성장 기록을 불러오고 있어요. ${data.index} / ${data.total} 지점`);
    renderProfile(); renderChart();
  });
  listen('done', data => {
    source.close(); terminal = true; state.status = 'done';
    // Server can stop before plannedCount when it reaches unavailable historical data.
    state.points = state.points.filter(point => point.received);
    const messages = [];
    if (data.truncated || state.meta?.truncated || data.loadedCount < (state.meta?.plannedCount ?? 0)) messages.push('조회 가능한 기간의 기록만 표시했어요.');
    const missing = state.points.filter(point => !isNumber(point.combatPower)).length;
    if (missing) messages.push(`${missing}개 지점의 계산 전투력이 없어 해당 구간의 선을 끊어 표시했어요.`);
    if (!state.points.length) messages.push('조회할 수 있는 기록이 없어요.');
    showHistoryNotice(messages.join(' ')); setText('chart-progress', `${data.loadedCount}개 지점 · 조회 완료`);
    renderProfile(); renderDates(); renderChart();
    const interval = defaultInterval(state.points);
    if (interval) selectInterval(interval);
    else setText('selection-hint', '비교하려면 전투력 값이 있는 날짜가 두 개 이상 필요해요.');
  });
  source.addEventListener('error', event => {
    if (typeof event.data === 'string') {
      try { const data = JSON.parse(event.data); finishError(data.code === 'NOT_FOUND' ? '캐릭터를 찾지 못했어요. 닉네임을 확인해 주세요.' : data.message || '조회 중 오류가 발생했어요.'); }
      catch { finishError('서버 오류 응답을 읽지 못했어요.'); }
    } else finishError('서버와의 연결이 끊어졌어요. 다시 시도해 주세요.');
  });
}
$('search-form').addEventListener('submit', event => { event.preventDefault(); startHistory($('character-input').value); });
$('start-search').addEventListener('click', () => $('character-input').focus());
$('history-retry').addEventListener('click', () => startHistory(state.name));
$('detail-retry').addEventListener('click', loadDetail);
for (const button of document.querySelectorAll('[data-range]')) button.addEventListener('click', () => { if (button.dataset.range !== state.range) startHistory(state.name, button.dataset.range); });
$('compare-mode').addEventListener('change', () => { state.anchor = null; renderChart(); setText('selection-hint', $('compare-mode').checked ? '그래프에서 비교할 두 날짜를 차례로 선택하세요.' : '날짜를 선택하면 직전 기록과 비교합니다.'); });
$('compare-dates').addEventListener('click', () => {
  const interval = orderedInterval($('previous-date').value, $('current-date').value);
  if (!interval) setText('selection-hint', '서로 다른 두 날짜를 선택해 주세요.'); else selectInterval(interval);
});
window.addEventListener('pagehide', () => { stream?.close(); cancelDetail(); });
let chartWidth = 0;
new ResizeObserver(([entry]) => {
  if (entry.contentRect.width > 0 && Math.abs(entry.contentRect.width - chartWidth) > 1) {
    chartWidth = entry.contentRect.width; renderChart();
  }
}).observe($('chart'));
renderChanges();
const params = new URLSearchParams(location.search);
if (params.get('characterName')?.trim()) startHistory(params.get('characterName'), params.get('range') === 'monthly' ? 'monthly' : 'daily');
