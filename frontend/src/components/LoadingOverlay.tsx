import type { HistoryRange } from '../api/types';
import type { HistoryState } from '../lib/history';

interface Props {
  name: string;
  states: Record<HistoryRange, HistoryState>;
  labels: Record<HistoryRange, string>;
}

function percent(state: HistoryState): number {
  if (state.status === 'done') return 100;
  if (!state.total) return 0;
  return Math.min(100, Math.round((state.received / state.total) * 100));
}

/** 두 구간을 동시에 조회하는 동안 화면 위에 띄우는 팝업. 둘 다 끝나면 호출부가 닫는다. */
export function LoadingOverlay({ name, states, labels }: Props) {
  const info = states.daily.meta?.characterInfo ?? states.monthly.meta?.characterInfo ?? null;
  return (
    <div className="overlay" role="dialog" aria-live="polite" aria-label="조회 진행">
      <div className="card overlay-card">
        <div className="overlay-head">
          {info?.image && <img className="overlay-avatar" src={info.image} alt="" />}
          <div>
            <div className="overlay-title">{info?.name ?? name}</div>
            <div className="muted" style={{ fontSize: 13 }}>
              {info ? `${info.className} · Lv. ${info.level ?? '-'}` : '캐릭터를 찾는 중'}<span className="dots" />
            </div>
          </div>
        </div>
        {(['daily', 'monthly'] as HistoryRange[]).map((range) => {
          const state = states[range];
          const pct = percent(state);
          const done = state.status === 'done';
          const failed = state.status === 'error' && !!state.error;
          return (
            <div className={`overlay-row ${done ? 'done' : failed ? 'failed' : ''}`} key={range}>
              <div className="overlay-row-head">
                <span>{labels[range]} {done ? '조회 완료' : failed ? '조회 실패' : '조회 중'}{!done && !failed && <span className="dots" />}</span>
                <span className="mono">{failed ? '—' : `${pct}%`}</span>
              </div>
              <div className="progress-bar wide"><span style={{ width: `${pct}%` }} /></div>
              {failed && <div className="down" style={{ fontSize: 12 }}>{state.error}</div>}
            </div>
          );
        })}
        <div className="muted" style={{ fontSize: 12 }}>지점 하나마다 넥슨 문서 15개를 받습니다. 처음 조회는 시간이 걸릴 수 있어요.</div>
      </div>
    </div>
  );
}
