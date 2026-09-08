import { useCallback, useRef, useState, type CSSProperties } from 'react';

/** 팝오버 최대 폭. CSS 의 max-width 와 맞춰 둔다. */
const WIDTH = 320;
const MARGIN = 10;

/**
 * 카드에 마우스를 올렸을 때 뜨는 세부 팝오버의 자리를 잡는다.
 *
 * <p>팝오버는 {@code position: fixed} 다. 문서 흐름에 얹으면 카드 아래로 삐져나온 만큼
 * 스크롤 영역이 늘어나 화면이 밀린다. 대신 좌표를 직접 넣어야 해서 이 훅이 필요하다.
 * 아래가 좁으면 위로 뒤집고, 좌우로는 화면 안에 붙잡아 둔다.
 */
function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(value, max));
}

export function useHoverPop() {
  const anchor = useRef<HTMLDivElement>(null);
  const [popStyle, setPopStyle] = useState<CSSProperties>({});

  const place = useCallback(() => {
    const element = anchor.current;
    if (!element) return;
    const box = element.getBoundingClientRect();
    const below = window.innerHeight - box.bottom;
    const above = box.top;
    const left = clamp(box.left, MARGIN, Math.max(MARGIN, window.innerWidth - WIDTH - MARGIN));

    // 좌표는 화면 안으로 물린다. 카드가 화면 가장자리에 걸쳐 있어도 팝오버는 다 보여야 한다.
    if (below >= above) {
      const top = clamp(box.bottom, MARGIN, window.innerHeight - MARGIN);
      setPopStyle({ top, left, maxHeight: window.innerHeight - top - MARGIN });
    } else {
      const bottom = clamp(window.innerHeight - box.top, MARGIN, window.innerHeight - MARGIN);
      setPopStyle({ bottom, left, maxHeight: window.innerHeight - bottom - MARGIN });
    }
  }, []);

  return { anchor, popStyle, onMouseEnter: place };
}
