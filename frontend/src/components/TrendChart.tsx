import { useState } from 'react';
import type { LevelBandWeek } from '../api/levelBands';
import type { HistoryPoint, HistoryRange } from '../api/types';
import { compact, dateLabel, formatAxisDate, formatGameNumber, formatNumber } from '../lib/format';
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
  /**
   * 아직 받는 중인가.
   *
   * <p>다 받은 뒤에도 비어 있는 자리는 "아직 안 온 것"이 아니라 <b>기록이 없는 것</b>이다.
   * 서버는 최신부터 훑다가 데이터가 없는 날을 만나면 거기서 멈추므로, 그보다 오래된 자리는
   * 끝내 채워지지 않는다. 둘을 구별하지 않으면 다 끝난 화면에서도 "불러오는 중"이라고 적힌다.
   */
  loading: boolean;
  /** 좁은 화면(≤520px). 낮은 viewBox 로 그린다 */
  compact?: boolean;
  onPick: (date: string) => void;
};

/**
 * 기록이 없는 구간에 붙는 이유.
 *
 * <p>월드를 옮기면 ocid 가 바뀌고, 새 ocid 로는 옮기기 전 날짜가 전부 비어 온다(실측:
 * 챌린저스 → 스카니아 이관 다음 날부터 30일이 통째로 빈다). 단정하지 않고 묻는 투로 두는
 * 것은 다른 원인(넥슨 쪽 결손)도 같은 모양으로 보이기 때문이다.
 */
const BLANK_NOTE = '혹시 월드 리프를 하셨나요? 리프 전 기록은 확인이 어려워요.';

/** viewBox 치수. svg 는 폭에 맞춰 통째로 줄어들므로, 좁은 화면은 다른 치수를 쓴다. */
type Layout = { WIDTH: number; HEIGHT: number; LEFT: number; RIGHT: number; TOP: number; BOTTOM: number };
const WIDE: Layout = { WIDTH: 980, HEIGHT: 280, LEFT: 65, RIGHT: 66, TOP: 24, BOTTOM: 34 };
/**
 * 폰. 980×280 을 300px 로 줄이면 86px 짜리 띠가 되어 선의 오르내림이 안 보인다.
 * viewBox 폭을 절반 가까이 줄여 같은 화면 폭에서 두 배 높게 그린다. 글자 크기는
 * CSS(≤520px) 가 viewBox 단위로 따로 잡는다.
 */
const COMPACT: Layout = { WIDTH: 560, HEIGHT: 300, LEFT: 58, RIGHT: 54, TOP: 20, BOTTOM: 34 };
const TICKS = 4;

type Scale = ((value: number) => number) & { ticks: number[] };

