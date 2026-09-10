/** 백엔드 명세: .plan/general/2026-09-04-frontend-api-handoff.md */

export type HistoryRange = 'daily' | 'monthly';

export interface CharacterInfo {
  name: string;
  className: string;
  level: number | null;
  guild: string | null;
  world: string | null;
  image: string | null;
  /** 이 직업의 주스탯 (STR/DEX/INT/LUK). 화면이 남의 직업 스탯 증감을 지운다 */
  mainStats: string[];
  /** 부스탯. 직업에 따라 둘 이상일 수 있다 */
  subStats: string[];
  /** 마력을 쓰는 직업인가. 공격력·마력 중 어느 쪽을 보여줄지 가른다 */
  usesMagic: boolean;
}

export interface HistoryPoint {
  date: string;
  level: number | null;
  combatPower: number | null;
  apiCombatPower: number | null;
  /** HEXA 코어에 그때까지 들어간 솔 에르다 조각. 6차 전이거나 못 받았으면 null */
  solErdaFragments: number | null;
  /** 가진 코어를 모두 만렙까지 올리는 데 드는 조각. 조각 축 0~100%의 분모 */
  solErdaFragmentsRequired: number | null;
  /** 스킬 재사용 대기시간 감소(초). 모자 잠재에서 온다 */
  cooldownSecond: number | null;
  /** 스킬 사용 시 재사용 대기시간이 통째로 미적용될 확률(%). 어빌리티에서 온다 */
  cooldownSkipPercent: number | null;
  /** 그날 계산에 쓴 장비 프리셋 번호. 점수가 같아 갈릴 때가 있어 되돌릴 근거가 된다 */
  itemPreset: number | null;
  /** 기간이 지나 계산에서 빠진 것들. 하나도 없으면 null */
  expired: ExpiredItems | null;
}

/** 만료로 스탯이 빠진 항목 수. 스탯이 없던 항목은 파서가 표시하지 않으므로 세지 않는다 */
export interface ExpiredItems {
  artifactCrystals: number;
  cashItems: number;
  petEquipments: number;
  titleOption: boolean;
}

/** 계산에 실제로 쓴 프리셋 번호 */
export interface PresetInfo {
  itemPreset: number;
  abilityPreset: number;
  hyperStatPreset: number;
  unionRaiderPreset: number;
}

export interface HistoryMeta {
  ocid: string;
  range: string;
  characterInfo: CharacterInfo;
  preset: PresetInfo | null;
  requestedCount: number;
  plannedCount: number;
  truncated: boolean;
  truncatedFrom: string | null;
  /** 최신 → 과거 순으로 온다 */
  dates: string[];
  /** 이 순간 함께 추이를 받고 있는 사람 수. 자기 자신을 포함한다 */
  concurrent: number;
}

export interface HistoryPointEvent {
  index: number;
  total: number;
  point: HistoryPoint;
  /** 기다리는 동안 변하므로 지점마다 새로 온다 */
  concurrent: number;
}

export interface HistoryDone {
  loadedCount: number;
  truncated: boolean;
  truncatedFrom: string | null;
}

export interface HistoryError {
  /** 'NOT_FOUND' 없는 캐릭터 · 'TOO_LOW' Lv.260 미만 · 그 밖의 실패는 'ERROR' */
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
  /** 펼쳐 봤을 때 보여줄 여러 줄 설명. 세트 효과 문구 등. 줄바꿈으로 구분된다 */
  /** 펼쳤을 때 이전 칸에 놓을 여러 줄 설명. 세트 효과 문구 등 */
  previousDetail: string | null;
  /** 같은 것의 이후 칸 몫 */
  detail: string | null;
  /** 이름 밑에 붙일 블럭. 헥사 코어의 종류 */
  badge: string | null;
}

export interface SourceChange {
  source: string;
  deltas: StatDelta[];
  entries: EntryChange[];
  /**
   * 캐릭터가 아니라 넥슨 데이터가 바뀐 것일 때 줄에 붙일 말. 아니면 null.
   * 유니온 공격대 문서가 비어 오는 날이 그렇다 - 성장으로 읽히면 안 된다.
   */
  notice: string | null;
}

export type ChangeType = 'ADDED' | 'REMOVED' | 'REPLACED' | 'STAT_CHANGED';

/** 옵션 / 잠재 / 익셉셔널처럼 변화를 갈라 놓은 묶음. 비어 있는 종류는 오지 않는다 */
export interface StatDeltaGroup {
  category: string;
  deltas: StatDelta[];
}

/** 아이템 창 스탯 한 줄. total = base + add + scroll + starforce */
export interface ItemStatLine {
  name: string;
  total: number;
  base: number;
  add: number;
  scroll: number;
  starforce: number;
  percent: boolean;
}

/** 게임 아이템 창 한 장. 교체를 펼쳐 이전·이후를 나란히 읽는다 */
export interface ItemDetail {
  name: string | null;
  icon: string | null;
  starForce: number | null;
  scrollUpgrade: number | null;
  requiredLevel: number | null;
  potentialGrade: string | null;
  additionalPotentialGrade: string | null;
  /** 기간이 지나 스탯이 빠졌으면 그 사유. 아직 살아 있으면 null */
  expired: string | null;
  stats: ItemStatLine[];
  /** 설명문이 곧 스탯인 것들(칭호). 스탯 줄이 없을 때 이 자리가 채워진다 */
  descriptionLines: string[];
  potentialLines: string[];
  additionalPotentialLines: string[];
  exceptionalLines: string[];
}

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
  deltaGroups: StatDeltaGroup[];
  /** 게임 아이템 창에 나오는 것들. 교체를 펼쳐 나란히 읽는다. 빈 자리면 null */
  previousItem: ItemDetail | null;
  currentItem: ItemDetail | null;
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
