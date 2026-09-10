import { useCallback, useEffect, useReducer } from 'react';
import { openHistoryStream } from '../api/historyStream';
import type { HistoryPoint, HistoryRange } from '../api/types';
import { applyHistoryEvent, loadingHistoryState, type HistoryState } from './history';

/**
 * 한 구간의 추이 스트림. 이름·재시도 횟수가 바뀌면 새로 연다.
 *
 * <p>{@code replacePoints} 는 프리셋을 되돌려 다시 계산한 결과로 갈아 끼울 때 쓴다.
 * 스트림을 다시 열지 않고 지점만 바꾼다 - 캐릭터도 구간도 그대로이기 때문이다.
 */
export function useHistory(
  name: string,
  range: HistoryRange,
  retry: number,
): [HistoryState, (points: HistoryPoint[]) => void] {
  const [state, dispatch] = useReducer(applyHistoryEvent, undefined, loadingHistoryState);

  useEffect(() => {
    if (!name) return;
    dispatch({ type: 'reset' });
    return openHistoryStream(name, range, dispatch);
  }, [name, range, retry]);

  const replacePoints = useCallback(
    (points: HistoryPoint[]) => dispatch({ type: 'repaired', points }),
    [],
  );
  return [state, replacePoints];
}
