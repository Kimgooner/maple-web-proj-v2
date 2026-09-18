import type { CharacterInfo, CombatPowerBreakdown, StatSheetSummary } from '../api/types';
import { formatGameNumber, formatNumber } from '../lib/format';
import { sourceLabel } from '../lib/labels';

/** 소수가 있으면 한 자리까지. 데몬어벤져 주스탯처럼 환산값이 소수인 것만 해당된다. */
function stat(value: number): string {
  return Number.isInteger(value) ? formatNumber(value) : formatNumber(Math.round(value * 10) / 10);
}

function percent(value: number): string {
  return `${formatNumber(Math.round(value * 100) / 100)}%`;
}

function signed(value: number): string {
  return `${value >= 0 ? '+' : ''}${percent(value)}`;
}

/** 시트의 한 칸. 이름·값 뽑기·% 여부. */
type Column = { key: string; label: string; pick: (s: StatSheetSummary) => number; percent?: boolean };

const STAT_FIELD: Record<string, { flat: keyof StatSheetSummary; pct: keyof StatSheetSummary; fixed: keyof StatSheetSummary }> = {
  STR: { flat: 'str', pct: 'strPercent', fixed: 'strNoPercent' },
  DEX: { flat: 'dex', pct: 'dexPercent', fixed: 'dexNoPercent' },
  INT: { flat: 'intStat', pct: 'intPercent', fixed: 'intNoPercent' },
  LUK: { flat: 'luk', pct: 'lukPercent', fixed: 'lukNoPercent' },
  HP: { flat: 'hp', pct: 'hpPercent', fixed: 'hpNoPercent' },
};

/**
 * 이 직업에서 전투력에 걸리는 칸만 고른다.
 *
 * <p>주스탯은 고정·%·%미적용(심볼·헥사) 셋, 부스탯은 고정, 그 뒤 올스탯·공격력(마력)·데미지·보공·크뎀·최뎀.
 * 종합이 0 인 칸은 빼서 표가 옆으로 길어지지 않게 한다 — 주스탯과 공격력은 0 이어도 남긴다.
 */
function columnsFor(info: CharacterInfo | null, total: StatSheetSummary): Column[] {
  const mains = info?.mainStats ?? ['STR'];
  const subs = (info?.subStats ?? []).filter((s) => !mains.includes(s));
  const usesMagic = info?.usesMagic ?? false;
  const all: (Column & { keep?: boolean })[] = [];
  for (const m of mains) {
    const f = STAT_FIELD[m];
    if (!f) continue;
    all.push({ key: `${m}`, label: m, pick: (s) => s[f.flat] as number, keep: true });
    all.push({ key: `${m}%`, label: `${m} %`, pick: (s) => s[f.pct] as number, percent: true });
    all.push({ key: `${m}fixed`, label: `${m} 고정`, pick: (s) => s[f.fixed] as number });
  }
  for (const sub of subs) {
    const f = STAT_FIELD[sub];
    if (!f) continue;
    all.push({ key: sub, label: sub, pick: (s) => s[f.flat] as number });
    all.push({ key: `${sub}%`, label: `${sub} %`, pick: (s) => s[f.pct] as number, percent: true });
  }
  all.push({ key: 'all', label: '올스탯', pick: (s) => s.allStat });
  all.push({ key: 'all%', label: '올스탯 %', pick: (s) => s.allStatPercent, percent: true });
  all.push({ key: 'allfixed', label: '올스탯 고정', pick: (s) => s.allStatNoPercent });
  all.push({ key: 'power', label: usesMagic ? '마력' : '공격력', pick: (s) => (usesMagic ? s.magicPower : s.attackPower), keep: true });
  all.push({ key: 'power%', label: usesMagic ? '마력 %' : '공격력 %', pick: (s) => (usesMagic ? s.magicPowerPercent : s.attackPowerPercent), percent: true, keep: true });
  all.push({ key: 'dmg', label: '데미지', pick: (s) => s.damage, percent: true });
  all.push({ key: 'boss', label: '보공', pick: (s) => s.bossDamage, percent: true });
  all.push({ key: 'crit', label: '크뎀', pick: (s) => s.criticalDamage, percent: true });
  all.push({ key: 'final', label: '최종뎀', pick: (s) => s.finalDamage, percent: true });
  return all.filter((c) => c.keep || c.pick(total) !== 0);
}

/**
 * 표의 줄 차례. 비율 순으로 늘어놓으면 캐릭터마다 자리가 바뀌어 눈이 매번 다시 찾는다.
 * 게임에서 손대는 차례(장비 → 펫·캐시 → 세트 → 심볼·헥사 → 스킬 → AP → 유니온 → 하이퍼·어빌 → 기타)로
 * 고정하고, 유니온 넷은 한 줄로 묶는다. 여기 없는 소스(컨버전·성향·소모품)는 값이 있을 때만 뒤에 붙는다.
 */
