import { useEffect, useReducer } from 'react';
import { openHistoryStream } from '../api/historyStream';
import type { HistoryRange } from '../api/types';
import { applyHistoryEvent, loadingHistoryState, type HistoryState } from './history';

/** 한 구간의 추이 스트림. 이름·재시도 횟수가 바뀌면 새로 연다. */
export function useHistory(name: string, range: HistoryRange, retry: number): HistoryState {
  const [state, dispatch] = useReducer(applyHistoryEvent, undefined, loadingHistoryState);

  useEffect(() => {
    if (!name) return;
    dispatch({ type: 'error', data: { message: '' } }); // 이전 상태 지우기용. 바로 meta 가 덮어쓴다
    return openHistoryStream(name, range, dispatch);
  }, [name, range, retry]);

  return state;
}
