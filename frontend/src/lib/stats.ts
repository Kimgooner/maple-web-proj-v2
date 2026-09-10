import type { CharacterInfo, StatDelta } from '../api/types';

/**
 * 스탯을 놓는 순서. 앞일수록 전투력에 크게 걸린다.
 *
 * <p>여기 없는 것(HP·최종뎀·/9렙·고정 계열)은 이 목록 뒤에, 서버가 준 순서대로 붙는다.
 * 방어율 무시는 StatSheet 에 필드가 없어 아직 오지 않는다 — 생기면 이 자리에서 잡힌다.
 */
const ORDER = [
  'ATTACK_POWER_PERCENT', 'MAGIC_POWER_PERCENT',
  'BOSS_DAMAGE',
  'DAMAGE',
  'CRITICAL_DAMAGE',
  'IGNORE_DEFENSE',
  'ALL_STAT_PERCENT', 'MAIN_PERCENT', 'SUB_PERCENT',
  'ATTACK_POWER', 'MAGIC_POWER',
  'MAIN_FLAT', 'SUB_FLAT',
];

/**
 * 솔 에르다 조각. 전투력 스탯이 아니라 "얼마나 부었나"라서 항상 맨 뒤에 둔다.
 *
 * <p>순서가 곧 중요도인 목록에 조각이 중간에 끼면, 전투력에 걸리는 스탯을 읽다 말고
 * 성격이 다른 값을 한 번 건너뛰게 된다. 색도 차트의 조각 선과 같은 보라라 눈에 띄어서,
 * 뒤에 있어야 "덤으로 붙은 값"으로 읽힌다.
 */
const FRAGMENT = 'SOL_ERDA_FRAGMENT';

/**
 * 재사용 대기시간. 전투력에는 안 들어가지만 실전에서는 주력기를 몇 번 더 쓰느냐를 가른다.
 * 성격이 다른 값이라 전투력 스탯 뒤, 조각 앞에 모아 둔다.
 */
const COOLDOWN = ['COOLDOWN_SECOND', 'COOLDOWN_SKIP_PERCENT'];

/** 직업마다 갈리는 자리. 주스탯이 INT 인 캐릭터에게 INT_PERCENT 는 MAIN_PERCENT 다. */
const BASE_STATS = ['STR', 'DEX', 'INT', 'LUK'];

function baseOf(statName: string): string | null {
  const base = BASE_STATS.find((stat) => statName === stat || statName.startsWith(`${stat}_`));
  return base ?? null;
}

/**
 * 이 직업이 실제로 쓰는 스탯만 남긴다.
 *
 * <p>남의 직업 주스탯(마법사에게 STR)은 값이 붙어 있어도 전투력에 한 톨도 안 들어간다.
 * 그것까지 늘어놓으면 정작 무엇이 올랐는지가 묻힌다. 직업을 아직 모르면 전부 남긴다 —
 * 잘못 지우느니 많이 보여주는 쪽이 낫다.
 */
function applies(statName: string, info: CharacterInfo | null): boolean {
  if (!info) return true;
  if (statName === 'ATTACK_POWER' || statName === 'ATTACK_POWER_PERCENT') return !info.usesMagic;
  if (statName === 'MAGIC_POWER' || statName === 'MAGIC_POWER_PERCENT') return info.usesMagic;

  const base = baseOf(statName);
  if (base === null) return true;
  return (info.mainStats ?? []).includes(base) || (info.subStats ?? []).includes(base);
}

function rank(statName: string, info: CharacterInfo | null): number {
  if (statName === FRAGMENT) return ORDER.length + 2;
  if (COOLDOWN.includes(statName)) return ORDER.length + 1;
  const base = baseOf(statName);
  let key = statName;
  if (base !== null) {
    const main = (info?.mainStats ?? []).includes(base);
    key = statName.endsWith('_PERCENT') ? (main ? 'MAIN_PERCENT' : 'SUB_PERCENT')
      : (main ? 'MAIN_FLAT' : 'SUB_FLAT');
  }
  const at = ORDER.indexOf(key);
  return at < 0 ? ORDER.length : at;
}

/** 화면에 내놓을 스탯. 직업에 안 맞는 것을 지우고 중요한 순으로 놓는다. */
export function presentDeltas(deltas: StatDelta[], info: CharacterInfo | null): StatDelta[] {
  return deltas
    .filter((delta) => applies(delta.statName, info))
    .map((delta, index) => ({ delta, index, rank: rank(delta.statName, info) }))
    // 같은 자리(주스탯이 둘인 제논, 목록에 없는 것들)는 서버가 준 순서를 지킨다.
    .sort((left, right) => left.rank - right.rank || left.index - right.index)
    .map((entry) => entry.delta);
}
