import type { HistoryPoint, HistoryRange } from '../api/types';
import { compact, dateLabel, formatNumber, shortDate } from '../lib/format';
import { isPending } from '../lib/history';

type Props = {
  points: HistoryPoint[];
  range: HistoryRange;
  showFragments: boolean;
  interval: { previousDate: string; currentDate: string } | null;
  anchor: string | null;
  /** 지점을 고를 수 있는 상태인가. 아직 받는 중이면 못 고른다 */
  interactive: boolean;
  onPick: (date: string) => void;
};

const WIDTH = 980;
const HEIGHT = 280;
const LEFT = 65;
const RIGHT = 66;
const TOP = 24;
const BOTTOM = 34;
const TICKS = 4;

type Scale = ((value: number) => number) & { ticks: number[] };

/** 값이 하나뿐이거나 모두 같아도 선이 납작해지지 않게 최소 폭을 준다. */
function scale(values: number[]): Scale {
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;
  const span = Math.max(rawMax - rawMin, Math.max(rawMax, 1) * 0.02);
  const min = rawMin - span * 0.15;
  const max = rawMax + span * 0.15;
  const at = ((value: number) =>
    TOP + (HEIGHT - TOP - BOTTOM) * (1 - (value - min) / (max - min))) as Scale;
  at.ticks = Array.from({ length: TICKS + 1 }, (_, k) => min + ((max - min) * k) / TICKS);
  return at;
}

