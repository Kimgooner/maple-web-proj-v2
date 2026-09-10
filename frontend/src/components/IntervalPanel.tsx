import type { CharacterInfo, ChangeSummary, HistoryPoint } from '../api/types';
import type { Interval } from '../lib/selection';
import { changeCounts, totalDeltas } from '../lib/changes';
import { presentDeltas } from '../lib/stats';
import { StatChips } from './StatChips';
import { dateLabel, formatGameNumber, formatPercentChange, formatSignedGame } from '../lib/format';

interface Props {
  interval: Interval | null;
  points: HistoryPoint[];
  summary: ChangeSummary | null;
  /** 직업. 남의 직업 스탯을 지우고 공격력·마력 중 쓰는 쪽만 남기는 데 쓴다 */
  info: CharacterInfo | null;
}

/**
 * 고른 구간의 재사용 대기시간. 0 이면 적지 않고, 이전과 다르면 화살표로 잇는다.
 * 전투력과 달리 잘 안 바뀌는 값이라, 안 바뀌었으면 이후 값만 적어 줄을 아낀다.
 */
function cooldownLines(previous: HistoryPoint | undefined, current: HistoryPoint | undefined): string[] {
  // 단위는 양쪽에 다 붙인다. "2초 → 5" 처럼 한쪽만 붙으면 5가 무슨 단위인지 읽다 멈춘다.
  const rows: [number, number, string, string][] = [
    [previous?.cooldownSecond ?? 0, current?.cooldownSecond ?? 0, '초', ''],
    [previous?.cooldownSkipPercent ?? 0, current?.cooldownSkipPercent ?? 0, '%', '로 미적용'],
  ];
  return rows
    .filter(([before, after]) => before > 0 || after > 0)
    .map(([before, after, unit, tail]) => (before === after
      ? `재사용 대기시간 ${after}${unit}${tail}`
      : `재사용 대기시간 ${before}${unit} → ${after}${unit}${tail}`));
}

/** 고른 구간의 요약. 차트 옆에 붙는다. */
export function IntervalPanel({ interval, points, summary, info }: Props) {
  const previous = interval ? points.find((point) => point.date === interval.previousDate) : undefined;
  const current = interval ? points.find((point) => point.date === interval.currentDate) : undefined;
  const both = previous?.combatPower != null && current?.combatPower != null;
  const delta = both ? current.combatPower! - previous.combatPower! : null;
  const counts = changeCounts(summary);
  const cooldowns = cooldownLines(previous, current);
  const totals = presentDeltas(totalDeltas(summary), info);

  return (
    <aside className="interval panel" aria-labelledby="interval-title">
      <span className="outline-badge">선택한 구간</span>
      <h2 id="interval-title">
        {interval ? `${dateLabel(interval.previousDate)} → ${dateLabel(interval.currentDate)}` : '두 시점을 선택해 주세요'}
      </h2>
      <div className="interval-gain">
        <strong className={delta == null ? 'neutral' : delta > 0 ? 'positive' : delta < 0 ? 'negative' : 'neutral'}>
          {delta != null ? formatSignedGame(delta) : '—'}
        </strong>
        <span>{both ? formatPercentChange(previous.combatPower!, current.combatPower!) : ''}</span>
      </div>
      <p className="muted number">
        {both ? `${formatGameNumber(previous.combatPower!)} → ${formatGameNumber(current.combatPower!)}` : '전투력의 변화를 비교합니다'}
      </p>

      {/*
        고른 구간의 재사용 대기시간. 헤더 블럭은 늘 '지금' 값이라, 지난 시점을 고른 사람은
        그때 값을 알 길이 없었다. 바뀐 구간이면 이전 → 이후로 같이 적는다.
      */}
      {cooldowns.length > 0 && (
        <div className="cooldowns interval-cooldowns">
          {cooldowns.map((text) => <span key={text}>{text}</span>)}
        </div>
      )}

      <div className="interval-counts">
        <h3>이 구간의 변경 내역</h3>
        {/*
          분류를 풀어 쓰되 줄이 아니라 칩으로 흘린다. 줄로 두면 분류가 늘어난 만큼
          세로로 길어져 옆의 차트와 높이가 어긋난다. 칩은 넘치면 다음 줄로 접혀서,
          한 분류가 걸리든 여섯이 걸리든 한두 줄로 끝난다.
        */}
        {!summary
          ? <p className="interval-counts-empty">—</p>
          : counts.length === 0
            ? <p className="interval-counts-empty">바뀐 항목이 없어요.</p>
            : (
              <div className="interval-chips">
                {counts.map(({ key, label, count }) => (
                  <span className="interval-chip" key={key}>
                    {label}<b>{count}</b>
                  </span>
                ))}
              </div>
            )}

        {/*
          분류 칩만 두면 "무엇이" 바뀌었는지는 알아도 "얼마나"가 없다. 줄마다 흩어져 있는
          증감을 스탯별로 더해 여기 한 번 적는다 - 아래로 내려가 줄을 일일이 더하지 않아도
          이 구간에 캐릭터가 어디로 움직였는지가 읽힌다.
        */}
        {totals.length > 0 && (
          <div className="interval-totals">
            <StatChips deltas={totals} limit={6} />
          </div>
        )}
      </div>
    </aside>
  );
}