const ROWS: { label: string; sources: string[] }[] = [
  { label: '장비', sources: ['items'] },
  { label: '펫 장비', sources: ['pet'] },
  { label: '캐시 장비', sources: ['cash'] },
  { label: '세트 효과', sources: ['setEffect'] },
  { label: '심볼', sources: ['symbol'] },
  { label: '헥사스탯', sources: ['hexaStat'] },
  { label: '스킬(0차)', sources: ['skill'] },
  { label: 'AP 분배', sources: ['abilityPoint'] },
  { label: '유니온', sources: ['unionRaider', 'unionOccupied', 'unionArtifact', 'unionChampion'] },
  { label: '하이퍼스탯', sources: ['hyperStat'] },
  { label: '어빌리티', sources: ['ability'] },
  { label: '기타', sources: ['otherStat'] },
];

type Row = { label: string; stats: StatSheetSummary; sharePercent: number };

/** 소스 여럿을 한 줄로. 스탯은 필드마다 더하고 비율도 더한다. */
function sumStats(list: StatSheetSummary[]): StatSheetSummary {
  const out = { ...list[0] } as Record<string, number>;
  for (const s of list.slice(1)) {
    for (const [key, value] of Object.entries(s)) out[key] = (out[key] ?? 0) + (value as number);
  }
  return out as unknown as StatSheetSummary;
}

function rowsOf(sources: { source: string; stats: StatSheetSummary; sharePercent: number }[]): Row[] {
  const bySource = new Map(sources.map((s) => [s.source, s]));
  const rows: Row[] = [];
  const used = new Set<string>();
  for (const row of ROWS) {
    const present = row.sources.filter((key) => bySource.has(key));
    if (present.length === 0) continue;
    present.forEach((key) => used.add(key));
    const parts = present.map((key) => bySource.get(key)!);
    rows.push({
      label: row.label,
      stats: sumStats(parts.map((p) => p.stats)),
      sharePercent: parts.reduce((sum, p) => sum + p.sharePercent, 0),
    });
  }
  for (const s of sources) {
    if (!used.has(s.source)) rows.push({ label: sourceLabel(s.source), stats: s.stats, sharePercent: s.sharePercent });
  }
  return rows;
}

/**
 * 주스탯·부스탯 한 줄씩: (스탯 + 올스탯) × (100 + 스탯% + 올스탯%) ÷ 100 + 고정. 계산이 실제로 하는
 * 식({@code calculateStat})을 총합 시트의 수로 적은 것이다. 데몬어벤져의 HP 환산이나 제논의
 * 세 스탯 합처럼 식이 다른 직업은 이 줄을 적지 않는다 — 틀린 식을 적느니 안 적는다.
 */
function statNotes(info: CharacterInfo | null, total: StatSheetSummary | null): string[] {
  if (!info || !total) return [];
  const mains = info.mainStats.filter((m) => m !== 'HP');
  if (mains.length !== 1 || info.mainStats.includes('HP')) return [];
  const perLevel: Record<string, keyof StatSheetSummary> = {
    STR: 'strPerLevel9', DEX: 'dexPerLevel9', INT: 'intPerLevel9', LUK: 'lukPerLevel9',
  };
  const line = (name: string, title: string) => {
    const f = STAT_FIELD[name];
    if (!f) return null;
    // 9레벨마다 붙는 몫(AP 자동 분배). 0 이면 적지 않는다.
    const byLevel = Math.floor((info.level ?? 0) / 9) * (total[perLevel[name]] as number);
    const pct = (total[f.pct] as number) + total.allStatPercent;
    const fixed = (total[f.fixed] as number) + total.allStatNoPercent;
    const flat = `${name} ${formatNumber(total[f.flat] as number)} + 올스탯 ${formatNumber(total.allStat)}`
      + (byLevel ? ` + 레벨 ${formatNumber(byLevel)}` : '');
    return `${title} = (${flat}) × (100 + ${percent(pct)}) ÷ 100 + 고정 ${formatNumber(fixed)}`;
  };
  const notes: string[] = [];
  const main = line(mains[0], '주스탯');
  if (main) notes.push(main);
  for (const sub of info.subStats.filter((s) => !mains.includes(s))) {
    const note = line(sub, info.subStats.length > 1 ? `부스탯 ${sub}` : '부스탯');
    if (note) notes.push(note);
  }
  return notes;
}

function Cell({ value, isPercent }: { value: number; isPercent?: boolean }) {
  if (value === 0) return <td className="zero">·</td>;
  return <td>{isPercent ? percent(value) : stat(value)}</td>;
}

