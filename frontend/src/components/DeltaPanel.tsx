import type { DetailResponse, EntryChange, HistoryPoint, SlotChange } from '../api/types';
import { formatLongDate, formatNumber, formatPercentChange, formatSigned } from '../lib/format';
import { sourceLabel } from '../lib/labels';
import type { Interval } from '../lib/selection';
import { ArrowRight } from './icons';
import { ItemChangeCard } from './ItemChangeCard';
import { StatPop } from './StatPop';

interface Props {
  interval: Interval | null;
  points: HistoryPoint[];
  detail: DetailResponse | null;
  loading: boolean;
  error: string | null;
  hint: string;
}

/** 무엇이 바뀌었는지만. 얼마나 바뀌었는지는 카드에 마우스를 올리면 뜬다. */
function EntryNames({ entries }: { entries: EntryChange[] }) {
  if (entries.length === 0) return null;
  return (
    <div className="entry-names">
      {entries.map((e) => {
        const kind = e.previous == null ? 'added' : e.current == null ? 'removed' : 'changed';
        return (
          <span className={`entry-name entry-${kind}`} key={e.name} title={e.name}>
            {e.icon && <img className="entry-icon" src={e.icon} alt="" />}
            {e.name}
          </span>
        );
      })}
    </div>
  );
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

export function DeltaPanel({ interval, points, detail, loading, error, hint }: Props) {
  if (!interval) {
    return <div className="card empty">{hint}</div>;
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
            <span className="hint">{hint}</span>
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
                  <div className="source-head">
                    <span className="source-name">{sourceLabel(c.source)}</span>
                    <EntryNames entries={c.entries ?? []} />
                  </div>
                  <StatPop
                    groups={c.deltas.length ? [{ category: '스탯', deltas: c.deltas }] : []}
                    entries={c.entries ?? []}
                  />
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
