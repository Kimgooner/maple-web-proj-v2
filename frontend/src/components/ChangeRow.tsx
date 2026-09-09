import { Fragment } from 'react';
import type { CharacterInfo, EntryChange, ItemDetail, ItemStatLine, StatDelta } from '../api/types';
import type { ChangeRow as Row } from '../lib/changes';
import { formatNumber, formatStatDelta } from '../lib/format';
import { CHANGE_TYPE_LABELS, sourceLabel, statLabel } from '../lib/labels';
import { presentDeltas } from '../lib/stats';

/** 오른 것 / 내린 것 / 그대로인 것. 비어 있는 줄은 그리지 않는다. */
const STAT_SIDES = [
  { key: 'up', label: '증가', match: (delta: number) => delta > 0 },
  { key: 'down', label: '감소', match: (delta: number) => delta < 0 },
  { key: 'flat', label: '유지', match: (delta: number) => delta === 0 },
];

/**
 * 요약 줄의 스탯 묶음. 한 줄에 +와 -를 섞어 늘어놓으면 색만이 둘을 갈라서
 * 무엇이 깎였는지 세어 봐야 안다. 오른 쪽과 내린 쪽을 줄로 나눠 적는다.
 */
function StatChips({ deltas }: { deltas: StatDelta[] }) {
  return (
    <div className="stat-split">
      {STAT_SIDES.map((side) => ({ side, items: deltas.filter((delta) => side.match(delta.delta)) }))
        .filter(({ items }) => items.length > 0)
        .map(({ side, items }) => (
          <Fragment key={side.key}>
            <span className={`stat-side ${side.key}`}>{side.label}</span>
            <div className="stat-chips">
              {/* 조각은 전투력 스탯이 아니다. 차트의 조각 선과 같은 색으로 두어 같은 것임을 알린다. */}
              {items.map((delta) => (
                <span
                  className={`stat-chip ${delta.statName === 'SOL_ERDA_FRAGMENT' ? 'fragment' : side.key}`}
                  key={delta.statName}
                >
                  <span className="stat-chip-name">{statLabel(delta.statName)}</span>
                  <span className="stat-chip-value">{formatStatDelta(delta.statName, delta.delta)}</span>
                </span>
              ))}
            </div>
          </Fragment>
        ))}
    </div>
  );
}

/** 스탯 내역 한 조각. 색만으로는 어디서 온 값인지 모르니 이름을 달아 둔다. */
const PART_LABELS: Record<string, string> = {
  base: '기본', star: '스타포스', add: '추가옵션', scroll: '주문서',
};

function Part({ value, kind, suffix }: { value: number; kind: string; suffix: string }) {
  return (
    <span className={`stat-part ${kind}`} title={PART_LABELS[kind]}>
      {value > 0 ? `+${formatNumber(value)}` : formatNumber(value)}{suffix}
    </span>
  );
}

/**
 * 아이템 창의 스탯 한 줄. {@code STR +93  5 +79 +9} 처럼 총합 뒤에 내역을 붙인다.
 * 기본·추가옵션·주문서·스타포스를 색으로 갈라 어디서 온 값인지 바로 읽히게 한다.
 */
function StatLine({ line }: { line: ItemStatLine }) {
  const suffix = line.percent ? '%' : '';
  // 게임 아이템 창이 적는 차례. 실측: STR +85 (5 +79 +1) = 기본 · 스타포스 · 주문서.
  const parts: [number, string][] = [
    [line.base, 'base'], [line.starforce, 'star'], [line.add, 'add'], [line.scroll, 'scroll'],
  ];
  const shown = parts.filter(([value]) => value !== 0);
  /*
   * 기본값만으로 된 줄은 내역을 적어봐야 총합을 한 번 더 쓰는 것이다.
   * 반대로 추가옵션·주문서·스타포스에서 온 값은 몫이 하나뿐이어도 적는다 —
   * 그게 아니면 "올스탯 +6%" 가 어디서 붙은 건지 알 길이 없다.
   */
  const worthShowing = shown.length > 1 || shown.some(([, kind]) => kind !== 'base');
  return (
    <div className="item-stat">
      <span className="item-stat-name">{line.name}</span>
      <span className="item-stat-total">+{formatNumber(line.total)}{suffix}</span>
      {worthShowing && (
        <span className="item-stat-parts">
          ({shown.map(([value, kind], index) => (
            <Fragment key={kind}>{index > 0 && ' '}<Part value={value} kind={kind} suffix={suffix} /></Fragment>
          ))})
        </span>
      )}
    </div>
  );
}

