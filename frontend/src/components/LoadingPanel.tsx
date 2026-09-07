import type { HistoryMeta, HistoryPoint, HistoryRange } from '../api/types';
import { formatLongDate } from '../lib/format';
import { isPending } from '../lib/history';

interface Props {
  name: string;
  range: HistoryRange;
  meta: HistoryMeta | null;
  points: HistoryPoint[];
  received: number;
  total: number;
}

type RowState = 'done' | 'failed' | 'active' | 'waiting';

/** 조회가 끝날 때까지 보여주는 창. 서버가 최신부터 보내므로 목록도 최신이 위다. */
export function LoadingPanel({ name, range, meta, points, received, total }: Props) {
  const rows = [...points].reverse();
  let activeMarked = false;
  const stateOf = (p: HistoryPoint): RowState => {
    if (!isPending(p)) return p.combatPower == null ? 'failed' : 'done';
    if (!activeMarked) { activeMarked = true; return 'active'; }
    return 'waiting';
  };
  const label: Record<RowState, string> = { done: '완료', failed: '미완료', active: '조회 중', waiting: '대기' };
  const pct = total ? Math.round((received / total) * 100) : 0;

  return (
    <div className="card loading-card">
      <div className="loading-head">
        <span className="loading-title">
          {meta?.characterInfo.name ?? name} · {range === 'daily' ? '최근 30일' : '최근 12개월'} 조회 중
        </span>
        <span className="progress">
          <span className="mono">{received}/{total || '?'}</span>
          <span className="progress-bar"><span style={{ width: `${pct}%` }} /></span>
        </span>
      </div>
      {rows.length === 0 ? (
        <div className="muted" style={{ fontSize: 13 }}>캐릭터를 찾는 중<span className="dots" /></div>
      ) : (
        <ul className="loading-list">
          {rows.map((p) => {
            const state = stateOf(p);
            return (
              <li className={`loading-row ${state}`} key={p.date}>
                <span className="mono">{formatLongDate(p.date)}</span>
                <span>{label[state]}{state === 'active' && <span className="dots" />}</span>
              </li>
            );
          })}
        </ul>
      )}
      {meta?.truncated && meta.truncatedFrom && (
        <div className="muted" style={{ fontSize: 12 }}>{formatLongDate(meta.truncatedFrom)} 이전은 캐릭터가 없어 조회하지 않습니다.</div>
      )}
      <div className="muted" style={{ fontSize: 12 }}>지점 하나마다 넥슨 문서 15개를 받습니다. 처음 조회는 시간이 걸릴 수 있어요.</div>
    </div>
  );
}
