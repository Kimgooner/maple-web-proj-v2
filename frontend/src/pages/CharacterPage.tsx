import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchDetail } from '../api/detail';
import { fetchLevelBands, type LevelBandWeek } from '../api/levelBands';
import { fetchRepairedHistory } from '../api/repair';
import type { DetailResponse, HistoryRange } from '../api/types';
import { ChartLoading } from '../components/ChartLoading';
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

  const [history, replacePoints] = useHistory(name, range, retry);
  /*
   * 로딩 판은 조금 늦게 띄운다. 캐시가 도는 구간은 몇 십 ms 만에 끝나는데, 그때마다
   * 판이 번쩍했다 사라지면 30일 ↔ 12개월을 오갈 때 화면이 계속 깜빡인다.
   * 이 시간 안에 끝나면 사용자는 로딩을 본 적이 없게 된다.
   */
  const [slowLoad, setSlowLoad] = useState(false);
  /** 기다림이 길어졌는가. 이때부터 몇 명이 함께 조회 중인지도 같이 적는다. */
  const [longWait, setLongWait] = useState(false);
  /** 프리셋을 되돌려 다시 계산했는가. 눌러 고친 뒤에는 버튼 대신 결과를 알린다. */
  const [repair, setRepair] = useState<'idle' | 'working' | 'done' | 'failed'>('idle');
  /** 되돌린 뒤 못 박은 장비 프리셋 번호. 변경 내역도 이 번호로 맞춰 비교해야 한다. */
  const [fixedPreset, setFixedPreset] = useState<number | null>(null);
  const [anchor, setAnchor] = useState<string | null>(null);
  const [interval, setInterval] = useState<Interval | null>(null);
  const [detail, setDetail] = useState<DetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  /** 첫 지점을 눌렀을 때처럼 구간을 만들 수 없는 경우의 안내 */
  const [pickHint, setPickHint] = useState<string | null>(null);

  useEffect(() => { if (name) pushRecent(name); }, [name]);

  /*
   * 새 조회가 시작될 때마다 시계를 처음부터 다시 잰다. 안 그러면 앞선 조회에서 켜진
   * 플래그가 그대로 넘어와, 캐시로 금방 끝나는 전환에서도 판이 한 번 번쩍인다.
   */
  useEffect(() => {
    setSlowLoad(false);
    setLongWait(false);
    if (history.status !== 'loading') return;
    const slow = window.setTimeout(() => setSlowLoad(true), 400);
    const long = window.setTimeout(() => setLongWait(true), 4000);
    return () => { window.clearTimeout(slow); window.clearTimeout(long); };
  }, [history.status, range, retry, name]);

  // 캐릭터와 무관한 통계라 한 번만 받는다. 실패해도 빈 배열이라 차트는 그대로 뜬다.
  useEffect(() => {
    const controller = new AbortController();
    fetchLevelBands(controller.signal).then(setBands);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    setAnchor(null); setInterval(null); setDetail(null); setDetailError(null); setPickHint(null);
    setRepair('idle'); setFixedPreset(null);
  }, [name, range, retry]);

  /*
   * 보스 프리셋을 고르는 점수가 같아 하루만 다른 번호가 뽑히면, 캐릭터는 아무것도 안 했는데
   * 그래프에 골짜기가 생긴다. 서버가 이 구간에서 제일 많이 쓴 번호로 그날만 다시 계산한다.
   */
  const fixPreset = () => {
    setRepair('working');
    fetchRepairedHistory(name, range)
      .then((points) => {
        replacePoints(points);
        setFixedPreset(points.find((point) => point.itemPreset != null)?.itemPreset ?? null);
        setRepair('done');
        setInterval(null);
      })
      .catch(() => setRepair('failed'));
  };

  useEffect(() => {
    if (history.status === 'done' && interval === null) setInterval(defaultInterval(history.points));
  }, [history.status, history.points, interval]);

  useEffect(() => {
    const ocid = history.meta?.ocid;
    if (!interval || !ocid) return;
    const controller = new AbortController();
    setDetailLoading(true);
    setDetailError(null);
    fetchDetail(ocid, interval.previousDate, interval.currentDate, controller.signal, fixedPreset)
      .then(setDetail)
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setDetailError(err instanceof Error ? err.message : '변경 내역을 불러오지 못했습니다.');
      })
      .finally(() => { if (!controller.signal.aborted) setDetailLoading(false); });
    return () => controller.abort();
  }, [interval, history.meta?.ocid, detailRetry, fixedPreset]);

  /*
   * 조회가 안 되는 이유는 둘이고, 사용자가 할 일이 다르다. 하나는 철자를 고치는 것이고
   * 다른 하나는 레벨을 올려 다시 오는 것이다. 한 화면으로 뭉뚱그리면 260 미만 캐릭터로
   * 조회한 사람이 자기 닉네임을 계속 다시 쳐 보게 된다.
   */
  const notFound = history.errorCode === 'NOT_FOUND';
  const tooLow = history.errorCode === 'TOO_LOW';
  const failed = !notFound && !tooLow && history.status === 'error' && history.error;
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

        {notFound || tooLow ? (
          <section className="panel missing">
            <div>
              <h1>{name}</h1>
              {notFound ? (
                <p>그런 이름의 캐릭터가 없습니다. 철자를 확인해 주세요.</p>
              ) : (
                <>
                  <p>{history.error}</p>
                  <p className="muted">
                    전투력 계산이 4차 전직을 기준으로 짜여 있어, 그 아래 레벨은 값을 믿을 수
                    없습니다. 조용히 틀린 값을 보여주는 대신 여기서 멈춥니다.
                  </p>
                </>
              )}
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
              <CharacterHeader
                info={info}
                name={name}
                points={history.points}
                loading={streaming}
                preset={history.meta?.preset ?? null}
                range={range}
              />
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
                </div>

                {streaming && (slowLoad || history.points.length === 0) ? (
                  <ChartLoading
                    received={history.received}
                    total={history.total}
                    concurrent={longWait ? history.concurrent : 0}
                  />
                ) : history.points.length === 0 ? (
                  <div className="chart-empty">불러올 성장 기록이 없어요.</div>
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
                {/*
                  안내 문구와 되돌리기 버튼을 한 줄에 둔다. 버튼만 따로 아래에 두면
                  차트 오른쪽 아래가 두 줄로 늘어져 여백만 먹는다.
                */}
                <div className="chart-bottom">
                  <p className="selection-hint" role="status">{hint}</p>
                  {/*
                    전투력이 이유 없이 뚝 떨어져 보일 때 누른다. 프리셋이 갈린 날만 되돌린다.
                    다 받은 뒤에만 띄운다 - 받는 중에는 골짜기인지 아직 안 온 지점인지 모른다.
                  */}
                  {selectable && (
                    <div className="chart-repair">
                      {repair === 'done' ? (
                        <span className="repair-done">프리셋을 맞춰 다시 계산했어요</span>
                      ) : (
                        <>
                          <span
                            className="repair-help"
                            tabIndex={0}
                            role="note"
                            data-tip="잘못 고른 프리셋이 있는지 확인하고 전투력을 다시 계산합니다"
                            aria-label="잘못 고른 프리셋이 있는지 확인하고 전투력을 다시 계산합니다"
                          >?</span>
                          <button
                            type="button"
                            className="repair-btn"
                            disabled={repair === 'working'}
                            onClick={fixPreset}
                          >
                            {repair === 'working' ? '다시 계산하는 중…'
                              : repair === 'failed' ? '다시 시도'
                                : '전투력이 이상해요!'}
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              </section>

              <IntervalPanel
                interval={interval}
                points={history.points}
                summary={detail?.changeSummary ?? null}
                info={info}
                loading={streaming}
              />
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