export function TrendChart({ points, range, showFragments, interval, anchor, interactive, onPick }: Props) {
  const count = points.length;
  const x = (index: number) =>
    count <= 1 ? (LEFT + WIDTH - RIGHT) / 2 : LEFT + ((WIDTH - LEFT - RIGHT) * index) / (count - 1);

  const y = scale(points.map((p) => p.combatPower).filter((v): v is number => v != null));
  const fragValues = points.map((p) => p.solErdaFragments).filter((v): v is number => v != null);
  const fy = scale(fragValues);
  // 조각이 한 번도 안 변한 캐릭터(만렙)는 축을 다섯 칸으로 늘려 봐야 같은 숫자만 반복된다.
  const fragFlat = fragValues.length > 0 && Math.min(...fragValues) === Math.max(...fragValues);

  const line = (pick: (point: HistoryPoint) => number | null, at: Scale): string => {
    let path = '';
    let open = false;
    points.forEach((point, index) => {
      const value = pick(point);
      if (value == null) { open = false; return; }
      path += `${open ? 'L' : 'M'}${x(index)},${at(value)} `;
      open = true;
    });
    return path.trim();
  };

  /**
   * 조각은 누적이라 줄지 않는다. 선만 그으면 전투력과 겹쳐 읽기 어려워 아래를 옅게 깐다.
   * 오른쪽 축이 0부터 시작하지 않으므로 균일하게 채우면 "0부터 쌓인 양"으로 잘못 읽힌다 —
   * 아래로 사라지는 그라디언트라야 면적이 아니라 강조로 보인다.
   */
  const fragmentRuns = (): { line: string; area: string }[] => {
    const runs: { i: number; v: number }[][] = [];
    let run: { i: number; v: number }[] = [];
    points.forEach((point, i) => {
      if (point.solErdaFragments == null) { if (run.length) runs.push(run); run = []; return; }
      run.push({ i, v: point.solErdaFragments });
    });
    if (run.length) runs.push(run);
    return runs.map((r) => {
      const path = r.map((q, k) => `${k ? 'L' : 'M'}${x(q.i)},${fy(q.v)}`).join(' ');
      const floor = HEIGHT - BOTTOM;
      return { line: path, area: `${path} L${x(r[r.length - 1].i)},${floor} L${x(r[0].i)},${floor} Z` };
    });
  };

  const index = new Map(points.map((point, i) => [point.date, i]));
  const start = interval ? index.get(interval.previousDate) : undefined;
  const end = interval ? index.get(interval.currentDate) : undefined;
  const tickCount = Math.min(6, count);
  const labelled = new Set(
    Array.from({ length: tickCount }, (_, i) => Math.round((i * (count - 1)) / Math.max(1, tickCount - 1))));
  const fragments = showFragments ? fragmentRuns() : [];
  const half = count > 1 ? (x(1) - x(0)) / 2 : (WIDTH - LEFT - RIGHT) / 2;

  return (
    <div className="chart">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="group" aria-label="전투력 추이. 날짜 지점을 선택해 두 시점을 비교하세요.">
        <defs>
          <linearGradient id="fragment-fade" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--fragment)" stopOpacity="0.16" />
            <stop offset="100%" stopColor="var(--fragment)" stopOpacity="0" />
          </linearGradient>
        </defs>

        {y.ticks.map((value, k) => (
          <g key={value}>
            <line x1={LEFT} x2={WIDTH - RIGHT} y1={y(value)} y2={y(value)} stroke="#eef0f4" strokeWidth="1" />
            <text x={LEFT - 10} y={y(value) + 4} textAnchor="end">{compact(Math.round(value))}</text>
            {showFragments && !fragFlat && (
              <text className="fragment-axis" x={WIDTH - RIGHT + 10} y={y(value) + 4} textAnchor="start">
                {formatNumber(Math.round(fy.ticks[k]))}
              </text>
            )}
          </g>
        ))}

        {showFragments && fragFlat && (
          <text className="fragment-axis" x={WIDTH - RIGHT + 10} y={fy(fragValues[0]) + 4} textAnchor="start">
            {formatNumber(fragValues[0])}
          </text>
        )}

        {start != null && end != null && (
          <>
            <rect x={x(start)} y={TOP} width={x(end) - x(start)} height={HEIGHT - TOP - BOTTOM} fill="#fff1e7" />
            {[start, end].map((i) => (
              <line key={i} x1={x(i)} x2={x(i)} y1={TOP} y2={HEIGHT - BOTTOM} stroke="#ee925f" strokeDasharray="3 4" />
            ))}
          </>
        )}

        {fragments.map((f, k) => <path key={`fa-${k}`} d={f.area} fill="url(#fragment-fade)" stroke="none" />)}
        {fragments.map((f, k) => (
          <path key={`fl-${k}`} d={f.line} fill="none" stroke="var(--fragment)" strokeWidth="2" strokeLinejoin="round" />
        ))}
        <path d={line((p) => p.combatPower, y)} fill="none" stroke="#ed702e" strokeWidth="2.6" strokeLinejoin="round" />

        {points.map((point, i) => {
          const pending = isPending(point);
          const selected = i === start || i === end || point.date === anchor;
          const cy = point.combatPower != null ? y(point.combatPower) : HEIGHT - BOTTOM;
          return (
            <g className="chart-point" key={point.date} onClick={() => interactive && !pending && onPick(point.date)}>
              <title>
                {dateLabel(point.date)}
                {pending ? ' · 불러오는 중' : point.combatPower == null ? ' · 계산 실패' : ` · 전투력 ${point.combatPower.toLocaleString('ko-KR')}`}
                {point.solErdaFragments != null ? ` · 조각 ${point.solErdaFragments.toLocaleString('ko-KR')}` : ''}
              </title>
              {point.combatPower != null && (
                <circle
                  cx={x(i)} cy={cy} r={selected ? 5 : 2.6}
                  fill={selected ? '#ed702e' : '#fff'} stroke="#ed702e" strokeWidth="1.5"
                />
              )}
              {pending && <circle cx={x(i)} cy={HEIGHT - BOTTOM} r="2.5" fill="#dfe3e9" />}
              {labelled.has(i) && (
                <text x={x(i)} y={HEIGHT - 10} textAnchor="middle">
                  {range === 'monthly' ? point.date.slice(0, 7).replace('-', '.') : shortDate(point.date)}
                </text>
              )}
              <rect x={x(i) - half} y={TOP} width={half * 2} height={HEIGHT - TOP - BOTTOM} fill="transparent" />
            </g>
          );
        })}
      </svg>
    </div>
  );
}
