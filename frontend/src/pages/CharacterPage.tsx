import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchDetail } from '../api/detail';
import { fetchLevelBands, type LevelBandWeek } from '../api/levelBands';
import type { DetailResponse, HistoryRange } from '../api/types';
import { CharacterHeader } from '../components/CharacterHeader';
import { ChangesPanel } from '../components/ChangesPanel';
import { IntervalPanel } from '../components/IntervalPanel';
import { TopBar } from '../components/TopBar';
import { TrendChart } from '../components/TrendChart';
import { dateLabel } from '../lib/format';
import { pushRecent } from '../lib/recent';
import { useHistory } from '../lib/useHistory';
import { defaultInterval, intervalEndingAt, pickPoint, type Interval } from '../lib/selection';

const RANGE_LABEL: Record<HistoryRange, string> = { daily: '최근 30일', monthly: '최근 12개월' };

export function CharacterPage() {
  const { name = '' } = useParams();
  const [range, setRange] = useState<HistoryRange>('daily');
  const [compare, setCompare] = useState(false);
  const [showFragments, setShowFragments] = useState(true);
  const [showMedian, setShowMedian] = useState(true);
  const [bands, setBands] = useState<LevelBandWeek[]>([]);
  const [retry, setRetry] = useState(0);
  const [detailRetry, setDetailRetry] = useState(0);

  const history = useHistory(name, range, retry);
  const [anchor, setAnchor] = useState<string | null>(null);
  const [interval, setInterval] = useState<Interval | null>(null);
  const [detail, setDetail] = useState<DetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  /** 첫 지점을 눌렀을 때처럼 구간을 만들 수 없는 경우의 안내 */
  const [pickHint, setPickHint] = useState<string | null>(null);

  useEffect(() => { if (name) pushRecent(name); }, [name]);

  // 캐릭터와 무관한 통계라 한 번만 받는다. 실패해도 빈 배열이라 차트는 그대로 뜬다.
  useEffect(() => {
    const controller = new AbortController();
    fetchLevelBands(controller.signal).then(setBands);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    setAnchor(null); setInterval(null); setDetail(null); setDetailError(null); setPickHint(null);
  }, [name, range, retry]);

  useEffect(() => {
    if (history.status === 'done' && interval === null) setInterval(defaultInterval(history.points));
  }, [history.status, history.points, interval]);

  useEffect(() => {
    const ocid = history.meta?.ocid;
    if (!interval || !ocid) return;
    const controller = new AbortController();
    setDetailLoading(true);
    setDetailError(null);
    fetchDetail(ocid, interval.previousDate, interval.currentDate, controller.signal)
      .then(setDetail)
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setDetailError(err instanceof Error ? err.message : '변경 내역을 불러오지 못했습니다.');
      })
      .finally(() => { if (!controller.signal.aborted) setDetailLoading(false); });
    return () => controller.abort();
  }, [interval, history.meta?.ocid, detailRetry]);

  const notFound = history.errorCode === 'NOT_FOUND';
  const failed = !notFound && history.status === 'error' && history.error;
  const streaming = history.status === 'loading';
  const info = history.meta?.characterInfo ?? null;
  const selectable = history.status === 'done'
    && history.points.filter((point) => point.combatPower != null).length >= 2;

  /** 비교 모드면 두 점을 차례로, 아니면 고른 지점과 그 직전 지점을 잡는다. */
  const pick = (date: string) => {
    if (!selectable) return;
    setPickHint(null);
    if (compare) {
      const next = pickPoint(anchor, date);
      setAnchor(next.anchor);
      if (next.interval) setInterval(next.interval);
      return;
    }
    setAnchor(null);
    const next = intervalEndingAt(history.points, date);
    if (next) setInterval(next);
    else setPickHint('첫 지점에는 이전 기록이 없어요. 다음 지점을 선택해 주세요.');
  };

  const hint = pickHint ?? (!selectable
    ? '지점을 모두 받으면 두 시점을 고를 수 있어요.'
    : compare
      ? anchor
        ? `${dateLabel(anchor)} 선택 · 비교할 다른 날짜를 선택하세요.`
        : '차트에서 두 지점을 차례로 선택하세요.'
      : '차트의 지점을 선택하면 직전 지점과 비교합니다.');

  return (
    <>
      <TopBar />
      <main className="page">
        <div className="breadcrumb">
          <span aria-hidden="true">⌂</span><span>/</span>캐릭터 분석
        </div>

        {notFound ? (
          <section className="panel missing">
            <div>
              <h1>{name}</h1>
              <p>그런 이름의 캐릭터가 없습니다. 철자를 확인해 주세요.</p>
            </div>
            <Link to="/" className="outline">처음으로</Link>
          </section>
        ) : failed ? (
          <div className="notice">
            {history.error}
            <button type="button" className="outline retry" onClick={() => setRetry((v) => v + 1)}>다시 시도</button>
          </div>
        ) : (
          <>
            {info ? (
              <CharacterHeader info={info} name={name} points={history.points} loading={streaming} />
            ) : (
              <div className="skeleton" style={{ height: 168, marginBottom: 22 }} />
            )}

            <div className="analysis-grid">
              <section className="chart-panel panel" aria-labelledby="chart-title">
                <div className="section-heading">
                  <div>
                    <h2 id="chart-title">전투력 추이</h2>
                    <p className="muted">변화 지점을 선택해 변경 내역을 확인하세요</p>
                  </div>
                  <div className="segmented" aria-label="조회 기간">
                    {(['daily', 'monthly'] as HistoryRange[]).map((value) => (
                      <button
                        type="button" key={value}
                        aria-pressed={range === value}
                        onClick={() => setRange(value)}
                      >{RANGE_LABEL[value]}</button>
                    ))}
                  </div>
                </div>

                <div className="legend">
                  <span><i />실전 전투력</span>
                  <button type="button" onClick={() => setShowFragments((v) => !v)} style={{ opacity: showFragments ? 1 : 0.45 }}>
                    <i className="line-fragment" />솔 에르다 조각
                  </button>
                  {bands.length > 0 && (
                    <button type="button" onClick={() => setShowMedian((v) => !v)} style={{ opacity: showMedian ? 1 : 0.45 }}>
                      <i className="line-median" />같은 레벨 중앙값
                    </button>
                  )}
                  <span className="muted" style={{ marginLeft: 'auto', fontSize: 10 }}>
                    {streaming ? `${history.received} / ${history.total || '…'} 지점` : ''}
                  </span>
                </div>

                {history.points.length === 0 ? (
                  <div className="chart-empty">캐릭터의 성장 기록을 불러오고 있어요…</div>
                ) : (
                  <TrendChart
                    points={history.points} range={range} showFragments={showFragments}
                    bands={bands} showMedian={showMedian}
                    interval={interval} anchor={anchor} interactive={selectable} onPick={pick}
                  />
                )}

                <div className="chart-footer">
                  <label className="switch-label">
                    <input
                      type="checkbox" checked={compare} disabled={!selectable}
                      onChange={(event) => { setCompare(event.target.checked); setAnchor(null); }}
                    />
                    <span className="switch" aria-hidden="true" />두 시점 비교
                  </label>
                  <span className="muted number">
                    {history.points.length
                      ? `${dateLabel(history.points[0].date)} — ${dateLabel(history.points[history.points.length - 1].date)}`
                      : ''}
                  </span>
                </div>
                <p className="selection-hint" role="status">{hint}</p>
              </section>

              <IntervalPanel interval={interval} points={history.points} summary={detail?.changeSummary ?? null} />
            </div>

            <ChangesPanel
              interval={interval}
              info={info}
              summary={detail?.changeSummary ?? null}
              loading={detailLoading}
              error={detailError}
              onRetry={() => setDetailRetry((v) => v + 1)}
            />
          </>
        )}

        <footer>
          <span>Data based on NEXON Open API</span>
          <span>MapleDelta <span className="footer-dot">·</span> 성장의 순간을 기록하다</span>
        </footer>
      </main>
    </>
  );
}
