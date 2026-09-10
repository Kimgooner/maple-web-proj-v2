import type { HistoryEvent, HistoryMeta, HistoryPoint } from '../api/types';

export type HistoryStatus = 'idle' | 'loading' | 'done' | 'error';

/** 스트림을 새로 열기 전 상태를 비운다. 이름이나 재시도가 바뀔 때. */
export type HistoryAction =
  | HistoryEvent
  | { type: 'reset' }
  /** 프리셋을 되돌려 다시 계산한 지점으로 통째로 갈아 끼운다 */
  | { type: 'repaired'; points: HistoryPoint[] };

export interface HistoryState {
  status: HistoryStatus;
  meta: HistoryMeta | null;
  /** 과거 → 최신 순. meta 의 dates 로 자리를 미리 만들고 point 가 오면 채운다 */
  points: HistoryPoint[];
  received: number;
  total: number;
  error: string | null;
  /** 'NOT_FOUND'(없는 캐릭터) · 'TOO_LOW'(Lv.260 미만) 는 화면이 따로 그린다 */
  errorCode: string | null;
  /**
   * 이미 보여 줄 것이 있는데 새로 받는 중일 때, 새 것을 여기 쌓았다가 다 받으면 한 번에 바꾼다.
   *
   * <p>30일 ↔ 12개월을 오갈 때 화면이 통째로 비었다가 다시 차면, 바뀔 이유가 없는 이름·레벨·
   * 전투력까지 같이 깜빡인다. 다 받은 뒤 갈아 끼우면 바뀌는 것만 바뀐다.
   */
  pending: { meta: HistoryMeta; points: HistoryPoint[] } | null;
  /**
   * 지금 함께 추이를 받고 있는 사람 수(자기 포함).
   *
   * <p>콜드 조회 하나가 넥슨을 510회 쓰고 초당 호출에 상한이 있어, 동시에 보는 사람이
   * 많으면 다 같이 느려진다. 멈춘 것이 아니라 줄을 선 것인데 화면에서는 구별이 안 되므로,
   * 기다림이 길어질 때 이 수를 같이 보여 준다.
   */
  concurrent: number;
}

export const initialHistoryState: HistoryState = {
  status: 'idle',
  meta: null,
  points: [],
  received: 0,
  total: 0,
  error: null,
  errorCode: null,
  pending: null,
  concurrent: 0,
};

/** 화면에 내놓을 만한 지점이 있는가. 자리만 잡힌 빈 배열은 아니다. */
function hasShowable(state: HistoryState): boolean {
  return state.points.some((point) => !isPending(point));
}

function blankPoints(dates: string[]): HistoryPoint[] {
  return [...dates].sort().map((date) => ({
    date, level: null, combatPower: null, apiCombatPower: null,
    solErdaFragments: null, solErdaFragmentsRequired: null,
    cooldownSecond: null, cooldownSkipPercent: null, itemPreset: null, expired: null,
  }));
}

function fill(points: HistoryPoint[], incoming: HistoryPoint): HistoryPoint[] {
  return points.some((point) => point.date === incoming.date)
    ? points.map((point) => (point.date === incoming.date ? incoming : point))
    : [...points, incoming].sort((left, right) => left.date.localeCompare(right.date));
}

export function loadingHistoryState(): HistoryState {
  return { ...initialHistoryState, status: 'loading' };
}

/**
 * 전투력 0 은 값이 아니라 "못 구했다"로 본다.
 *
 * <p>캐릭터가 아직 없던 날짜나 계산이 서지 않는 캐릭터에서 0 이 온다. 그대로 두면
 * 그래프가 바닥에 한 점 찍히고, 축이 0 을 품느라 왼쪽 눈금이 음수까지 벌어진다.
 * null 로 바꿔 두면 선이 끊기고 구간 선택·변화 핀도 그 지점을 건너뛴다 —
 * 아래 로직이 전부 {@code combatPower !== null} 로 거르기 때문이다.
 */
function withoutZero(point: HistoryPoint): HistoryPoint {
  if (point.combatPower !== 0 && point.apiCombatPower !== 0) return point;
  return {
    ...point,
    combatPower: point.combatPower === 0 ? null : point.combatPower,
    apiCombatPower: point.apiCombatPower === 0 ? null : point.apiCombatPower,
  };
}

/** 아직 point 가 오지 않은 자리 */
export function isPending(point: HistoryPoint): boolean {
  return point.level === null && point.combatPower === null && point.solErdaFragments === null;
}

export function applyHistoryEvent(state: HistoryState, event: HistoryAction): HistoryState {
  switch (event.type) {
    case 'reset':
      // 보여 줄 것이 이미 있으면 들고 있는다. 새 것을 다 받으면 그때 갈아 끼운다.
      return hasShowable(state)
        ? { ...state, status: 'loading', received: 0, total: 0, error: null, errorCode: null, pending: null }
        : loadingHistoryState();
    case 'meta': {
      const points = blankPoints(event.data.dates);
      const base = { ...state, status: 'loading' as const, received: 0, total: event.data.plannedCount, error: null, errorCode: null, concurrent: event.data.concurrent };
      return hasShowable(state)
        ? { ...base, pending: { meta: event.data, points } }
        : { ...base, meta: event.data, points, pending: null };
    }
    case 'point': {
      const incoming = withoutZero(event.data.point);
      const received = Math.max(state.received, event.data.index);
      const concurrent = event.data.concurrent;
      return state.pending
        ? { ...state, pending: { ...state.pending, points: fill(state.pending.points, incoming) }, received, total: event.data.total, concurrent }
        : { ...state, points: fill(state.points, incoming), received, total: event.data.total, concurrent };
    }
    case 'done':
      return state.pending
        ? { ...state, status: 'done', received: state.total, meta: state.pending.meta, points: state.pending.points, pending: null }
        : { ...state, status: 'done', received: state.total };
    case 'repaired':
      return { ...state, points: event.points, pending: null };
    case 'error':
      return { ...state, status: 'error', error: event.data.message, errorCode: event.data.code ?? 'ERROR' };
  }
}

/** 로드된 최신 지점. 계산값이 null 이면 그 지점은 건너뛴다 */
export function latestLoadedPoint(points: HistoryPoint[]): HistoryPoint | null {
  for (let i = points.length - 1; i >= 0; i -= 1) {
    if (points[i].combatPower !== null) return points[i];
  }
  return null;
}
