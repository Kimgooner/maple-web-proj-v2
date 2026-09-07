import type { HistoryPoint, HistoryRange } from '../api/types';
import { formatAxisDate, formatCompact, formatLongDate, formatNumber } from '../lib/format';
import { isPending } from '../lib/history';
import { changedDates, type Interval } from '../lib/selection';

export type SelectMode = 'pin' | 'range';

interface Props {
  points: HistoryPoint[];
  range: HistoryRange;
  showApi: boolean;
  mode: SelectMode;
  interval: Interval | null;
  anchor: string | null;
  /** 구간 모드에서 점 하나를 고를 때 */
  onPick: (date: string) => void;
  /** 변화 지점 모드에서 핀을 누를 때 */
  onPin: (date: string) => void;
}

const W = 1000;
const H = 280;
const PL = 64;
const PR = 20;
const PT = 20;
const PB = 40;

export function TrendChart({ points, range, showApi, mode, interval, anchor, onPick, onPin }: Props) {
  const n = points.length;
  const x = (i: number) => (n <= 1 ? (PL + W - PR) / 2 : PL + ((W - PL - PR) * i) / (n - 1));

  const values = points.flatMap((p) => [p.combatPower, showApi ? p.apiCombatPower : null]).filter((v): v is number => v != null);
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;
  const span = Math.max(rawMax - rawMin, Math.max(rawMax, 1) * 0.02);
  const min = rawMin - span * 0.15;
  const max = rawMax + span * 0.15;
  const y = (v: number) => PT + ((H - PT - PB) * (1 - (v - min) / (max - min)));

  const segments = (pick: (p: HistoryPoint) => number | null): string => {
    let d = '';
    let open = false;
    points.forEach((p, i) => {
      const v = pick(p);
      if (v == null) { open = false; return; }
      d += `${open ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)} `;
      open = true;
    });
    return d.trim();
  };

  const pins = new Set(changedDates(points));
  const index = new Map(points.map((p, i) => [p.date, i]));
  const ticks = 4;
  const gridValues = Array.from({ length: ticks + 1 }, (_, k) => min + ((max - min) * k) / ticks);
  const labelEvery = Math.max(1, Math.ceil(n / 10));
  const selPrev = interval ? index.get(interval.previousDate) : undefined;
  const selCur = interval ? index.get(interval.currentDate) : undefined;
  const half = n > 1 ? (x(1) - x(0)) / 2 : (W - PL - PR) / 2;

  return (
    <div className="chart-wrap">
      <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="전투력 추이">
        {gridValues.map((v) => (
          <g key={v}>
            <line x1={PL} x2={W - PR} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
            <text x={PL - 10} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)" fontFamily="var(--mono)">{formatCompact(Math.round(v))}</text>
          </g>
        ))}
        {selPrev != null && selCur != null && (
          <rect x={x(selPrev)} y={PT} width={x(selCur) - x(selPrev)} height={H - PT - PB} fill="var(--accent)" fillOpacity="0.10" />
        )}
        {showApi && <path d={segments((p) => p.apiCombatPower)} fill="none" stroke="var(--api)" strokeWidth="1.5" strokeDasharray="4 4" />}
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
                  {pending ? ' · 불러오는 중' : p.combatPower == null ? ' · 계산 실패 (02시 이후 다시 시도)' : ` · 계산 ${formatNumber(p.combatPower)}`}
                  {p.apiCombatPower != null ? ` · 넥슨 ${formatNumber(p.apiCombatPower)}` : ''}
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
