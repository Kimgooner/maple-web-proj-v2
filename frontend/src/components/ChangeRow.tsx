import type { EntryChange, StatDelta } from '../api/types';
import type { ChangeRow as Row } from '../lib/changes';
import { formatStatDelta } from '../lib/format';
import { CHANGE_TYPE_LABELS, sourceLabel, statLabel } from '../lib/labels';

function tone(value: number): string {
  return value > 0 ? 'positive' : value < 0 ? 'negative' : 'neutral';
}

function StatSpans({ deltas, limit = Infinity }: { deltas: StatDelta[]; limit?: number }) {
  return (
    <>
      {deltas.slice(0, limit).map((delta) => (
        <span className={tone(delta.delta)} key={delta.statName}>
          {statLabel(delta.statName)} {formatStatDelta(delta.statName, delta.delta)}
        </span>
      ))}
      {deltas.length > limit && <span className="muted">외 {deltas.length - limit}개</span>}
    </>
  );
}

function EntryTable({ entries }: { entries: EntryChange[] }) {
  return (
    <table className="entry-table">
      <thead>
        <tr>{['항목', '이전', '이후'].map((title) => <th scope="col" key={title}>{title}</th>)}</tr>
      </thead>
      <tbody>
        {entries.map((entry) => (
          <tr key={entry.name}>
            <td>
              <span className="entry-label">
                {entry.icon && <img src={entry.icon} alt="" loading="lazy" />}
                {entry.name}
              </span>
            </td>
            <td className="before">{entry.previous ?? '없음'}</td>
            <td className="after">{entry.current ?? '없음'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** 슬롯 접두사를 뗀 부위명. 백엔드 키는 "장비 - 무기" 처럼 온다. */
function bareSlot(slot: string | null): string {
  if (!slot) return '';
  const at = slot.lastIndexOf(' - ');
  return at >= 0 ? slot.slice(at + 3) : slot;
}

/**
 * 변경 내역 한 줄. 접혀 있고 누르면 펼쳐진다.
 *
 * <p>펼친 내용은 장비면 옵션·잠재·익셉셔널로 나뉘고, 스킬 같은 핵심이면
 * 항목별 이전·이후 표가 먼저 온다.
 */
export function ChangeRow({ row }: { row: Row }) {
  const deltas = row.change.deltas ?? [];
  const entries = row.kind === 'source' ? row.change.entries ?? [] : [];

  const title = row.kind === 'slot'
    ? row.change.currentItemName ?? row.change.previousItemName ?? '이름 없는 항목'
    : sourceLabel(row.change.source);
  const subtitle = row.kind === 'slot'
    ? `${row.label} · ${bareSlot(row.change.currentSlot ?? row.change.slot ?? row.change.previousSlot)} · ${CHANGE_TYPE_LABELS[row.change.changeType] ?? row.change.changeType}`
    : entries.length ? `${entries.length}개 항목 변경` : '스탯 변화';
  const icon = row.kind === 'slot'
    ? row.change.currentItemIcon ?? row.change.previousItemIcon
    : entries.find((entry) => entry.icon)?.icon ?? null;

  const before = row.kind === 'slot'
    ? row.change.previousItemName ?? '미장착'
    : entries.length ? entries.map((e) => `${e.name}: ${e.previous ?? '없음'}`).slice(0, 2).join(' / ') : '—';
  const after = row.kind === 'slot'
    ? row.change.currentItemName ?? '미장착'
    : entries.length ? entries.map((e) => `${e.name}: ${e.current ?? '없음'}`).slice(0, 2).join(' / ') : '—';

  const groups = row.kind === 'slot' && row.change.deltaGroups?.length
    ? row.change.deltaGroups
    : [{ category: '스탯 증감', deltas }];

  return (
    <details className="change-row">
      <summary className="change-summary">
        <div className="change-name">
          <span className="item-icon" aria-hidden="true">
            {icon ? <img src={icon} alt="" loading="lazy" /> : row.kind === 'slot' ? '◇' : '✦'}
          </span>
          <div>
            <div className="item-title">{title}</div>
            <div className="item-subtitle">{subtitle}</div>
          </div>
        </div>
        <div className="row-value before">{before}</div>
        <div className="row-value after">{after}</div>
        <div className="stat-preview">
          {deltas.length
            ? <StatSpans deltas={deltas} limit={2} />
            : <span className="muted">{entries.length ? '항목 변경 · 상세 보기' : '세부 변경 보기'}</span>}
        </div>
        <span className="chevron" aria-hidden="true">›</span>
      </summary>
      <div className="change-body">
        {entries.length > 0 && (
          <>
            <h3>항목별 이전 · 이후</h3>
            <EntryTable entries={entries} />
          </>
        )}
        {groups.filter((group) => group.deltas.length > 0).map((group) => (
          <div key={group.category}>
            <h3>{group.category}</h3>
            <div className="all-stats"><StatSpans deltas={group.deltas} /></div>
          </div>
        ))}
        {entries.length === 0 && deltas.length === 0 && (
          <p className="muted">추가로 표시할 스탯 변화가 없습니다.</p>
        )}
      </div>
    </details>
  );
}
