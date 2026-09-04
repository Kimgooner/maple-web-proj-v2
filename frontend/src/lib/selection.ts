import type { HistoryPoint } from '../api/types';

/** 비교 구간. 둘 다 points 안의 date 이고 previous < current */
export interface Interval {
  previousDate: string;
  currentDate: string;
}

/**
 * 기본 구간: 계산 전투력이 마지막으로 변한 인접 두 지점.
 * 변한 곳이 없으면 값이 있는 최신 두 지점. 값이 있는 지점이 둘 미만이면 null.
 */
export function defaultInterval(points: HistoryPoint[]): Interval | null {
  const loaded = points.filter((p) => p.combatPower !== null);
  if (loaded.length < 2) return null;

  for (let i = loaded.length - 1; i >= 1; i -= 1) {
    if (loaded[i].combatPower !== loaded[i - 1].combatPower) {
      return { previousDate: loaded[i - 1].date, currentDate: loaded[i].date };
    }
  }
  return {
    previousDate: loaded[loaded.length - 2].date,
    currentDate: loaded[loaded.length - 1].date,
  };
}

/** 인접 지점 중 전투력이 직전과 달라진 지점의 date 목록 (변화 핀) */
export function changedDates(points: HistoryPoint[]): string[] {
  const loaded = points.filter((p) => p.combatPower !== null);
  const result: string[] = [];
  for (let i = 1; i < loaded.length; i += 1) {
    if (loaded[i].combatPower !== loaded[i - 1].combatPower) result.push(loaded[i].date);
  }
  return result;
}

/** 핀을 눌렀을 때: 그 지점과 값이 있는 직전 지점 */
export function intervalEndingAt(points: HistoryPoint[], date: string): Interval | null {
  const loaded = points.filter((p) => p.combatPower !== null);
  const index = loaded.findIndex((p) => p.date === date);
  if (index < 1) return null;
  return { previousDate: loaded[index - 1].date, currentDate: date };
}

/**
 * 차트에서 점을 하나씩 고를 때. 첫 클릭은 시작점, 둘째 클릭이 구간을 완성한다.
 * 같은 점을 두 번 누르면 시작점만 남긴다. 순서는 date 로 정렬한다.
 */
export function pickPoint(
  anchor: string | null,
  date: string,
): { anchor: string | null; interval: Interval | null } {
  if (anchor === null || anchor === date) return { anchor: date, interval: null };
  const [previousDate, currentDate] = [anchor, date].sort();
  return { anchor: null, interval: { previousDate, currentDate } };
}
