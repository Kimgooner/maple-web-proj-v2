import type { EntryChange, StatDelta, StatDeltaGroup } from '../api/types';
import { formatStatDelta } from '../lib/format';
import { statLabel } from '../lib/labels';

interface Props {
  groups: StatDeltaGroup[];
  /** 스킬·심볼처럼 이름 있는 항목의 값 변화. 장비에는 없다 */
  entries?: EntryChange[];
}

function Chip({ delta }: { delta: StatDelta }) {
  return (
    <span className="chip">
      <span className="label">{statLabel(delta.statName)}</span>
      <span className={`mono ${delta.delta > 0 ? 'up' : delta.delta < 0 ? 'down' : 'muted'}`}>
        {formatStatDelta(delta.statName, delta.delta)}
      </span>
    </span>
  );
}

function EntryRow({ entry }: { entry: EntryChange }) {
  const kind = entry.previous == null ? 'added' : entry.current == null ? 'removed' : 'changed';
  return (
    <div className={`pop-entry entry-${kind}`}>
      <span className="pop-entry-name">{entry.name}</span>
      <span className="mono">
        {kind === 'added' && <><span className="up">새로 적용</span> {entry.current}</>}
        {kind === 'removed' && <><span className="down">빠짐</span> {entry.previous}</>}
        {kind === 'changed' && <><span className="muted">{entry.previous}</span> → {entry.current}</>}
      </span>
    </div>
  );
}

/**
 * 카드 위에 마우스를 올렸을 때 뜨는 세부 내역. 여닫는 것은 CSS 가 맡는다
 * (`.item-card:hover > .stat-pop`) — 카드 어디에 올려도 뜬다.
 *
 * <p>카드 바로 아래에 붙여 둔다. 사이를 띄우면 마우스가 그 틈을 지날 때 닫힌다.
 */
export function StatPop({ groups, entries = [] }: Props) {
  const named = entries.filter((entry) => entry.previous !== entry.current);
  if (groups.length === 0 && named.length === 0) return null;

  return (
    <div className="stat-pop" role="group">
      {named.length > 0 && (
        <div className="stat-pop-group">
          {named.map((entry) => <EntryRow entry={entry} key={entry.name} />)}
        </div>
      )}
      {groups.map((group) => (
        <div className="stat-pop-group" key={group.category}>
          {(groups.length > 1 || named.length > 0) && <div className="stat-pop-title">{group.category}</div>}
          <div className="chips">
            {group.deltas.map((delta) => <Chip delta={delta} key={delta.statName} />)}
          </div>
        </div>
      ))}
    </div>
  );
}
