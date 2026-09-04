import { useEffect, useReducer, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchDetail } from '../api/detail';
import { openHistoryStream } from '../api/historyStream';
import type { DetailResponse, HistoryRange } from '../api/types';
import { CharacterHeader } from '../components/CharacterHeader';
import { DeltaPanel } from '../components/DeltaPanel';
import { NexonNotice } from '../components/NexonNotice';
import { TopBar } from '../components/TopBar';
import { TrendChart } from '../components/TrendChart';
import { formatLongDate } from '../lib/format';
import { applyHistoryEvent, latestLoadedPoint, loadingHistoryState } from '../lib/history';
import { pushRecent } from '../lib/recent';
import { defaultInterval, intervalEndingAt, pickPoint, type Interval } from '../lib/selection';

const RANGE_LABEL: Record<HistoryRange, string> = { daily: '일간 30일', monthly: '월간 12개월' };

export function CharacterPage() {
  const { name = '' } = useParams();
  const [range, setRange] = useState<HistoryRange>('daily');
  const [showApi, setShowApi] = useState(true);
  const [retry, setRetry] = useState(0);
  const [history, dispatch] = useReducer(applyHistoryEvent, undefined, loadingHistoryState);
  const [anchor, setAnchor] = useState<string | null>(null);
  const [interval, setInterval] = useState<Interval | null>(null);
  const [detail, setDetail] = useState<DetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // 추이 스트림. 이름·구간이 바뀌면 새로 연다.
  useEffect(() => {
    if (!name) return;
    pushRecent(name);
    setAnchor(null);
    setInterval(null);
    setDetail(null);
    setDetailError(null);
    dispatch({ type: 'error', data: { message: '' } }); // 이전 오류 지우기용. 바로 아래 meta 가 덮어쓴다
    const close = openHistoryStream(name, range, dispatch);
    return close;
  }, [name, range, retry]);

  // 로드가 끝나면 기본 구간을 고른다.
  useEffect(() => {
    if (history.status === 'done' && interval === null) {
      setInterval(defaultInterval(history.points));
    }
  }, [history.status, history.points, interval]);

  // 구간이 바뀌면 변경 내역을 가져온다.
  useEffect(() => {
    const ocid = history.meta?.ocid;
    if (!interval || !ocid) return;
    const controller = new AbortController();
    setDetailLoading(true);
    setDetailError(null);
    fetchDetail(ocid, interval.previousDate, interval.currentDate, controller.signal)
      .then((res) => { setDetail(res); })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setDetailError(err instanceof Error ? err.message : '변경 내역을 불러오지 못했습니다.');
      })
      .finally(() => { if (!controller.signal.aborted) setDetailLoading(false); });
    return () => controller.abort();
  }, [interval, history.meta?.ocid]);

  const onPick = (date: string) => {
    const next = pickPoint(anchor, date);
    setAnchor(next.anchor);
    if (next.interval) setInterval(next.interval);
  };
  const onPin = (date: string) => {
    setAnchor(null);
    const next = intervalEndingAt(history.points, date);
    if (next) setInterval(next);
  };

  const info = history.meta?.characterInfo ?? null;
  const latest = latestLoadedPoint(history.points);
  const failed = history.status === 'error' && history.error;
  const loaded = history.points.filter((p) => p.combatPower != null || p.apiCombatPower != null).length;

  return (
    <div>
      <TopBar withSearch />
      <div className="page">
        {failed ? (
          <div className="card error-box">
            <span>{history.error}</span>
            <div style={{ display: 'flex', gap: 10 }}>
              <button type="button" className="chip-btn" onClick={() => setRetry((r) => r + 1)}>다시 시도</button>
              <Link to="/" className="chip-btn">다른 캐릭터</Link>
            </div>
          </div>
        ) : info ? (
          <CharacterHeader info={info} latest={latest} />
        ) : (
          <div className="skeleton" style={{ height: 134 }} />
        )}

        {!failed && (
          <div className="card">
            <div className="section-head">
              <div className="section-title">
                전투력 추이
                <small>변화 핀을 누르면 그 구간의 변경 내역을 봅니다</small>
              </div>
              <div className="controls">
                <div className="legend">
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><span className="swatch" />계산값</span>
                  <button type="button" className={showApi ? '' : 'off'} onClick={() => setShowApi((v) => !v)}><span className="swatch-dash" />넥슨 값</button>
                </div>
                <div className="toggle">
                  {(['daily', 'monthly'] as HistoryRange[]).map((r) => (
                    <button type="button" key={r} className={range === r ? 'on' : ''} onClick={() => setRange(r)}>{RANGE_LABEL[r]}</button>
                  ))}
                </div>
              </div>
            </div>

            {history.status === 'done' && loaded === 0 ? (
              <div className="empty">이 구간에는 캐릭터 데이터가 없습니다.</div>
            ) : (
              <TrendChart points={history.points} range={range} showApi={showApi} interval={interval} anchor={anchor} onPick={onPick} onPin={onPin} />
            )}

            <div className="chart-foot">
              <span>
                {anchor
                  ? `${formatLongDate(anchor)} 을 시작점으로 잡았습니다. 끝점을 고르세요.`
                  : history.meta?.truncated && history.meta.truncatedFrom
                    ? `${formatLongDate(history.meta.truncatedFrom)} 이전은 캐릭터가 없어 잘렸습니다.`
                    : ' '}
              </span>
              {history.status === 'loading' && (
                <span className="progress">
                  <span className="mono">{history.received}/{history.total || '?'}</span>
                  <span className="progress-bar"><span style={{ width: history.total ? `${(history.received / history.total) * 100}%` : '0%' }} /></span>
                </span>
              )}
            </div>
          </div>
        )}

        {!failed && (
          <DeltaPanel interval={interval} points={history.points} detail={detail} loading={detailLoading} error={detailError} />
        )}
      </div>
      <NexonNotice />
    </div>
  );
}
