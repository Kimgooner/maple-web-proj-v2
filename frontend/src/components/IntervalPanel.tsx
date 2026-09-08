import type { ChangeSummary, HistoryPoint } from '../api/types';
import type { Interval } from '../lib/selection';
import { changeCounts } from '../lib/changes';
import { dateLabel, formatNumber, formatPercentChange, formatSigned } from '../lib/format';

interface Props {
  interval: Interval | null;
  points: HistoryPoint[];
  summary: ChangeSummary | null;
}

/** 고른 구간의 요약. 차트 옆에 붙는다. */
export function IntervalPanel({ interval, points, summary }: Props) {
  const previous = interval ? points.find((point) => point.date === interval.previousDate) : undefined;
  const current = interval ? points.find((point) => point.date === interval.currentDate) : undefined;
  const both = previous?.combatPower != null && current?.combatPower != null;
  const delta = both ? current.combatPower! - previous.combatPower! : null;
  const counts = changeCounts(summary);

  return (
    <aside className="interval panel" aria-labelledby="interval-title">
      <span className="outline-badge">선택한 구간</span>
      <h2 id="interval-title">
        {interval ? `${dateLabel(interval.previousDate)} → ${dateLabel(interval.currentDate)}` : '두 시점을 선택해 주세요'}
      </h2>
      <div className="interval-gain">
        <strong className={delta == null ? 'neutral' : delta > 0 ? 'positive' : delta < 0 ? 'negative' : 'neutral'}>
          {delta != null ? formatSigned(delta) : '—'}
        </strong>
        <span>{both ? formatPercentChange(previous.combatPower!, current.combatPower!) : ''}</span>
      </div>
      <p className="muted number">
        {both ? `${formatNumber(previous.combatPower!)} → ${formatNumber(current.combatPower!)}` : '전투력의 변화를 비교합니다'}
      </p>

      <div className="interval-counts">
        <h3>이 구간의 변경 내역</h3>
        <div><span><i>◇</i>장비</span><b>{summary ? `${counts.items}건` : '—'}</b></div>
        <div><span><i>✦</i>핵심 <small>스킬 · 심볼 등</small></span><b>{summary ? `${counts.core}건` : '—'}</b></div>
        <div><span><i>♧</i>캐시 · 펫</span><b>{summary ? `${counts.other}건` : '—'}</b></div>
      </div>

      <p className="interval-note">
        같은 구간에 바뀐 항목을 모아봤어요.<br />항목별 전투력 기여도를 뜻하지는 않아요.
      </p>
    </aside>
  );
}
