import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchDetail } from '../api/detail';
import type { DetailResponse, HistoryRange } from '../api/types';
import { CharacterHeader } from '../components/CharacterHeader';
import { DeltaPanel } from '../components/DeltaPanel';
import { LoadingOverlay } from '../components/LoadingOverlay';
import { NexonNotice } from '../components/NexonNotice';
import { TopBar } from '../components/TopBar';
import { TrendChart, type SelectMode } from '../components/TrendChart';
import { formatLongDate } from '../lib/format';
import { latestLoadedPoint } from '../lib/history';
import { pushRecent } from '../lib/recent';
import { useHistory } from '../lib/useHistory';
import { changedDates, defaultInterval, intervalEndingAt, pickPoint, type Interval } from '../lib/selection';

const RANGE_LABEL: Record<HistoryRange, string> = { daily: '월간 (30일)', monthly: '연간 (12개월)' };
const MODE_LABEL: Record<SelectMode, string> = { pin: '변화 지점', range: '구간 비교' };

export function CharacterPage() {
  const { name = '' } = useParams();
  const [range, setRange] = useState<HistoryRange>('daily');
  const [mode, setMode] = useState<SelectMode>('pin');
  const [showApi, setShowApi] = useState(true);
  const [retry, setRetry] = useState(0);
  // 두 구간을 한 번에 조회해 두고 토글은 보기만 바꾼다.
  const daily = useHistory(name, 'daily', retry);
  const monthly = useHistory(name, 'monthly', retry);
  const history = range === 'daily' ? daily : monthly;
  const [anchor, setAnchor] = useState<string | null>(null);
  const [interval, setInterval] = useState<Interval | null>(null);
  const [detail, setDetail] = useState<DetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    if (name) pushRecent(name);
  }, [name]);

  // 이름·구간·재시도가 바뀌면 선택과 변경 내역을 비운다.
  useEffect(() => {
    setAnchor(null);
    setInterval(null);
    setDetail(null);
    setDetailError(null);
  }, [name, range, retry]);

  // 로드가 끝나면 기본 구간(마지막으로 변한 지점과 직전 지점)을 고른다.
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
  const switchMode = (next: SelectMode) => {
    setMode(next);
    setAnchor(null);
  };

  const info = history.meta?.characterInfo ?? null;
  const latest = latestLoadedPoint(history.points);
  const failed = history.status === 'error' && history.error;
  const anyLoading = daily.status === 'loading' || monthly.status === 'loading';
  const loading = !failed && history.status !== 'done';
  const loaded = history.points.filter((p) => p.combatPower != null || p.apiCombatPower != null).length;
  const pinCount = changedDates(history.points).length;
  const hint = mode === 'pin'
    ? pinCount > 0
      ? '차트의 변화 지점을 누르면 직전 지점과 비교합니다'
      : '이 구간에는 전투력이 변한 지점이 없습니다'
    : anchor
      ? `${formatLongDate(anchor)} 을 시작점으로 잡았습니다. 끝점을 고르세요`
      : '차트에서 두 점을 차례로 고르면 그 사이를 비교합니다';

  const rangeToggle = (
    <div className="toggle">
      {(['daily', 'monthly'] as HistoryRange[]).map((r) => (
        <button type="button" key={r} className={range === r ? 'on' : ''} onClick={() => setRange(r)}>{RANGE_LABEL[r]}</button>
      ))}
    </div>
  );

  return (
    <div>
      <TopBar withSearch />
      <div className="page">
        {failed && (
          <div className="card error-box">
            <span>{history.error}</span>
            <div style={{ display: 'flex', gap: 10 }}>
              <button type="button" className="chip-btn" onClick={() => setRetry((r) => r + 1)}>다시 시도</button>
              <Link to="/" className="chip-btn">다른 캐릭터</Link>
            </div>
          </div>
        )}

        {anyLoading && (
          <LoadingOverlay name={name} states={{ daily, monthly }} labels={RANGE_LABEL} />
        )}

        {loading && !anyLoading && (
          <div className="skeleton" style={{ height: 134 }} />
        )}

        {!failed && !loading && info && (
          <>
            <CharacterHeader info={info} latest={latest} />

            <div className="card">
              <div className="section-head">
                <div className="section-title">
                  전투력 추이
                  <small>{hint}</small>
                </div>
                <div className="controls">
                  <div className="legend">
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><span className="swatch" />계산값</span>
                    <button type="button" className={showApi ? '' : 'off'} onClick={() => setShowApi((v) => !v)}><span className="swatch-dash" />넥슨 값</button>
                  </div>
                  <div className="toggle">
                    {(['pin', 'range'] as SelectMode[]).map((m) => (
                      <button type="button" key={m} className={mode === m ? 'on' : ''} onClick={() => switchMode(m)}>{MODE_LABEL[m]}</button>
                    ))}
                  </div>
                  {rangeToggle}
                </div>
              </div>

              {loaded === 0 ? (
                <div className="empty">이 구간에는 캐릭터 데이터가 없습니다.</div>
              ) : (
                <TrendChart points={history.points} range={range} showApi={showApi} mode={mode} interval={interval} anchor={anchor} onPick={onPick} onPin={onPin} />
              )}

              <div className="chart-foot">
                <span>
                  {history.meta?.truncated && history.meta.truncatedFrom
                    ? `${formatLongDate(history.meta.truncatedFrom)} 이전은 캐릭터가 없어 잘렸습니다.`
                    : ' '}
                </span>
                <span className="mono">변화 지점 {pinCount}개</span>
              </div>
            </div>

            <DeltaPanel interval={interval} points={history.points} detail={detail} loading={detailLoading} error={detailError} hint={hint} />
          </>
        )}
      </div>
      <NexonNotice />
    </div>
  );
}
