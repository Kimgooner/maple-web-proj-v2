import type { DetailResponse, HistoryPoint, SlotChange } from '../api/types';
import { formatLongDate, formatNumber, formatPercentChange, formatSigned } from '../lib/format';
import { sourceLabel } from '../lib/labels';
import type { Interval } from '../lib/selection';
import { ArrowRight } from './icons';
import { ItemChangeCard } from './ItemChangeCard';
import { StatChips } from './StatChips';

interface Props {
  interval: Interval | null;
  points: HistoryPoint[];
  detail: DetailResponse | null;
  loading: boolean;
  error: string | null;
}

function SlotGroup({ title, changes }: { title: string; changes: SlotChange[] }) {
  if (changes.length === 0) {
    return <div className="group-empty"><b>{title}</b><span>이 구간에 변화 없음</span></div>;
  }
  return (
    <>
      <div className="group-title">{title}<small className="mono">{changes.length}</small></div>
      <div className="grid3">
        {changes.map((c, i) => <ItemChangeCard change={c} key={`${c.slot ?? c.currentSlot ?? c.previousSlot}-${i}`} />)}
      </div>
    </>
  );
}

export function DeltaPanel({ interval, points, detail, loading, error }: Props) {
  if (!interval) {
    return <div className="card empty">비교할 두 지점이 아직 없습니다. 추이가 다 불러와지면 마지막으로 변한 구간을 자동으로 고릅니다.</div>;
  }

  const prev = points.find((p) => p.date === interval.previousDate);
  const cur = points.find((p) => p.date === interval.currentDate);
  const hasBoth = prev?.combatPower != null && cur?.combatPower != null;
  const diff = hasBoth ? cur.combatPower! - prev.combatPower! : null;
  const diffClass = diff == null ? 'muted' : diff > 0 ? 'up' : diff < 0 ? 'down' : 'muted';

  const summary = detail?.changeSummary;
  const total = summary
    ? summary.itemChanges.length + summary.coreChanges.length + summary.cashChanges.length + summary.petChanges.length
    : 0;

  return (
    <div className="delta">
      <div className="card summary">
        <div>
          <div className="summary-dates">
            <span className="mono">{formatLongDate(interval.previousDate)}</span>
            <ArrowRight />
            <span className="mono" style={{ color: 'var(--text)' }}>{formatLongDate(interval.currentDate)}</span>
            <span className="hint">구간 바꾸기: 차트에서 두 점 선택</span>
          </div>
          <div className="summary-values">
            <span className="summary-prev mono">{prev?.combatPower != null ? formatNumber(prev.combatPower) : '—'}</span>
            <ArrowRight color="var(--text)" />
            <span className="summary-cur mono">{cur?.combatPower != null ? formatNumber(cur.combatPower) : '—'}</span>
          </div>
        </div>
        <div className="summary-right">
          <span className={`summary-diff mono ${diffClass}`}>{diff != null ? formatSigned(diff) : '—'}</span>
          <div className="summary-sub">
            {hasBoth && <span className={`mono ${diffClass}`}>{formatPercentChange(prev.combatPower!, cur.combatPower!)}</span>}
            {prev?.level != null && cur?.level != null && (
              <><span>·</span><span className="mono">Lv. {prev.level}{prev.level !== cur.level ? ` → ${cur.level}` : ''}</span></>
            )}
          </div>
        </div>
      </div>

      {error && <div className="card error-box"><span>{error}</span></div>}
      {loading && !detail && <><div className="skeleton" /><div className="skeleton" /></>}

      {summary && (
        <>
          {total === 0 && (
            <div className="card empty">이 구간에 잡힌 변화가 없습니다. 레벨업만 있었거나, 넥슨 쪽 반영이 늦은 것일 수 있어요.</div>
          )}
          <SlotGroup title="장비" changes={summary.itemChanges} />
          {summary.coreChanges.length > 0 ? (
            <>
              <div className="group-title">핵심<small className="mono">{summary.coreChanges.length}</small></div>
              {summary.coreChanges.map((c) => (
                <div className="card source-row" key={c.source}>
                  <span className="source-name">{sourceLabel(c.source)}</span>
                  <StatChips deltas={c.deltas} />
                </div>
              ))}
            </>
          ) : (
            <div className="group-empty"><b>핵심</b><span>스킬·세트·심볼·유니온 등에 변화 없음</span></div>
          )}
          <SlotGroup title="캐시" changes={summary.cashChanges} />
          <SlotGroup title="펫" changes={summary.petChanges} />
        </>
      )}
    </div>
  );
}
