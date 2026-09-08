import type { HistoryPoint, HistoryRange } from '../api/types';
import { formatAxisDate, formatCompact, formatLongDate, formatNumber } from '../lib/format';
import { changedDates } from '../lib/selection';
import { isPending } from '../lib/history';

export type SelectMode = 'pin' | 'range';

type Props = {
  points: HistoryPoint[];
  range: HistoryRange;
  showFragments: boolean;
  mode: SelectMode;
  interval: { previousDate: string; currentDate: string } | null;
  anchor: string | null;
  /** 구간 모드에서 점 하나를 고를 때 */
  onPick: (date: string) => void;
  /** 변화 지점 모드에서 핀을 누를 때 */
  onPin: (date: string) => void;
};

const W = 1000;
const H = 280;
const PL = 64;
const PR = 64;
const PT = 20;
const PB = 40;
const TICKS = 4;

/** 값 → y 좌표. 눈금 값도 같이 들고 있어 좌우 축이 같은 높이에 찍힌다. */
type Scale = ((v: number) => number) & { ticks: number[] };

/** 값이 하나뿐이거나 모두 같아도 선이 납작해지지 않게 최소 폭을 준다. */
function scale(values: number[]): Scale {
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;
  const span = Math.max(rawMax - rawMin, Math.max(rawMax, 1) * 0.02);
  const min = rawMin - span * 0.15;
  const max = rawMax + span * 0.15;
  const at = ((v: number) => PT + (H - PT - PB) * (1 - (v - min) / (max - min))) as Scale;
  at.ticks = Array.from({ length: TICKS + 1 }, (_, k) => min + ((max - min) * k) / TICKS);
  return at;
}