/** 잠재 등급 색은 게임과 맞춘다. 한글을 클래스 이름으로 쓰지 않으려고 갈아 끼운다. */
const GRADE_SLUG: Record<string, string> = {
  레어: 'rare', 에픽: 'epic', 유니크: 'unique', 레전드리: 'legendary',
};

function PotentialBlock({ title, grade, lines }: { title: string; grade: string | null; lines: string[] }) {
  if (lines.length === 0) return null;
  return (
    <div className="item-potential">
      <div className="item-section">
        {title}
        {grade ? <span className={`potential-grade ${GRADE_SLUG[grade] ?? ''}`}>{grade}</span> : null}
      </div>
      {lines.map((line, index) => <div className="item-line" key={`${line}-${index}`}>{line}</div>)}
    </div>
  );
}

/**
 * 게임 아이템 창을 그대로 옮긴 카드. 교체된 두 장비를 나란히 놓고 읽는다.
 *
 * <p>증감으로 적으면 잠재 한 줄이 여러 스탯으로 흩어져 "무엇이 붙어 있었나"가 사라진다.
 * 스타포스·주문서 횟수처럼 증감으로는 아예 표현되지 않는 것도 여기서만 보인다.
 */
function ItemCard({ item, label, muted }: { item: ItemDetail | null; label: string; muted: boolean }) {
  const tag = (
    <div className="item-card-tags">
      <span className={`item-card-when ${muted ? 'before' : 'after'}`}>{label}</span>
      {/* 기간이 지난 장비는 그림도 이름도 그대로 남는다. 스탯만 빠진 것을 여기서 말한다. */}
      {item?.expired && <span className="item-card-expired">{item.expired}</span>}
    </div>
  );
  if (!item) {
    return (
      <div className={`item-card${muted ? ' faded' : ''}`}>
        <div className="item-card-head">{tag}</div>
        <div className="item-card-empty">✕ 없음</div>
      </div>
    );
  }
  return (
    <div className={`item-card${muted ? ' faded' : ''}`}>
      <div className="item-card-head">
        {item.icon && <img className="item-card-icon" src={item.icon} alt="" loading="lazy" />}
        {tag}
        <div className="item-card-title">
          <div className="item-card-name">
            {item.name ?? '이름 없는 장비'}
            {item.starForce ? <span className="starforce">{item.starForce}★</span> : null}
          </div>
          <div className="item-card-sub">
            {item.requiredLevel ? `요구 레벨 ${item.requiredLevel}` : ''}
            {item.scrollUpgrade ? ` · 주문서 ${item.scrollUpgrade}회` : ''}
          </div>
        </div>
      </div>

      {item.stats.length > 0 && (
        <div className="item-stats">{item.stats.map((line) => <StatLine line={line} key={line.name} />)}</div>
      )}
      {/* 칭호는 옵션 필드가 없고 설명문이 곧 스탯이다. 계산이 읽는 줄을 그대로 적는다. */}
      {item.stats.length === 0 && (item.descriptionLines ?? []).length > 0 && (
        <div className="item-stats">
          {item.descriptionLines.map((line, index) => (
            <div className="item-line" key={`${line}-${index}`}>{line}</div>
          ))}
        </div>
      )}

      {/*
        스탯도 잠재도 없는 장비(장식용 캐시 아이템 등). 빈 카드로 두면 못 받아온 것처럼 보인다.
        만료된 것은 만료 태그가 이미 비어 있는 이유를 말하므로 겹쳐 적지 않는다.
      */}
      {!item.expired && item.stats.length === 0 && (item.descriptionLines ?? []).length === 0
        && item.potentialLines.length === 0 && item.additionalPotentialLines.length === 0
        && item.exceptionalLines.length === 0 && (
        <div className="item-line muted">붙어 있는 효과가 없습니다</div>
      )}
      <PotentialBlock title="잠재능력" grade={item.potentialGrade} lines={item.potentialLines} />
      <PotentialBlock title="에디셔널 잠재능력" grade={item.additionalPotentialGrade} lines={item.additionalPotentialLines} />
      <PotentialBlock title="익셉셔널" grade={null} lines={item.exceptionalLines} />
    </div>
  );
}

