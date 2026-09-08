import { Fragment } from 'react';
import type { EntryChange, StatDelta } from '../api/types';
import type { ChangeRow as Row } from '../lib/changes';
import { formatStatDelta } from '../lib/format';
import { CHANGE_TYPE_LABELS, sourceLabel, statLabel } from '../lib/labels';

/** 이전·이후 칸에 보여줄 항목 줄 수. 그 위는 "외 N개" 로 접는다. */
const ENTRY_LINES = 3;

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

/** 오른 것 / 내린 것 / 그대로인 것. 비어 있는 줄은 그리지 않는다. */
const STAT_SIDES = [
  { key: 'up', label: '증가', match: (delta: number) => delta > 0 },
  { key: 'down', label: '감소', match: (delta: number) => delta < 0 },
  { key: 'flat', label: '유지', match: (delta: number) => delta === 0 },
];

/**
 * 펼친 화면의 스탯 묶음. 한 줄에 +와 -를 섞어 늘어놓으면 색만이 둘을 갈라서
 * 무엇이 깎였는지 세어 봐야 안다. 오른 쪽과 내린 쪽을 줄로 나눠 적는다.
 */
function StatChips({ deltas }: { deltas: StatDelta[] }) {
  return (
    <div className="stat-split">
      {STAT_SIDES.map((side) => ({ side, items: deltas.filter((delta) => side.match(delta.delta)) }))
        .filter(({ items }) => items.length > 0)
        .map(({ side, items }) => (
          <Fragment key={side.key}>
            <span className={`stat-side ${side.key}`}>{side.label}</span>
            <div className="stat-chips">
              {items.map((delta) => (
                <span className={`stat-chip ${side.key}`} key={delta.statName}>
                  <span className="stat-chip-name">{statLabel(delta.statName)}</span>
                  <span className="stat-chip-value">{formatStatDelta(delta.statName, delta.delta)}</span>
                </span>
              ))}
            </div>
          </Fragment>
        ))}
    </div>
  );
}

function EntryDetail({ detail }: { detail: string }) {
  return (
    <div className="entry-detail">
      {detail.split('\n').map((line) => <div key={line}>{line}</div>)}
    </div>
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
            {/* 설명은 그 값이 살아 있는 쪽에 붙인다. 사라진 항목의 효과를
                "없음" 아래에 늘어놓으면 아직 받는 것처럼 읽힌다. */}
            <td className="before">
              {entry.previous ?? '없음'}
              {entry.detail && entry.current == null && <EntryDetail detail={entry.detail} />}
            </td>
            <td className="after">
              {entry.current ?? '없음'}
              {entry.detail && entry.current != null && <EntryDetail detail={entry.detail} />}
            </td>
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
export function ChangeRow({ row, open, onToggle }: { row: Row; open: boolean; onToggle: () => void }) {
  const deltas = row.change.deltas ?? [];
  const entries = row.kind === 'source' ? row.change.entries ?? [] : [];

  const title = row.kind === 'slot'
    ? row.change.currentItemName ?? row.change.previousItemName ?? '이름 없는 항목'
    : sourceLabel(row.change.source);
  const subtitle = row.kind === 'slot'
    ? `${row.label} · ${bareSlot(row.change.currentSlot ?? row.change.slot ?? row.change.previousSlot)}`
    : entries.length ? `${entries.length}개 항목 변경` : '스탯 변화';
  const icon = row.kind === 'slot'
    ? row.change.currentItemIcon ?? row.change.previousItemIcon
    : entries.find((entry) => entry.icon)?.icon ?? null;

  /**
   * 항목이 여럿인 소스(어빌리티 3줄, 심볼 여러 개…)는 한 줄로 이어 붙이면 읽을 수 없다.
   * 항목마다 줄을 나눠 적고, 너무 길어지지 않게 세 개까지만 보인다.
   */
  const entryLines = (pick: (entry: EntryChange) => string | null) => {
    if (entries.length === 0) return <>—</>;
    return (
      <>
        {entries.slice(0, ENTRY_LINES).map((entry) => (
          <div className="entry-line" key={entry.name}>
            <span className="entry-line-name" title={entry.name}>{entry.name}</span>
            <span>{pick(entry) ?? '없음'}</span>
          </div>
        ))}
        {entries.length > ENTRY_LINES && (
          <div className="entry-line muted">외 {entries.length - ENTRY_LINES}개</div>
        )}
      </>
    );
  };

  /**
   * 옵션 변경은 장비가 그대로고 옵션만 바뀐 것이라, 이전·이후에 같은 이름을 두 번 적으면
   * 무엇이 바뀌었는지 되레 가려진다. 그 칸은 비우고 "주요 변화" 가 말하게 둔다.
   * 교체·장착·해제는 무엇이 무엇으로 바뀌었는지가 핵심이라 그대로 적는다.
   */
  const sameItem = row.kind === 'slot' && row.change.changeType === 'STAT_CHANGED';
  const before = row.kind === 'slot'
    ? (
      <span className="value-item">
        {/* 무엇이 빠졌는지는 이름보다 그림이 빠르다. 옵션만 바뀐 줄에는 뺄 것이 없어 안 붙인다. */}
        {!sameItem && row.change.previousItemIcon && (
          <img className="value-icon" src={row.change.previousItemIcon} alt="" loading="lazy" />
        )}
        {sameItem ? '—' : row.change.previousItemName ?? '미장착'}
      </span>
    )
    : entryLines((entry) => entry.previous);
  const after = row.kind === 'slot'
    ? <>{sameItem ? '옵션만 바뀜' : row.change.currentItemName ?? '미장착'}</>
    : entryLines((entry) => entry.current);

  const groups = row.kind === 'slot' && row.change.deltaGroups?.length
    ? row.change.deltaGroups
    : [{ category: '스탯 증감', deltas }];

  return (
    <details
      className="change-row"
      open={open}
      onToggle={(event) => {
        // 브라우저가 details 를 열고 닫을 때마다 부모에 알린다. 하나만 열어 두려고
        // 열림 상태를 위에서 들고 있다.
        if (event.currentTarget.open !== open) onToggle();
      }}
    >
      <summary className="change-summary">
        <div className="change-name">
          <span className="item-icon" aria-hidden="true">
            {icon ? <img src={icon} alt="" loading="lazy" /> : row.kind === 'slot' ? '◇' : '✦'}
          </span>
          <div>
            <div className="item-title">
              {title}
              {row.kind === 'slot' && (
                <span className={`change-badge badge-${row.change.changeType}`}>
                  {CHANGE_TYPE_LABELS[row.change.changeType] ?? row.change.changeType}
                </span>
              )}
            </div>
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
            <StatChips deltas={group.deltas} />
          </div>
        ))}
        {entries.length === 0 && deltas.length === 0 && (
          <p className="muted">추가로 표시할 스탯 변화가 없습니다.</p>
        )}
      </div>
    </details>
  );
}
