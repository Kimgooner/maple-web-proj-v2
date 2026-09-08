import type { CharacterInfo, HistoryPoint } from '../api/types';
import { dateLabel, formatNumber, formatPercentChange, formatSigned, shortDate } from '../lib/format';
import { INCOMPLETE_CLASSES } from '../lib/labels';
import { expiredLabel, latestExpired } from '../lib/expired';

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

  const expired = expiredLabel(latestExpired(points));
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
          {expired && (
            <span
              className="expired-warning"
              title="기간이 지나 스탯이 빠진 항목입니다. 게임에는 아직 붙어 보일 수 있지만 계산에서는 빠졌습니다."
            >
              만료됨 · {expired}
            </span>
          )}
        </div>
      </div>

      <div className="power">
        <div className="metric-label">실전 전투력 <span className="soft-badge">보스 프리셋 기준</span></div>
        <strong className="power-number">{latest?.combatPower != null ? formatNumber(latest.combatPower) : '—'}</strong>
        <small className="muted">{latest ? `${dateLabel(latest.date)} 기준` : '계산 데이터를 기다리고 있어요'}</small>
      </div>

      <div className="period-metric">
        <div className="metric-label">{loading ? '조회 중' : '조회 구간 변화'}</div>
        <strong className={tone(delta)}>
          {comparable ? formatPercentChange(first.combatPower!, latest.combatPower!) : '—'}
        </strong>
        <span className={`number ${tone(delta)}`}>{delta != null ? formatSigned(delta) : '—'}</span>
        <small className="muted">{comparable ? `${shortDate(first.date)} — ${shortDate(latest.date)}` : ''}</small>
      </div>
    </section>
  );
}
