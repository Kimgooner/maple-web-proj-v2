import { useEffect, useState } from 'react';
import type { ChangeSummary } from '../api/types';
import type { Interval } from '../lib/selection';
import { changeRows, type ChangeCategory } from '../lib/changes';
import { ChangeRow } from './ChangeRow';
import { dateLabel } from '../lib/format';

interface Props {
  interval: Interval | null;
  summary: ChangeSummary | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}

const FILTERS: [ChangeCategory | 'all', string][] = [
  ['all', '전체'], ['items', '장비'], ['core', '핵심'], ['other', '캐시 · 펫'],
];

export function ChangesPanel({ interval, summary, loading, error, onRetry }: Props) {
  const [filter, setFilter] = useState<ChangeCategory | 'all'>('all');
  /** 펼친 줄. 하나를 펼치면 앞서 펼친 것은 닫힌다. */
  const [openKey, setOpenKey] = useState<string | null>(null);
  const rows = changeRows(summary);

  // 구간이 바뀌면 줄이 통째로 갈리므로 펼친 것을 닫는다.
  useEffect(() => { setOpenKey(null); }, [interval, summary]);
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
        <h2 id="changes-title">무엇이 바뀌었나요?</h2>
        <span className="muted number">
          {interval ? `${dateLabel(interval.previousDate)} → ${dateLabel(interval.currentDate)}` : ''}
        </span>
      </div>

      <div className="filters" aria-label="변경 내역 분류">
        {FILTERS.map(([key, title]) => (
          <button
            type="button"
            key={key}
            aria-pressed={filter === key}
            disabled={!summary}
            onClick={() => setFilter(key)}
          >
            {title}
            <span className="filter-count">
              {summary ? (key === 'all' ? rows.length : rows.filter((row) => row.category === key).length) : '—'}
            </span>
          </button>
        ))}
      </div>

      <div className="detail-notice" role="status" aria-live="polite">{notice}</div>
      {error && <button type="button" className="outline retry" onClick={onRetry}>변경 내역 다시 시도</button>}

      {shown.length > 0 && (
        <div aria-busy={loading}>
          <div className="table-head" aria-hidden="true">
            {['변경 항목', '이전', '이후', '주요 변화', ''].map((text, i) => <span key={i}>{text}</span>)}
          </div>
          {shown.map((row) => (
            <ChangeRow
              row={row}
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
