import { Fragment, useState } from 'react';
import type { StatDelta } from '../api/types';
import { formatStatDelta } from '../lib/format';
import { statLabel } from '../lib/labels';

/** 오른 것 / 내린 것 / 그대로인 것. 비어 있는 줄은 그리지 않는다. */
const STAT_SIDES = [
  { key: 'up', label: '증가', match: (delta: number) => delta > 0 },
  { key: 'down', label: '감소', match: (delta: number) => delta < 0 },
  { key: 'flat', label: '유지', match: (delta: number) => delta === 0 },
];

const FRAGMENT = 'SOL_ERDA_FRAGMENT';

/**
 * 요약 줄의 스탯 묶음. 한 줄에 +와 -를 섞어 늘어놓으면 색만이 둘을 갈라서
 * 무엇이 깎였는지 세어 봐야 안다. 오른 쪽과 내린 쪽을 줄로 나눠 적는다.
 *
 * @param limit 한 쪽에 이만큼만 보이고 나머지는 접는다. 1년 구간처럼 스탯이 스무 개씩
 *              나오면 이 블럭 하나가 화면 절반을 먹는데, 정작 눈에 걸려야 할 것은 앞의
 *              몇 개다(순서가 곧 중요도다). 안 주면 접지 않는다.
 */
export function StatChips({ deltas, limit }: { deltas: StatDelta[]; limit?: number }) {
  const [open, setOpen] = useState(false);

  const sides = STAT_SIDES
    .map((side) => ({ side, items: deltas.filter((delta) => side.match(delta.delta)) }))
    .filter(({ items }) => items.length > 0);

  /*
   * 조각은 잘라내지 않는다. 정렬에서 맨 뒤라 그냥 자르면 제일 먼저 사라지는데,
   * "이 구간에 6차로 얼마를 부었나"는 접어 둘 값이 아니다.
   */
  const shownOf = (items: StatDelta[]) => {
    if (limit == null || open) return items;
    const fragment = items.filter((d) => d.statName === FRAGMENT);
    const rest = items.filter((d) => d.statName !== FRAGMENT);
    return [...rest.slice(0, limit), ...fragment];
  };

  const hidden = limit == null || open
    ? 0
    : sides.reduce((sum, { items }) => sum + (items.length - shownOf(items).length), 0);

  return (
    <div className="stat-split">
      {sides.map(({ side, items }) => (
        <Fragment key={side.key}>
          <span className={`stat-side ${side.key}`}>{side.label}</span>
          <div className="stat-chips">
            {/* 조각은 전투력 스탯이 아니다. 차트의 조각 선과 같은 색으로 두어 같은 것임을 알린다. */}
            {shownOf(items).map((delta) => (
              <span
                className={`stat-chip ${delta.statName === FRAGMENT ? 'fragment' : side.key}`}
                key={delta.statName}
              >
                <span className="stat-chip-name">{statLabel(delta.statName)}</span>
                <span className="stat-chip-value">{formatStatDelta(delta.statName, delta.delta)}</span>
              </span>
            ))}
          </div>
        </Fragment>
      ))}

      {(hidden > 0 || open) && limit != null && (
        <button type="button" className="stat-more" onClick={() => setOpen((v) => !v)}>
          {open ? '접기' : `+${hidden}개 더`}
        </button>
      )}
    </div>
  );
}
