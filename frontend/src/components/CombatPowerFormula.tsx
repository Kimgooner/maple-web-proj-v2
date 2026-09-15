import type { CombatPowerBreakdown } from '../api/types';
import { formatGameNumber, formatNumber } from '../lib/format';

/** 소수가 있으면 한 자리까지. 데몬어벤져 주스탯처럼 환산값이 소수인 것만 해당된다. */
function stat(value: number): string {
  return Number.isInteger(value) ? formatNumber(value) : formatNumber(Math.round(value * 10) / 10);
}

function percent(value: number): string {
  return `${formatNumber(Math.round(value * 100) / 100)}%`;
}

function signed(value: number): string {
  return `${value >= 0 ? '+' : ''}${percent(value)}`;
}

/**
 * 전투력이 어떻게 나왔는지. 게임 식의 항을 차례로 늘어놓고 마지막에 전투력을 둔다.
 *
 * <p>항마다 위에 이름, 가운데 식에 들어간 값, 아래에 그 값이 무엇으로 이루어졌는지를 적는다.
 * 데미지는 "542%" 만 보면 어디서 온 수인지 모르는데, "100 + 데미지 32 + 보공 410" 이라고
 * 풀어 두면 자기 스탯창과 맞춰 볼 수 있다.
 */
export function CombatPowerFormula({ breakdown }: { breakdown: CombatPowerBreakdown }) {
  const terms: { label: string; value: string; note: string }[] = [
    {
      label: '스탯',
      value: stat(breakdown.statTerm),
      note: `(주스탯 ${stat(breakdown.mainStat)} × 4${breakdown.subStat ? ` + 부스탯 ${stat(breakdown.subStat)}` : ''}) ÷ 100`,
    },
    {
      label: breakdown.usesMagic ? '마력' : '공격력',
      value: formatNumber(breakdown.power),
      note: '% 까지 곱한 값',
    },
    {
      label: '데미지',
      value: percent(100 + breakdown.damage + breakdown.bossDamage),
      note: `100 + 데미지 ${signed(breakdown.damage)} + 보공 ${signed(breakdown.bossDamage)}`,
    },
    {
      label: '크리티컬 데미지',
      value: percent(135 + breakdown.criticalDamage),
      note: `135 + 크뎀 ${signed(breakdown.criticalDamage)}`,
    },
    {
      label: '최종 데미지',
      value: percent(100 + breakdown.finalDamage),
      note: `100 + 최종 데미지 ${signed(breakdown.finalDamage)}`,
    },
  ];
  if (breakdown.correction != null) {
    terms.push({
      label: '직업 보정',
      value: `× ${formatNumber(Math.round(breakdown.correction * 1e6) / 1e6)}`,
      note: '스탯 계산이 다른 직업의 보정 상수',
    });
  }

  return (
    <div className="formula" aria-label="전투력 계산 과정">
      <div className="formula-terms">
        {terms.map((term, index) => (
          <div className="formula-term" key={term.label}>
            {index > 0 && <span className="formula-op" aria-hidden="true">×</span>}
            <div className="formula-card">
              <div className="formula-label">{term.label}</div>
              <div className="formula-value">{term.value}</div>
              <div className="formula-note">{term.note}</div>
            </div>
          </div>
        ))}
        <div className="formula-term">
          <span className="formula-op" aria-hidden="true">÷ 1,000,000 =</span>
          <div className="formula-card result">
            <div className="formula-label">전투력</div>
            <div className="formula-value">{formatGameNumber(breakdown.combatPower)}</div>
            <div className="formula-note">소수점은 버린다</div>
          </div>
        </div>
      </div>
      <p className="formula-foot muted">
        방어율 무시·크리티컬 확률·재사용 대기시간은 전투력에 들어가지 않습니다. 값은 오늘 시점의
        계산에 실제로 쓴 것들입니다.
      </p>
    </div>
  );
}
