/**
 * MapleDelta 마크. 오르는 선과 그 끝의 점 — 추이 차트의 선을 그대로 떼어 온 모양이다.
 *
 * <p>`public/favicon.svg` 와 같은 도형이니 모양을 바꿀 때 둘을 같이 손댄다.
 * 색은 주변 글자색을 따라간다(currentColor) - 브랜드 색은 `.brand-mark` 가 정한다.
 */
export function BrandMark() {
  return (
    <svg className="brand-svg" viewBox="0 0 32 32" role="img" aria-label="MapleDelta">
      <path
        fill="none"
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M4 24 12 15.5 18 20.5 27 8.5"
      />
      <circle cx="27" cy="8.5" r="4" fill="currentColor" />
    </svg>
  );
}