/**
 * 앞머리를 블럭으로 떼어 낼 말. 지금은 헥사 코어의 주·부옵션뿐이다.
 * {@code SourceEntryExtractor} 가 붙이는 말과 짝이 맞아야 한다.
 */
const VALUE_ROLES = ['주옵션', '부옵션'];

/** 값의 앞머리를 블럭으로 떼고, 줄바꿈이 있으면 줄로 나눠 적는다. */
function ValueLines({ value }: { value: string | null }) {
  if (value === null) return <>—</>;
  const lines = value.split('\n');
  return (
    <>
      {lines.map((line, index) => {
        const role = VALUE_ROLES.find((name) => line.startsWith(`${name} `));
        return (
          <div className="value-line" key={`${line}-${index}`}>
            {role && <span className="value-role">{role}</span>}
            {role ? line.slice(role.length + 1) : line}
          </div>
        );
      })}
    </>
  );
}

function EntryDetail({ detail }: { detail: string }) {
  return (
    <div className="entry-detail">
      {detail.split('\n').map((line) => <div key={line}>{line}</div>)}
    </div>
  );
}

function EntryTable({ entries }: { entries: EntryChange[] }) {
  return (
    <table className="entry-table">
      <thead>
        <tr>{['항목', '이전', '이후'].map((title) => <th scope="col" key={title}>{title}</th>)}</tr>
      </thead>
      <tbody>
        {entries.map((entry) => (
          <tr key={entry.name}>
            <td>
              {/* 사라진 항목은 아이콘을 바래게 둔다. 훑어볼 때 없어진 줄이 먼저 눈에 들어온다. */}
              <span className={entry.current == null ? 'entry-label gone' : 'entry-label'}>
                {entry.icon && <img src={entry.icon} alt="" loading="lazy" />}
                <span className="entry-label-text">
                  {entry.name}
                  {/* 헥사 코어 종류. 이름이 긴 것이 있어 옆이 아니라 밑에 둔다. */}
                  {entry.badge && <span className="entry-badge">{entry.badge}</span>}
                </span>
              </span>
            </td>
            {/* 설명은 그 값이 살아 있는 쪽에 붙인다. 사라진 항목의 효과를
                빈 칸 아래에 늘어놓으면 아직 받는 것처럼 읽힌다. */}
            <td className="before">
              <ValueLines value={entry.previous} />
              {entry.detail && entry.current == null && <EntryDetail detail={entry.detail} />}
            </td>
            <td className="after">
              <ValueLines value={entry.current} />
              {entry.detail && entry.current != null && <EntryDetail detail={entry.detail} />}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** 슬롯 접두사를 뗀 부위명. 백엔드 키는 "장비 - 무기" 처럼 온다. */
function bareSlot(slot: string | null): string {
  if (!slot) return '';
  const at = slot.lastIndexOf(' - ');
  return at >= 0 ? slot.slice(at + 3) : slot;
}

/**
 * 변경 내역 한 줄. 접혀 있고 누르면 펼쳐진다.
 *
 * <p>이전 → 이후 칸은 <b>교체</b>일 때만 쓴다. 옵션 변경·장착·해제는 무엇이 바뀌었는지가
 * 이름이 아니라 스탯에 있고, 스킬처럼 항목이 여럿인 소스는 이름을 좌우로 늘어놓으면
 * 같은 이름이 두 번 적히기만 한다. 그 줄들은 주요 변화가 칸을 넓게 쓴다.
 */
export function ChangeRow({ row, info, open, onToggle }: {
  row: Row;
  info: CharacterInfo | null;
  open: boolean;
  onToggle: () => void;
}) {
  const deltas = presentDeltas(row.change.deltas ?? [], info);
  const entries = row.kind === 'source' ? row.change.entries ?? [] : [];
  const replaced = row.kind === 'slot' && row.change.changeType === 'REPLACED';
  /** 장비 줄은 무엇이 어떻게 바뀌었든 아이템 창 두 장으로 말한다. 증감으로는 스타포스도 잠재 재설정도 안 보인다. */
  const slotLabel = row.kind === 'slot'
    ? CHANGE_TYPE_LABELS[row.change.changeType] ?? '변경'
    : '';

  const title = row.kind === 'slot'
    ? row.change.currentItemName ?? row.change.previousItemName ?? '이름 없는 항목'
    : sourceLabel(row.change.source);
  const subtitle = row.kind === 'slot'
    ? `${row.label} · ${bareSlot(row.change.currentSlot ?? row.change.slot ?? row.change.previousSlot)}`
    : entries.length ? `${entries.length}개 항목 변경` : '스탯 변화';
  const icon = row.kind === 'slot'
    ? row.change.currentItemIcon ?? row.change.previousItemIcon
    : entries.find((entry) => entry.icon)?.icon ?? null;

  return (
    <details
      className={replaced ? 'change-row' : 'change-row wide'}
      open={open}
      onToggle={(event) => {
        // 브라우저가 details 를 열고 닫을 때마다 부모에 알린다. 하나만 열어 두려고
        // 열림 상태를 위에서 들고 있다.
        if (event.currentTarget.open !== open) onToggle();
      }}
    >
      <summary className="change-summary">
        <div className="change-name">
          <span className="item-icon" aria-hidden="true">
            {icon ? <img src={icon} alt="" loading="lazy" /> : row.kind === 'slot' ? '◇' : '✦'}
          </span>
          <div>
            <div className="item-title">
              {title}
              {row.kind === 'slot' && (
                <span className={`change-badge badge-${row.change.changeType}`}>
                  {CHANGE_TYPE_LABELS[row.change.changeType] ?? row.change.changeType}
                </span>
              )}
            </div>
            <div className="item-subtitle">{subtitle}</div>
          </div>
        </div>
        {/* 이후 이름은 줄 제목이 이미 말한다. 옆에는 밀려난 것만 둔다. */}
        {replaced && (
          <div className="row-value before">
            <span className="value-item">
              {/* 무엇이 빠졌는지는 이름보다 그림이 빠르다. */}
              {row.change.previousItemIcon && (
                <img className="value-icon" src={row.change.previousItemIcon} alt="" loading="lazy" />
              )}
              {row.change.previousItemName ?? '미장착'}
            </span>
          </div>
        )}
        {/*
          이동속도처럼 전투력에 안 걸리는 것만 바뀌었거나, 바뀐 것이 이 직업에 안 맞는
          스탯이라 걸러졌을 때다. "변경 사항 없음"이라고 하면 줄 자체가 왜 있는지
          말이 안 되므로, 무엇이 없는 것인지를 정확히 적는다.
        */}
        <div className="stat-preview">
          {deltas.length > 0
            ? <StatChips deltas={deltas} />
            : <span className="muted">전투력 변화 요소 없음</span>}
        </div>
        <span className="chevron" aria-hidden="true">›</span>
      </summary>
      <div className="change-body">
        {row.kind === 'slot' && (
          <div className="item-cards">
            <ItemCard item={row.change.previousItem} label={`${slotLabel} 전`} muted />
            <ItemCard item={row.change.currentItem} label={`${slotLabel} 후`} muted={false} />
          </div>
        )}
        {entries.length > 0 && <EntryTable entries={entries} />}
        {row.kind === 'source' && entries.length === 0 && (
          <p className="muted">추가로 표시할 스탯 변화가 없습니다.</p>
        )}
      </div>
    </details>
  );
}