/**
 * 전투력이 어떻게 나왔는지. 세 단계로 적는다.
 *
 * <ol>
 * <li>요소별 스탯 합 — 장비·세트·스킬… 각 소스가 종합에 더한 스탯을 정해진 차례로 한 줄씩. 맨 오른쪽은
 *     전투력 구성 비율(섀플리 값)이라 다 더하면 100% 다.</li>
 * <li>총합 — 위를 전부 더한 종합. 표의 마지막 줄.</li>
 * <li>계산 — 총합에서 나온 항을 곱해 전투력이 되는 식. 항마다 무엇으로 이루어졌는지를 같이 적는다.</li>
 * </ol>
 */
export function CombatPowerFormula({ breakdown, info }: { breakdown: CombatPowerBreakdown; info: CharacterInfo | null }) {
  const rows = rowsOf(breakdown.sources ?? []);
  const total = breakdown.total;
  const columns = total ? columnsFor(info, total) : [];
  const maxShare = Math.max(1, ...rows.map((r) => r.sharePercent));

  const powerName = breakdown.usesMagic ? '마력' : '공격력';
  const terms: { label: string; value: string; notes: string[] }[] = [
    {
      label: '스탯',
      value: stat(breakdown.statTerm),
      notes: [
        ...statNotes(info, total),
        `(주스탯 ${stat(breakdown.mainStat)} × 4${breakdown.subStat ? ` + 부스탯 ${stat(breakdown.subStat)}` : ''}) ÷ 100`,
      ],
    },
    {
      label: powerName,
      value: formatNumber(breakdown.power),
      notes: total
        ? [`${powerName} ${formatNumber(breakdown.usesMagic ? total.magicPower : total.attackPower)} × (100 + ${percent(breakdown.usesMagic ? total.magicPowerPercent : total.attackPowerPercent)}) ÷ 100`]
        : [],
    },
    {
      label: '데미지',
      value: percent(100 + breakdown.damage + breakdown.bossDamage),
      notes: [`100 + 데미지 ${signed(breakdown.damage)} + 보공 ${signed(breakdown.bossDamage)}`],
    },
    {
      label: '크리티컬 데미지',
      value: percent(135 + breakdown.criticalDamage),
      notes: [`135 + 크뎀 ${signed(breakdown.criticalDamage)}`],
    },
    {
      label: '최종 데미지',
      value: percent(100 + breakdown.finalDamage),
      notes: [`100 + 최종 데미지 ${signed(breakdown.finalDamage)}`],
    },
  ];
  if (breakdown.correction != null) {
    terms.push({
      label: '직업 보정',
      value: `× ${formatNumber(Math.round(breakdown.correction * 1e6) / 1e6)}`,
      notes: ['스탯 계산이 다른 직업의 보정 상수'],
    });
  }

  return (
    <div className="formula" aria-label="전투력 계산 과정">
      {total && rows.length > 0 && (
        <section className="formula-step">
          <h3>요소별 스탯 합</h3>
          <div className="formula-table-wrap">
            <table className="formula-table">
              <thead>
                <tr>
                  <th scope="col">요소</th>
                  {columns.map((c) => <th scope="col" key={c.key}>{c.label}</th>)}
                  <th scope="col" className="share" title="전투력 구성 비율. 요소를 하나씩 더해 갈 때 늘어난 몫을 모든 순서에 대해 평균한 값(섀플리)이라 다 더하면 100%">비율</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.label}>
                    <th scope="row">{row.label}</th>
                    {columns.map((c) => <Cell key={c.key} value={c.pick(row.stats)} isPercent={c.percent} />)}
                    <td className="share">
                      <span className="share-bar" style={{ width: `${(Math.max(0, row.sharePercent) / maxShare) * 100}%` }} />
                      <span className="share-value">{percent(Math.round(row.sharePercent * 10) / 10)}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">총합</th>
                  {columns.map((c) => <Cell key={c.key} value={c.pick(total)} isPercent={c.percent} />)}
                  <td className="share"><span className="share-value">100%</span></td>
                </tr>
              </tfoot>
            </table>
          </div>
          <ul className="formula-foot muted">
            <li>전투력에 영향을 주는 스탯만 포함</li>
            <li>비율은 섀플리 값(Shapley Value)을 통해 계산</li>
            <li>고정 = 스탯 % 미반영 스탯</li>
          </ul>
        </section>
      )}

      <section className="formula-step">
        <h3>계산</h3>
        <div className="formula-terms">
          {terms.map((term, index) => (
            <div className="formula-term" key={term.label}>
              {index > 0 && <span className="formula-op" aria-hidden="true">×</span>}
              <div className="formula-card">
                <div className="formula-label">{term.label}</div>
                <div className="formula-value">{term.value}</div>
                {term.notes.map((note, i) => <div className="formula-note" key={i}>{note}</div>)}
              </div>
            </div>
          ))}
          <div className="formula-term">
            <span className="formula-op" aria-hidden="true">÷ 1,000,000 =</span>
            <div className="formula-card result">
              <div className="formula-label">전투력</div>
              <div className="formula-value">{formatGameNumber(breakdown.combatPower)}</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
