import type { CharacterInfo, HistoryPoint, HistoryRange, PresetInfo } from '../api/types';
import { dateLabel, formatGameNumber, formatPercentChange, formatSignedGame, shortDate } from '../lib/format';
import { INCOMPLETE_CLASSES } from '../lib/labels';

interface Props {
  info: CharacterInfo | null;
  name: string;
  points: HistoryPoint[];
  loading: boolean;
  /** 계산에 실제로 쓴 프리셋 번호. 아직 못 받았으면 null */
  preset: PresetInfo | null;
  /** 지금 보고 있는 기간. "조회 구간(최근 30일) 변화" 처럼 라벨에 적는다 */
  range: HistoryRange;
}

const RANGE_LABEL: Record<HistoryRange, string> = { daily: '최근 30일', monthly: '최근 12개월' };

/**
 * 계산에 쓴 프리셋 번호. "보스 프리셋 기준" 이라고만 적으면 어느 번호를 골랐는지 알 수 없어,
 * 게임 안 값과 다를 때 어디를 봐야 할지 짚어 주지 못한다.
 */
function presetLabel(preset: PresetInfo | null): string | null {
  if (!preset) return null;
  return [
    `장비 ${preset.itemPreset}`,
    `어빌리티 ${preset.abilityPreset}`,
    `하이퍼 ${preset.hyperStatPreset}`,
    `유니온 ${preset.unionRaiderPreset}`,
  ].join(' · ');
}

function tone(value: number | null): string {
  if (value == null || value === 0) return 'neutral';
  return value > 0 ? 'positive' : 'negative';
}

/** 캐릭터 요약. 이름·실전 전투력·조회 구간 변화 셋을 나란히 둔다. */
export function CharacterHeader({ info, name, points, loading, preset, range }: Props) {
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
        <div className="metric-label">
          전투력
          {presetLabel(preset) && <span className="soft-badge">{presetLabel(preset)}</span>}
        </div>
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
        <div className="metric-label">{loading ? '조회 중' : `조회 구간(${RANGE_LABEL[range]}) 변화`}</div>
        <strong className={tone(delta)}>
          {comparable ? formatPercentChange(first.combatPower!, latest.combatPower!) : '—'}
        </strong>
        <span className={`number ${tone(delta)}`}>{delta != null ? formatSignedGame(delta) : '—'}</span>
        <small className="muted">{comparable ? `${shortDate(first.date)} — ${shortDate(latest.date)}` : ''}</small>
      </div>
    </section>
  );
}
