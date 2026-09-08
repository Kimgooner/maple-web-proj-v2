/** 백엔드 명세: .plan/general/2026-09-04-frontend-api-handoff.md */

export type HistoryRange = 'daily' | 'monthly';

export interface CharacterInfo {
  name: string;
  className: string;
  level: number | null;
  guild: string | null;
  world: string | null;
  image: string | null;
}

export interface HistoryPoint {
  date: string;
  level: number | null;
  combatPower: number | null;
  apiCombatPower: number | null;
  /** HEXA 코어에 그때까지 들어간 솔 에르다 조각. 6차 전이거나 못 받았으면 null */
  solErdaFragments: number | null;
}

export interface HistoryMeta {
  ocid: string;
  range: string;
  characterInfo: CharacterInfo;
  requestedCount: number;
  plannedCount: number;
  truncated: boolean;
  truncatedFrom: string | null;
  /** 최신 → 과거 순으로 온다 */
  dates: string[];
}

export interface HistoryPointEvent {
  index: number;
  total: number;
  point: HistoryPoint;
}

export interface HistoryDone {
  loadedCount: number;
  truncated: boolean;
  truncatedFrom: string | null;
}

export interface HistoryError {
  /** 'NOT_FOUND' 면 없는 캐릭터. 그 밖의 실패는 'ERROR' */
  code: string;
  message: string;
}

export type HistoryEvent =
  | { type: 'meta'; data: HistoryMeta }
  | { type: 'point'; data: HistoryPointEvent }
  | { type: 'done'; data: HistoryDone }
  | { type: 'error'; data: HistoryError };

export interface StatDelta {
  statName: string;
  delta: number;
}

/** 이름 있는 항목(스킬·심볼·세트…) 하나의 변화. 생긴 것은 previous 가 null, 사라진 것은 current 가 null */
export interface EntryChange {
  name: string;
  previous: string | null;
  current: string | null;
  /** 넥슨 아이콘 URL. 스킬·심볼만 있다 */
  icon: string | null;
}

export interface SourceChange {
  source: string;
  deltas: StatDelta[];
  entries: EntryChange[];
}

export type ChangeType = 'ADDED' | 'REMOVED' | 'REPLACED' | 'STAT_CHANGED';

export interface SlotChange {
  slot: string | null;
  previousSlot: string | null;
  currentSlot: string | null;
  changeType: ChangeType;
  previousItemName: string | null;
  currentItemName: string | null;
  previousItemIcon: string | null;
  currentItemIcon: string | null;
  deltas: StatDelta[];
}

export interface ChangeSummary {
  coreChanges: SourceChange[];
  petChanges: SlotChange[];
  cashChanges: SlotChange[];
  itemChanges: SlotChange[];
}

export interface DetailResponse {
  ocid: string;
  previousDate: string;
  currentDate: string;
  changeSummary: ChangeSummary;
}
