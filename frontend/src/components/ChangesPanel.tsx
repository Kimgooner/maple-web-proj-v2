import { useEffect, useState } from 'react';
import type { CharacterInfo, ChangeSummary } from '../api/types';
import type { Interval } from '../lib/selection';
import { CATEGORIES, changeRows, type ChangeCategory } from '../lib/changes';
import { ChangeRow } from './ChangeRow';
import { dateLabel } from '../lib/format';

interface Props {
  interval: Interval | null;
  info: CharacterInfo | null;
  summary: ChangeSummary | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}

const FILTERS: [ChangeCategory | 'all', string][] = [['all', '전체'], ...CATEGORIES];

export function ChangesPanel({ interval, info, summary, loading, error, onRetry }: Props) {
  const [filter, setFilter] = useState<ChangeCategory | 'all'>('all');
  /** 펼친 줄. 하나를 펼치면 앞서 펼친 것은 닫힌다. */
  const [openKey, setOpenKey] = useState<string | null>(null);
  const rows = changeRows(summary);
  const countOf = (key: ChangeCategory | 'all') =>
    key === 'all' ? rows.length : rows.filter((row) => row.category === key).length;

  /*
   * 비어 있는 탭은 숨긴다. 열두 개를 늘어놓으면 정작 뭐가 바뀌었는지가 0 사이에 묻힌다.
   * 아직 안 받았을 때는 모두 두어 화면이 뒤늦게 튀지 않게 한다.
   */
  const shownFilters = summary ? FILTERS.filter(([key]) => countOf(key) > 0) : FILTERS;

  // 구간이 바뀌면 줄이 통째로 갈리므로 펼친 것을 닫는다.
  useEffect(() => { setOpenKey(null); }, [interval, summary]);
  // 고르고 있던 분류가 이번 구간에 없으면 전체로 돌린다. 빈 화면만 남는 것을 막는다.
  useEffect(() => {
    if (summary && filter !== 'all' && countOf(filter) === 0) setFilter('all');
  });
  const shown = rows.filter((row) => filter === 'all' || row.category === filter);

  const notice = error
    ? error
    : !interval
      ? '두 시점을 선택하면 장비와 스킬의 변경 내역을 보여드려요.'
      : loading || !summary
        ? '변경 내역을 불러오고 있어요…'
        : shown.length === 0
          ? '이 구간에 확인된 변경 내역이 없어요.'
          : '';

  return (
    <section className="changes panel" aria-labelledby="changes-title">
      <div className="section-heading">
        <h2 id="changes-title">변경 사항</h2>
        <span className="muted number">
          {interval ? `${dateLabel(interval.previousDate)} → ${dateLabel(interval.currentDate)}` : ''}
        </span>
      </div>

      <div className="filters" aria-label="변경 내역 분류">
        {shownFilters.map(([key, title]) => (
          <button
            type="button"
            key={key}
            aria-pressed={filter === key}
            disabled={!summary}
            onClick={() => setFilter(key)}
          >
            {title}
            <span className="filter-count">{summary ? countOf(key) : '—'}</span>
          </button>
        ))}
      </div>

      <div className="detail-notice" role="status" aria-live="polite">{notice}</div>
      {error && <button type="button" className="outline retry" onClick={onRetry}>변경 내역 다시 시도</button>}

      {shown.length > 0 && (
        <div aria-busy={loading}>
          <div className="table-head" aria-hidden="true">
            {['변경 항목', '이전', ''].map((text, i) => <span key={i}>{text}</span>)}
          </div>
          {shown.map((row) => (
            <ChangeRow
              row={row}
              info={info}
              key={row.key}
              open={openKey === row.key}
              onToggle={() => setOpenKey((current) => (current === row.key ? null : row.key))}
            />
          ))}
        </div>
      )}
    </section>
  );
}
