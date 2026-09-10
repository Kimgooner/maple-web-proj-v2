import type { CharacterInfo, HistoryPoint } from '../api/types';
import { dateLabel, formatGameNumber, formatPercentChange, formatSignedGame, shortDate } from '../lib/format';
import { INCOMPLETE_CLASSES } from '../lib/labels';

interface Props {
  info: CharacterInfo | null;
  name: string;
  points: HistoryPoint[];
  loading: boolean;
}

function tone(value: number | null): string {
  if (value == null || value === 0) return 'neutral';
  return value > 0 ? 'positive' : 'negative';
}

/** 캐릭터 요약. 이름·실전 전투력·조회 구간 변화 셋을 나란히 둔다. */
export function CharacterHeader({ info, name, points, loading }: Props) {
  const valid = points.filter((point) => point.combatPower != null);
  const first = valid[0];
  const latest = valid[valid.length - 1];
  const level = latest?.level ?? info?.level;
  const meta = [level == null ? null : `Lv. ${level}`, info?.className, info?.guild].filter(Boolean).join(' · ');

  const cooldownSecond = latest?.cooldownSecond ?? 0;
  const cooldownSkip = latest?.cooldownSkipPercent ?? 0;
  const comparable = !loading && valid.length >= 2;
  const delta = comparable ? latest.combatPower! - first.combatPower! : null;

  return (
    <section className="profile panel" aria-label="캐릭터 요약">
      <div className="identity">
        <div className="avatar" aria-hidden="true">
          {info?.image ? <img src={info.image} alt="" /> : '✦'}
        </div>
        <div>
          <div className="name-line">
            <h1>{info?.name || name}</h1>
            {info?.world && <span className="world">{info.world}</span>}
          </div>
          <p className="muted">{meta}</p>
          {info?.className && INCOMPLETE_CLASSES.has(info.className) && (
            <span className="warning">계산 정확도 낮음 · {info.className}</span>
          )}
        </div>
      </div>

      <div className="power">
        <div className="metric-label">실전 전투력 <span className="soft-badge">보스 프리셋 기준</span></div>
        <strong className="power-number">{latest?.combatPower != null ? formatGameNumber(latest.combatPower) : '—'}</strong>
        <small className="muted">{latest ? `${dateLabel(latest.date)} 기준` : '계산 데이터를 기다리고 있어요'}</small>
        {/*
          전투력에 안 들어가지만 실전에서 크게 갈리는 값이라 전투력 옆에 붙인다.
          0 이면 적지 않는다 — "0초 감소"는 알려 주는 것이 없다.
        */}
        {(cooldownSecond > 0 || cooldownSkip > 0) && (
          <div className="cooldowns">
            {cooldownSecond > 0 && <span>재사용 대기시간 <b>{cooldownSecond}초</b></span>}
            {cooldownSkip > 0 && <span>재사용 대기시간 <b>{cooldownSkip}%</b>로 미적용</span>}
          </div>
        )}
      </div>

      <div className="period-metric">
        <div className="metric-label">{loading ? '조회 중' : '조회 구간 변화'}</div>
        <strong className={tone(delta)}>
          {comparable ? formatPercentChange(first.combatPower!, latest.combatPower!) : '—'}
        </strong>
        <span className={`number ${tone(delta)}`}>{delta != null ? formatSignedGame(delta) : '—'}</span>
        <small className="muted">{comparable ? `${shortDate(first.date)} — ${shortDate(latest.date)}` : ''}</small>
      </div>
    </section>
  );
}
