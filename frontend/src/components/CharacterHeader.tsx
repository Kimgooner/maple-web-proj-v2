import type { CharacterInfo, HistoryPoint } from '../api/types';
import { formatNumber } from '../lib/format';
import { INCOMPLETE_CLASSES } from '../lib/labels';
import { PersonIcon } from './icons';

interface Props {
  info: CharacterInfo;
  latest: HistoryPoint | null;
}

export function CharacterHeader({ info, latest }: Props) {
  const incomplete = INCOMPLETE_CLASSES.has(info.className);
  const level = latest?.level ?? info.level;
  const apiDiffers = latest?.apiCombatPower != null && latest.combatPower != null && latest.apiCombatPower !== latest.combatPower;

  return (
    <div className="card header">
      <div className="header-left">
        <div className="avatar">{info.image ? <img src={info.image} alt="" /> : <PersonIcon />}</div>
        <div>
          <div className="header-name">
            <h1>{info.name}</h1>
            {info.world && <span className="tag">{info.world}</span>}
            {incomplete && <span className="tag tag-warn" title="이 직업은 계산식이 아직 완성되지 않아 값이 넥슨과 크게 다를 수 있습니다">계산 정확도 낮음</span>}
          </div>
          <div className="header-meta">
            <span>{info.className}</span>
            {level != null && <><span>·</span><span className="mono">Lv. {level}</span></>}
            {info.guild && <><span>·</span><span>{info.guild}</span></>}
          </div>
        </div>
      </div>
      <div className="header-right">
        <span className="header-label">실전 전투력 · 계산값</span>
        <span className="header-cp mono">{latest?.combatPower != null ? formatNumber(latest.combatPower) : '—'}</span>
        {latest?.apiCombatPower != null && (
          <span className="header-api mono" title={apiDiffers ? '넥슨 stat 문서는 캐릭터가 접속하지 않으면 그 시점에 멈춥니다. 차이가 나도 오류가 아닙니다.' : undefined}>
            넥슨 전투력 {formatNumber(latest.apiCombatPower)}
          </span>
        )}
      </div>
    </div>
  );
}