export function TrendChart({ points, range, showFragments, mode, interval, anchor, onPick, onPin }: Props) {
  const n = points.length;
  const x = (i: number) => (n <= 1 ? (PL + W - PR) / 2 : PL + ((W - PL - PR) * i) / (n - 1));

  const y = scale(points.map((p) => p.combatPower).filter((v): v is number => v != null));
  const fy = scale(points.map((p) => p.solErdaFragments).filter((v): v is number => v != null));

  const segments = (pick: (p: HistoryPoint) => number | null): string => {
    let d = '';
    let open = false;
    points.forEach((p, i) => {
      const v = pick(p);
      if (v == null) { open = false; return; }
      d += `${open ? 'L' : 'M'}${x(i)},${y(v)} `;
      open = true;
    });
    return d.trim();
  };

  /**
   * 조각은 누적이라 줄지 않는다. 선만 그으면 전투력과 겹쳐 읽기 어려워서 아래를 옅게 깐다.
   * 다만 오른쪽 축은 0부터 시작하지 않으므로 바닥까지 균일하게 채우면 "0부터 쌓인 양"으로
   * 잘못 읽힌다. 아래로 갈수록 사라지는 그라디언트라야 면적이 아니라 강조로 보인다.
   * 값이 빈 구간에서 끊어야 하므로 이어진 덩어리마다 따로 만든다.
   */
  const fragmentRuns = (): { line: string; area: string }[] => {
    const runs: { i: number; v: number }[][] = [];
    let run: { i: number; v: number }[] = [];
    points.forEach((p, i) => {
      if (p.solErdaFragments == null) { if (run.length) runs.push(run); run = []; return; }
      run.push({ i, v: p.solErdaFragments });
    });
    if (run.length) runs.push(run);
    return runs.map((r) => {
      const line = r.map((q, k) => `${k ? 'L' : 'M'}${x(q.i)},${fy(q.v)}`).join(' ');
      return { line, area: `${line} L${x(r[r.length - 1].i)},${H - PB} L${x(r[0].i)},${H - PB} Z` };
    });
  };

  const pins = new Set(changedDates(points));
  const index = new Map(points.map((p, i) => [p.date, i]));
  const labelEvery = Math.max(1, Math.ceil(n / 10));
  const selPrev = interval ? index.get(interval.previousDate) : undefined;
  const selCur = interval ? index.get(interval.currentDate) : undefined;
  const half = n > 1 ? (x(1) - x(0)) / 2 : (W - PL - PR) / 2;
  const fragments = showFragments ? fragmentRuns() : [];

  return (
    <div className="chart-wrap">
      <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="전투력 추이">
        <defs>
          <linearGradient id="frag-fade" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--frag)" stopOpacity="0.22" />
            <stop offset="100%" stopColor="var(--frag)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {y.ticks.map((v, k) => (
          <g key={v}>
            <line x1={PL} x2={W - PR} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
            <text x={PL - 10} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)" fontFamily="var(--mono)">{formatCompact(Math.round(v))}</text>
            {showFragments && (
              <text x={W - PR + 10} y={y(v) + 4} textAnchor="start" fontSize="11" fill="var(--frag)" fontFamily="var(--mono)">{formatCompact(Math.round(fy.ticks[k]))}</text>
            )}
          </g>
        ))}
        {selPrev != null && selCur != null && (
          <rect x={x(selPrev)} y={PT} width={x(selCur) - x(selPrev)} height={H - PT - PB} fill="var(--accent)" fillOpacity="0.10" />
        )}
        {fragments.map((f, k) => (
          <path key={`frag-area-${k}`} d={f.area} fill="url(#frag-fade)" stroke="none" />
        ))}
        {fragments.map((f, k) => (
          <path key={`frag-line-${k}`} d={f.line} fill="none" stroke="var(--frag)" strokeWidth="1.5" />
        ))}
        <path d={segments((p) => p.combatPower)} fill="none" stroke="var(--line)" strokeWidth="2" />

        {points.map((p, i) => {
          const pending = isPending(p);
          const selected = i === selPrev || i === selCur;
          const isAnchor = p.date === anchor;
          const cy = p.combatPower != null ? y(p.combatPower) : H - PB;
          return (
            <g key={p.date}>
              {pending && <circle cx={x(i)} cy={H - PB} r="2.5" fill="var(--border)" />}
              {!pending && p.combatPower == null && <circle cx={x(i)} cy={H - PB} r="3.5" fill="none" stroke="var(--muted)" strokeWidth="1.5" />}
              {pins.has(p.date) && !selected && (
                <circle cx={x(i)} cy={cy} r="4.5" fill="var(--bg)" stroke="var(--accent)" strokeWidth="2" style={{ cursor: 'pointer' }} onClick={() => onPin(p.date)} />
              )}
              {selected && <circle cx={x(i)} cy={cy} r="6" fill="var(--accent)" stroke="var(--bg)" strokeWidth="2" />}
              {mode === 'range' && isAnchor && <circle cx={x(i)} cy={cy} r="8" fill="none" stroke="var(--accent)" strokeWidth="1.5" strokeDasharray="3 2" />}
              {i % labelEvery === labelEvery - 1 && (
                <text x={x(i)} y={H - 12} textAnchor="middle" fontSize="11" fill="var(--muted)" fontFamily="var(--mono)">{formatAxisDate(p.date, range)}</text>
              )}
              <rect
                className="hit"
                x={x(i) - half}
                y={PT}
                width={half * 2}
                height={H - PT - PB}
                style={{ cursor: pending ? 'default' : mode === 'range' || pins.has(p.date) ? 'pointer' : 'default' }}
                onClick={() => {
                  if (pending) return;
                  if (mode === 'range') onPick(p.date);
                  else if (pins.has(p.date)) onPin(p.date);
                }}
              >
                <title>
                  {formatLongDate(p.date)}
                  {pending ? ' · 불러오는 중' : p.combatPower == null ? ' · 계산 실패 (02시 이후 다시 시도)' : ` · 전투력 ${formatNumber(p.combatPower)}`}
                  {p.solErdaFragments != null ? ` · 조각 ${formatNumber(p.solErdaFragments)}` : ''}
                  {p.level != null ? ` · Lv.${p.level}` : ''}
                </title>
              </rect>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
