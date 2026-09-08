import type { CSSProperties } from 'react';
import type { EntryChange, StatDelta, StatDeltaGroup } from '../api/types';
import { formatStatDelta } from '../lib/format';
import { statLabel } from '../lib/labels';

interface Props {
  groups: StatDeltaGroup[];
  /** 스킬·심볼처럼 이름 있는 항목의 값 변화. 장비에는 없다 */
  entries?: EntryChange[];
  /** useHoverPop 이 잡아 준 화면 좌표 */
  style?: CSSProperties;
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
 * <p>자리는 {@link useHoverPop} 이 화면 좌표로 잡아 준다. 문서 흐름에 두면 카드 아래로
 * 삐져나온 만큼 스크롤이 늘어나 화면이 밀린다. 카드의 DOM 자식으로 두는 것은 그래야
 * 팝오버 위로 마우스가 넘어가도 카드의 :hover 가 유지되기 때문이다.
 */
export function StatPop({ groups, entries = [], style }: Props) {
  const named = entries.filter((entry) => entry.previous !== entry.current);
  if (groups.length === 0 && named.length === 0) return null;

  return (
    <div className="stat-pop" role="group" style={style}>
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
