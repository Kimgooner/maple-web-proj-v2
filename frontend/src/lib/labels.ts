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
};

export function statLabel(statName: string): string {
  return STAT_LABELS[statName] ?? statName;
}

const SOURCE_LABELS: Record<string, string> = {
  abilityPoint: 'AP 배분', symbol: '심볼', skill: '스킬', hexaStat: '헥사스탯',
  ability: '어빌리티', hyperStat: '하이퍼스탯', setEffect: '세트효과',
  unionOccupied: '유니온 점령 효과', unionRaider: '유니온 공격대', unionArtifact: '유니온 아티팩트',
  unionChampion: '유니온 챔피언',
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

/** 계산이 아직 안 끝난 직업. HP 기반 계산식이라 주/부스탯 경로를 그대로 못 쓴다 */
export const INCOMPLETE_CLASSES = new Set(['데몬어벤져']);
