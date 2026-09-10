import { useState } from 'react';
import type { LevelBandWeek } from '../api/levelBands';
import type { HistoryPoint, HistoryRange } from '../api/types';
import { compact, dateLabel, formatGameNumber, formatNumber, shortDate } from '../lib/format';
import { isPending } from '../lib/history';
import { bandAt } from '../lib/levelBands';

type Props = {
  points: HistoryPoint[];
  range: HistoryRange;
  showFragments: boolean;
  /** 같은 레벨대 기준선을 그릴 주간 분포. 아직 안 쌓였으면 빈 배열이다 */
  bands: LevelBandWeek[];
  showMedian: boolean;
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

/** 0 을 바닥에, 완성에 필요한 총량을 천장에 두는 축. 눈금은 백분율이라 값을 담지 않는다. */
function progressScale(required: number): Scale {
  const at = ((value: number) =>
    HEIGHT - BOTTOM - (HEIGHT - TOP - BOTTOM) * Math.min(Math.max(value, 0) / required, 1)) as Scale;
  at.ticks = [];
  return at;
}

/**
 * 값이 하나뿐이거나 모두 같아도 선이 납작해지지 않게 최소 폭을 준다.
 *
 * <p>아래쪽 여백이 0 밑으로 내려가지 않게 자른다. 전투력도 조각도 음수가 없는데,
 * 값이 작으면 여백이 부호를 넘어 "-2억" 같은 눈금이 찍혔다.
 */
function scale(values: number[]): Scale {
  const rawMin = values.length ? Math.min(...values) : 0;
  const rawMax = values.length ? Math.max(...values) : 1;
  const span = Math.max(rawMax - rawMin, Math.max(rawMax, 1) * 0.02);
  const min = Math.max(0, rawMin - span * 0.15);
  const max = rawMax + span * 0.15;
  const at = ((value: number) =>
    TOP + (HEIGHT - TOP - BOTTOM) * (1 - (value - min) / (max - min))) as Scale;
  at.ticks = Array.from({ length: TICKS + 1 }, (_, k) => min + ((max - min) * k) / TICKS);
  return at;
}

export function TrendChart(
  { points, range, showFragments, bands, showMedian, interval, anchor, interactive, onPick }: Props,
) {
  const [hovered, setHovered] = useState<number | null>(null);
  const count = points.length;
  const x = (index: number) =>
    count <= 1 ? (LEFT + WIDTH - RIGHT) / 2 : LEFT + ((WIDTH - LEFT - RIGHT) * index) / (count - 1);

  /**
   * 지점마다 그 시점·그 레벨의 구간 통계. 260 미만이거나 아직 표본이 없는 주면 null 이고,
   * 그 구간에서는 기준선이 끊긴다 — 전투력 선이 캐릭터 생성 전에 끊기는 것과 같은 규칙이다.
   */
  const bandOf = points.map((point) => bandAt(bands, point.date, point.level));
  const medians = showMedian
    ? bandOf.map((band) => band?.stats.median ?? null).filter((v): v is number => v != null)
    : [];

  /**
   * 축은 전투력과 기준선을 함께 담는다. 기준선만 화면 밖으로 나가면 "내가 중앙값 위인지
   * 아래인지"라는 이 선의 유일한 쓸모가 사라진다. 대신 캐릭터가 중앙값에서 많이 떨어져
   * 있으면 자기 선이 눌리는데, 그건 legend 에서 기준선을 끄면 된다.
   */
  const y = scale([
    ...points.map((p) => p.combatPower).filter((v): v is number => v != null),
    ...medians,
  ]);
  const fragValues = points.map((p) => p.solErdaFragments).filter((v): v is number => v != null);
  /**
   * 조각 축은 기간에 따라 다르게 잰다.
   *
   * <p>12개월은 완성도로 본다 — 맨 아래 0, 맨 위가 "가진 코어를 모두 만렙까지 올리는 데
   * 드는 조각"이다. 그 정도 기간이면 실제로 눈에 보이게 오른다.
   *
   * <p>30일은 데이터 범위로 확대한다. 한 달에 오르는 양은 완성도로 치면 1~2%뿐이라
   * 0~100% 축에 얹으면 선이 바닥에 붙어 아무것도 안 보인다. 그 대신 축 눈금을
   * 개수로 적어, 늘어난 폭이 과장돼 보이지 않게 숫자로 확인할 수 있게 한다.
   *
   * <p>분모는 가장 마지막에 아는 값을 쓴다 — 코어를 새로 열면 분모가 늘어나므로,
   * 지금 기준으로 그려야 축이 구간마다 흔들리지 않는다.
   */
  const required = range === 'monthly'
    ? [...points].reverse()
        .map((p) => p.solErdaFragmentsRequired)
        .find((v): v is number => v != null && v > 0) ?? null
    : null;
  const fy = required != null ? progressScale(required) : scale(fragValues);
  // 조각이 한 번도 안 변한 캐릭터(만렙)는 축을 다섯 칸으로 늘려 봐야 같은 숫자만 반복된다.
  const fragFlat = required == null && fragValues.length > 0
    && Math.min(...fragValues) === Math.max(...fragValues);

  /** 지점이 아니라 인덱스로 값을 집는 선. 기준선은 값이 지점 안이 아니라 구간 통계에 있다. */
  const lineAt = (pick: (index: number) => number | null, at: Scale): string => {
    let path = '';
    let open = false;
    points.forEach((_, index) => {
      const value = pick(index);
      if (value == null) { open = false; return; }
      path += `${open ? 'L' : 'M'}${x(index)},${at(value)} `;
      open = true;
    });
    return path.trim();
  };

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
   * 12개월은 축이 0부터라 이 면적이 실제로 "0부터 쌓인 양"이 맞고, 30일은 축이 범위로
   * 확대돼 있어 면적으로 읽으면 안 된다 — 어느 쪽이든 아래로 사라지는 그라디언트라야
   * 면적이 아니라 강조로 보인다.
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

  /**
   * 말풍선은 SVG 밖에 HTML 로 띄운다 — 표를 그리기엔 foreignObject 보다 이쪽이 낫다.
   * 자리는 viewBox 기준 비율이다. svg 가 width:100%, height:auto 라 비율이 그대로 맞는다.
   * 양 끝에서는 화면 밖으로 나가지 않게 가로 위치를 안쪽으로 물리고, 위쪽에 붙은
   * 지점에서는 말풍선을 아래로 뒤집는다.
   */
  const tip = hovered == null ? null : (() => {
    const point = points[hovered];
    const at = point.combatPower != null ? y(point.combatPower) : HEIGHT - BOTTOM;
    return {
      point,
      band: bandOf[hovered],
      left: Math.min(Math.max((x(hovered) / WIDTH) * 100, 15), 85),
      top: (at / HEIGHT) * 100,
      below: at < HEIGHT * 0.42,
    };
  })();

  return (
    <div className="chart" onMouseLeave={() => setHovered(null)}>
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
            {showFragments && required == null && !fragFlat && fy.ticks.length > k && (
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

        {/* 조각 축의 눈금은 전투력 격자와 자리가 다르다. 0%가 바닥에 딱 붙어야 뜻이 산다. */}
        {showFragments && required != null && [0, 25, 50, 75, 100].map((percent) => (
          <text
            key={percent}
            className="fragment-axis"
            x={WIDTH - RIGHT + 10}
            y={HEIGHT - BOTTOM - ((HEIGHT - TOP - BOTTOM) * percent) / 100 + 4}
            textAnchor="start"
          >{percent}%</text>
        ))}

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
        {showMedian && (
          <path
            className="median-line"
            d={lineAt((i) => bandOf[i]?.stats.median ?? null, y)}
            fill="none"
            strokeWidth="1.6"
            strokeLinejoin="round"
          />
        )}
        <path d={line((p) => p.combatPower, y)} fill="none" stroke="#ed702e" strokeWidth="2.6" strokeLinejoin="round" />

        {points.map((point, i) => {
          const pending = isPending(point);
          const selected = i === start || i === end || point.date === anchor;
          const cy = point.combatPower != null ? y(point.combatPower) : HEIGHT - BOTTOM;
          return (
            <g
              className="chart-point"
              key={point.date}
              onClick={() => interactive && !pending && onPick(point.date)}
              onMouseEnter={() => setHovered(i)}
              onFocus={() => setHovered(i)}
            >
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

      {tip && (
        <div
          className={`chart-tip${tip.below ? ' below' : ''}`}
          style={{ left: `${tip.left}%`, top: `${tip.top}%` }}
          role="tooltip"
        >
          <div className="chart-tip-head">
            <strong>{dateLabel(tip.point.date)}</strong>
            {tip.point.level != null && <span>Lv.{tip.point.level}</span>}
          </div>

          <div className="chart-tip-power">
            {isPending(tip.point)
              ? <span className="chart-tip-blank">불러오는 중</span>
              : tip.point.combatPower == null
                ? <span className="chart-tip-blank">계산 실패</span>
                : formatGameNumber(tip.point.combatPower)}
          </div>

          {/* 전투력에 안 들어가는 값이라 전투력 아래, 구간 통계 위에 둔다. */}
          {(tip.point.cooldownSecond || tip.point.cooldownSkipPercent) ? (
            <div className="chart-tip-cooldown">
              {tip.point.cooldownSecond ? `재사용 ${tip.point.cooldownSecond}초` : ''}
              {tip.point.cooldownSecond && tip.point.cooldownSkipPercent ? ' · ' : ''}
              {tip.point.cooldownSkipPercent ? `${tip.point.cooldownSkipPercent}% 미적용` : ''}
            </div>
          ) : null}

          {tip.point.solErdaFragments != null && (
            <div className="chart-tip-fragment">
              솔 에르다 조각 {formatNumber(tip.point.solErdaFragments)}
              {required != null ? ` · ${Math.round((tip.point.solErdaFragments / required) * 100)}%` : ''}
            </div>
          )}

          {tip.band && (
            <div className="chart-tip-band">
              <div className="chart-tip-band-head">
                Lv.{tip.band.stats.from}~{tip.band.stats.to}
                <span>표본 {formatNumber(tip.band.stats.sampleSize)}명</span>
              </div>
              <dl>
                <dt>상위 1%</dt><dd>{compact(tip.band.stats.top1Percent)}</dd>
                <dt>상위 10%</dt><dd>{compact(tip.band.stats.top10Percent)}</dd>
                <dt>상위 30%</dt><dd>{compact(tip.band.stats.top30Percent)}</dd>
                <dt className="median">중앙값</dt><dd className="median">{compact(tip.band.stats.median)}</dd>
                <dt>평균</dt><dd>{compact(tip.band.stats.mean)}</dd>
              </dl>
              {tip.band.estimated && (
                <p className="chart-tip-note">이 시점의 표본이 없어 최근 값으로 대신했어요.</p>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