/** 0 을 바닥에, 완성에 필요한 총량을 천장에 두는 축. 눈금은 백분율이라 값을 담지 않는다. */
function progressScale(required: number, { HEIGHT, TOP, BOTTOM }: Layout): Scale {
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
function scale(values: number[], { HEIGHT, TOP, BOTTOM }: Layout): Scale {
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
  { points, range, showFragments, bands, showMedian, interval, anchor, interactive, loading, compact: narrow = false, onPick }: Props,
) {
  const [hovered, setHovered] = useState<number | null>(null);
  const layout = narrow ? COMPACT : WIDE;
  const { WIDTH, HEIGHT, LEFT, RIGHT, TOP, BOTTOM } = layout;
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
   * 축은 캐릭터의 전투력만으로 잡는다.
   *
   * <p>전에는 기준선까지 담았는데, 22억 캐릭터의 같은 레벨 중앙값이 2.5억이면 축이 0부터
   * 25억이 되어 한 달치 오르내림이 직선으로 눌렸다 — 추이를 보는 화면에서 추이가 사라진
   * 것이다. 기준선은 "내가 중앙값 위인지 아래인지"만 말하면 되므로, 축 밖으로 나가면
   * 가장자리에 붙여 그리고 값을 적어 둔다. 축 안에 들어오는 캐릭터는 전과 같다.
   */
  const y = scale(points.map((p) => p.combatPower).filter((v): v is number => v != null), layout);
  const floorY = HEIGHT - BOTTOM;
  /** 기준선이 축 밖이면 가장자리에 붙인다. */
  const clampY = (value: number) => Math.min(Math.max(y(value), TOP), floorY);
  /**
   * 기준선이 가장자리에 붙어 있는가. 마지막 지점 기준이다 — 레벨이 올라 구간이 바뀌면
   * 중앙값도 뛰지만, 지금 어디쯤인지는 최근 값이 말한다. 붙어 있으면 값을 옆에 적어
   * 선만 봐서는 얼마나 떨어져 있는지 모르는 것을 메운다.
   */
  const lastMedian = medians.length ? medians[medians.length - 1] : null;
  const medianEdge = lastMedian == null ? null
    : y(lastMedian) > floorY ? 'below' : y(lastMedian) < TOP ? 'above' : null;
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
  const required = range === 'yearly'
    ? [...points].reverse()
        .map((p) => p.solErdaFragmentsRequired)
        .find((v): v is number => v != null && v > 0) ?? null
    : null;
  const fy = required != null ? progressScale(required, layout) : scale(fragValues, layout);
  // 조각이 한 번도 안 변한 캐릭터(만렙)는 축을 다섯 칸으로 늘려 봐야 같은 숫자만 반복된다.
  const fragFlat = required == null && fragValues.length > 0
    && Math.min(...fragValues) === Math.max(...fragValues);

  /** 지점이 아니라 인덱스로 값을 집는 선. 기준선은 값이 지점 안이 아니라 구간 통계에 있다. */
  const lineAt = (pick: (index: number) => number | null, at: (value: number) => number): string => {
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
      // 집계 대기 자리는 값이 없어도 끊지 않고 건너 앞뒤를 잇는다 — 02시 뒤에 채워질 자리다.
      if (point.awaiting) return;
      const value = pick(point);
      if (value == null) { open = false; return; }
      path += `${open ? 'L' : 'M'}${x(index)},${at(value)} `;
      open = true;
    });
    return path.trim();
  };

  /**
   * 집계 대기 자리의 세로 위치. 값이 없으니 앞뒤 값 있는 지점을 잇는 선 위에 놓는다 —
   * 선이 지나는 자리에 표시가 있어야 "여기가 빈 날"로 읽힌다. 한쪽만 있으면 그쪽 높이.
   */
  const awaitingY = (index: number): number => {
    let before: number | null = null;
    let after: number | null = null;
    for (let i = index - 1; i >= 0; i -= 1) { if (points[i].combatPower != null) { before = i; break; } }
    for (let i = index + 1; i < points.length; i += 1) { if (points[i].combatPower != null) { after = i; break; } }
    if (before == null && after == null) return HEIGHT - BOTTOM;
    if (before == null) return y(points[after!].combatPower!);
    if (after == null) return y(points[before].combatPower!);
    const t = (index - before) / (after - before);
    return y(points[before].combatPower!) + (y(points[after].combatPower!) - y(points[before].combatPower!)) * t;
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
  const tickCount = Math.min(narrow ? 4 : 6, count);
  const labelled = new Set(
    Array.from({ length: tickCount }, (_, i) => Math.round((i * (count - 1)) / Math.max(1, tickCount - 1))));
  const fragments = showFragments ? fragmentRuns() : [];
  const half = count > 1 ? (x(1) - x(0)) / 2 : (WIDTH - LEFT - RIGHT) / 2;

  /**
   * 기록이 없는 구간. 다 받은 뒤에 남은 빈 자리를 이어 붙인다.
   *
   * <p>선이 끊긴 것만으로는 "없는 기간"인지 "아직 그리는 중"인지 알 수 없다. 배경을 깔아
   * 두면 호버하기 전에도 읽힌다 — 이 구간에서는 점도 찍지 않고 고를 수도 없으니, 화면이
   * 하는 말과 실제 동작이 같아진다.
   */
  const blankRuns = loading ? [] : (() => {
    const runs: { from: number; to: number }[] = [];
    let from: number | null = null;
    points.forEach((point, i) => {
      if (isPending(point)) { if (from == null) from = i; return; }
      if (from != null) { runs.push({ from, to: i - 1 }); from = null; }
    });
    if (from != null) runs.push({ from, to: count - 1 });
    return runs;
  })();

  /**
   * 말풍선은 SVG 밖에 HTML 로 띄운다 — 표를 그리기엔 foreignObject 보다 이쪽이 낫다.
   * 자리는 viewBox 기준 비율이다. svg 가 width:100%, height:auto 라 비율이 그대로 맞는다.
   * 양 끝에서는 화면 밖으로 나가지 않게 가로 위치를 안쪽으로 물리고, 위쪽에 붙은
   * 지점에서는 말풍선을 아래로 뒤집는다.
   */
  const tip = hovered == null ? null : (() => {
    const point = points[hovered];
    const at = point.combatPower != null ? y(point.combatPower) : point.awaiting ? awaitingY(hovered) : HEIGHT - BOTTOM;
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

        {blankRuns.map((run) => {
          const left = Math.max(LEFT, x(run.from) - half);
          const right = Math.min(WIDTH - RIGHT, x(run.to) + half);
          const width = right - left;
          return (
            <g key={`blank-${run.from}`}>
              <rect className="chart-blank" x={left} y={TOP} width={width} height={HEIGHT - TOP - BOTTOM} />
              {/* 기록이 시작되는 자리. 선이 여기서부터 그려진다. */}
              {right < WIDTH - RIGHT && (
                <line x1={right} x2={right} y1={TOP} y2={HEIGHT - BOTTOM}
                      stroke="#d7dbe2" strokeWidth="1" strokeDasharray="3 4" />
              )}
              {/* 좁은 구간에 글자를 넣으면 잘려서 오히려 안 읽힌다. */}
              {width >= 84 && (
                <text className="chart-blank-label"
                      x={left + width / 2} y={TOP + (HEIGHT - TOP - BOTTOM) / 2 + (width >= 360 ? -4 : 4)}
                      textAnchor="middle">기록 없음</text>
              )}
              {/*
                구간이 넓으면(= 거의 전부 비었으면) 이유까지 적는다. 월드를 옮긴 직후에
                많이 보는 화면이라, 호버 전에 읽혀야 "고장났다"로 안 읽힌다.
              */}
              {width >= 360 && (
                <text className="chart-blank-note"
                      x={left + width / 2} y={TOP + (HEIGHT - TOP - BOTTOM) / 2 + 16}
                      textAnchor="middle">{BLANK_NOTE}</text>
              )}
            </g>
          );
        })}

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
            d={lineAt((i) => bandOf[i]?.stats.median ?? null, clampY)}
            fill="none"
            strokeWidth="1.6"
            strokeLinejoin="round"
          />
        )}
        {showMedian && medianEdge && lastMedian != null && (
          <text
            className="median-edge"
            x={WIDTH - RIGHT - 6}
            y={medianEdge === 'below' ? floorY - 5 : TOP + 12}
            textAnchor="end"
          >
            {`같은 레벨 중앙값 ${compact(lastMedian)} ${medianEdge === 'below' ? '↓' : '↑'}`}
          </text>
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
              onClick={() => interactive && !pending && !point.awaiting && onPick(point.date)}
              onMouseEnter={() => setHovered(i)}
              onFocus={() => setHovered(i)}
            >
              {point.combatPower != null && (
                <circle
                  cx={x(i)} cy={cy} r={selected ? 5 : 2.6}
                  fill={selected ? '#ed702e' : '#fff'} stroke="#ed702e" strokeWidth="1.5"
                />
              )}
              {pending && loading && <circle cx={x(i)} cy={HEIGHT - BOTTOM} r="2.5" fill="#dfe3e9" />}
              {/* 집계 대기. 선이 지나는 자리에 점선 테두리의 빈 점 — 값이 아니라 자리임을 말한다. */}
              {point.awaiting && (
                <circle cx={x(i)} cy={awaitingY(i)} r="3" fill="#fff" stroke="#c9ced8" strokeWidth="1.5" strokeDasharray="2 1.5" />
              )}
              {labelled.has(i) && (
                <text x={x(i)} y={HEIGHT - 10} textAnchor="middle">
                  {formatAxisDate(point.date, range)}
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
            {tip.point.awaiting
              ? <span className="chart-tip-blank">집계 대기</span>
              : isPending(tip.point)
                ? <span className="chart-tip-blank">{loading ? '불러오는 중' : '기록 없음'}</span>
                : tip.point.combatPower == null
                  ? <span className="chart-tip-blank">계산 실패</span>
                  : formatGameNumber(tip.point.combatPower)}
          </div>

          {/* 우리가 못 받은 것이 아니라 넥슨에 없는 기간이라는 것을 밝힌다. */}
          {!loading && isPending(tip.point) && (
            <div className="chart-tip-note">{BLANK_NOTE}</div>
          )}
          {tip.point.awaiting && (
            <div className="chart-tip-note">전날 기록은 오전 2시 이후에 볼 수 있어요.</div>
          )}

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
