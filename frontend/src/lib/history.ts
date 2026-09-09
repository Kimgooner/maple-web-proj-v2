import type { HistoryEvent, HistoryMeta, HistoryPoint } from '../api/types';

export type HistoryStatus = 'idle' | 'loading' | 'done' | 'error';

/** 스트림을 새로 열기 전 상태를 비운다. 이름이나 재시도가 바뀔 때. */
export type HistoryAction = HistoryEvent | { type: 'reset' };

export interface HistoryState {
  status: HistoryStatus;
  meta: HistoryMeta | null;
  /** 과거 → 최신 순. meta 의 dates 로 자리를 미리 만들고 point 가 오면 채운다 */
  points: HistoryPoint[];
  received: number;
  total: number;
  error: string | null;
  /** 'NOT_FOUND' 면 없는 캐릭터라 화면이 따로 그린다 */
  errorCode: string | null;
}

export const initialHistoryState: HistoryState = {
  status: 'idle',
  meta: null,
  points: [],
  received: 0,
  total: 0,
  error: null,
  errorCode: null,
};

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
      return loadingHistoryState();
    case 'meta': {
      const dates = [...event.data.dates].sort();
      return {
        ...state,
        status: 'loading',
        meta: event.data,
        points: dates.map((date) => ({ date, level: null, combatPower: null, apiCombatPower: null, solErdaFragments: null, solErdaFragmentsRequired: null, expired: null })),
        received: 0,
        total: event.data.plannedCount,
        error: null,
        errorCode: null,
      };
    }
    case 'point': {
      const incoming = withoutZero(event.data.point);
      const points = state.points.some((p) => p.date === incoming.date)
        ? state.points.map((p) => (p.date === incoming.date ? incoming : p))
        : [...state.points, incoming].sort((a, b) => a.date.localeCompare(b.date));
      return {
        ...state,
        points,
        received: Math.max(state.received, event.data.index),
        total: event.data.total,
      };
    }
    case 'done':
      return { ...state, status: 'done', received: state.total };
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
