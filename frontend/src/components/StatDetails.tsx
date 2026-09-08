import { useState, type ReactNode } from 'react';
import type { StatDelta, StatDeltaGroup } from '../api/types';
import { formatStatDelta } from '../lib/format';
import { statLabel } from '../lib/labels';

interface Props {
  groups: StatDeltaGroup[];
  /** 트리거 옆에 함께 놓을 것 (핵심 변화의 항목 이름 목록 등) */
  children?: ReactNode;
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

/**
 * 스탯 변화를 접어 둔다. 변화가 많은 구간은 펼쳐 두면 페이지가 한없이 길어져서,
 * 요약만 보이고 마우스를 올리거나 누르면 펼친다.
 *
 * <p>터치 기기에는 hover 가 없으므로 누르는 것으로도 열린다.
 */
export function StatDetails({ groups, children }: Props) {
  const [open, setOpen] = useState(false);
  if (groups.length === 0) return children ? <>{children}</> : null;

  const summary = groups.map((g) => `${g.category} ${g.deltas.length}`).join(' · ');

  return (
    <div
      className={`stat-details ${open ? 'open' : ''}`}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        className="stat-trigger"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="stat-trigger-label">{summary}</span>
        <span className="stat-caret" aria-hidden="true" />
      </button>
      {children}
      {open && (
        <div className="stat-pop" role="group">
          {groups.map((group) => (
            <div className="stat-pop-group" key={group.category}>
              {groups.length > 1 && <div className="stat-pop-title">{group.category}</div>}
              <div className="chips">
                {group.deltas.map((delta) => <Chip delta={delta} key={delta.statName} />)}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
