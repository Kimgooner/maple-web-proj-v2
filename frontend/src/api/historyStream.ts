import type { HistoryEvent, HistoryRange } from './types';

/**
 * history SSE 를 연다. 이벤트를 받는 대로 콜백에 넘기고, done/error 뒤에는 닫는다.
 * 반환값은 중단 함수. 서버는 실패를 error 이벤트로 알리고 정상 종료하므로,
 * 브라우저 EventSource 의 onerror 는 연결이 끊긴 경우로만 본다.
 */
export function openHistoryStream(
  characterName: string,
  range: HistoryRange,
  onEvent: (event: HistoryEvent) => void,
): () => void {
  const params = new URLSearchParams({ characterName, range });
  const source = new EventSource(`/api/analysis/combat-power/history/stream?${params}`);
  let closed = false;

  const close = () => {
    if (closed) return;
    closed = true;
    source.close();
  };

  const listen = (type: HistoryEvent['type'], terminal: boolean) => {
    source.addEventListener(type, (raw) => {
      if (closed) return;
      const data = JSON.parse((raw as MessageEvent<string>).data);
      onEvent({ type, data } as HistoryEvent);
      if (terminal) close();
    });
  };

  listen('meta', false);
  listen('point', false);
  listen('done', true);
  listen('error', true);

  source.onerror = () => {
    if (closed) return;
    onEvent({ type: 'error', data: { message: '서버와의 연결이 끊어졌습니다.' } });
    close();
  };

  return close;
}
