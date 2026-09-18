import type { ChangeType } from '../api/types';

const STAT_LABELS: Record<string, string> = {
  STR: 'STR', DEX: 'DEX', INT: 'INT', LUK: 'LUK', HP: 'HP', ALL_STAT: '올스탯',
  STR_PER_LEVEL9: 'STR/9렙', DEX_PER_LEVEL9: 'DEX/9렙', INT_PER_LEVEL9: 'INT/9렙', LUK_PER_LEVEL9: 'LUK/9렙',
  STR_NO_PERCENT: 'STR(고정)', DEX_NO_PERCENT: 'DEX(고정)', INT_NO_PERCENT: 'INT(고정)',
  LUK_NO_PERCENT: 'LUK(고정)', HP_NO_PERCENT: 'HP(고정)', ALL_STAT_NO_PERCENT: '올스탯(고정)',
  ATTACK_POWER: '공격력', MAGIC_POWER: '마력',
  STR_PERCENT: 'STR', DEX_PERCENT: 'DEX', INT_PERCENT: 'INT', LUK_PERCENT: 'LUK',
  HP_PERCENT: 'HP', ALL_STAT_PERCENT: '올스탯',
  ATTACK_POWER_PERCENT: '공격력', MAGIC_POWER_PERCENT: '마력',
  DAMAGE: '데미지', BOSS_DAMAGE: '보공', CRITICAL_DAMAGE: '크뎀', FINAL_DAMAGE: '최종뎀',
  COOLDOWN_SECOND: '재사용 감소', COOLDOWN_SKIP_PERCENT: '재사용 미적용',
  // 전투력 스탯이 아니다. 헥사 강화가 전투력에 안 잡혀 비는 증감 칸을 이걸로 채운다.
  SOL_ERDA_FRAGMENT: '조각',
};

export function statLabel(statName: string): string {
  return STAT_LABELS[statName] ?? statName;
}

const SOURCE_LABELS: Record<string, string> = {
  abilityPoint: '레벨 상승', symbol: '심볼', skill: '스킬', hexaStat: '헥사스탯', hexaCore: '헥사 코어', otherStat: '기타 능력치',
  ability: '어빌리티', hyperStat: '하이퍼스탯', setEffect: '세트효과',
  unionOccupied: '유니온 점령 효과', unionRaider: '유니온 공격대', unionArtifact: '유니온 아티팩트',
  unionChampion: '유니온 챔피언',
  // 계산 과정의 "요소별 스탯 합"에서만 쓰는 소스. 변경 내역은 장비·캐시·펫을 부위별로 따로 다룬다.
  items: '장비', cash: '캐시 장비', pet: '펫 장비', consumableItem: '소모품',
  propensity: '성향(의지)', conversionStarforce: '컨버전 스타포스',
};

export function sourceLabel(source: string): string {
  return SOURCE_LABELS[source] ?? source;
}

export const CHANGE_TYPE_LABELS: Record<ChangeType, string> = {
  ADDED: '장착',
  REMOVED: '해제',
  REPLACED: '교체',
  STAT_CHANGED: '옵션 변경',
};

/**
 * 계산이 아직 안 끝난 직업. 지금은 없다 — 데몬어벤져도 2026-09-12 에 HP 환산식을 실어
 * 오차 0.001% 안으로 들어왔다. 새 직업이 나와 식이 미완이면 여기 넣는다.
 */
export const INCOMPLETE_CLASSES = new Set<string>([]);
