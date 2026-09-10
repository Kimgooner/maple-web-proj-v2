/**
 * 아이콘이 없는 분류에 놓는 표식.
 *
 * <p>넥슨 API 가 그림을 주는 것은 스킬 · 심볼 · 헥사 코어 · 장비 · 캐시 · 펫뿐이다.
 * 세트효과 · 하이퍼스탯 · 어빌리티 · 유니온 · 레벨 상승에는 그림이 아예 없다. 관계 없는
 * 넥슨 아이콘을 아무거나 빌려다 붙이면 그림이 딴소리를 하게 되므로(기타 능력치가 결계의 핵
 * 소환 아이콘을 쓰는 것은 그 그림이 정말 그것이기 때문이다) 직접 그린 표식을 쓴다.
 *
 * <p>전부 ✦ 하나로 두면 목록을 훑을 때 분류가 구분되지 않는다. 선 하나로 그린 단순한
 * 모양이라 35px 상자에서도 뭉개지지 않고, 색은 상자의 글자색을 그대로 따라간다.
 */
const UNION = 'M2.5 2.5h6v6h-6zM11.5 2.5h6v6h-6zM2.5 11.5h6v6h-6zM11.5 11.5h6v6h-6';

const PATHS: Record<string, { d: string; label: string }> = {
  // 세트: 조각 둘이 맞물린다
  setEffect: { d: 'M3.5 3.5h8v8h-8zM8.5 8.5h8v8h-8', label: '세트효과' },
  // 하이퍼스탯: 쌓아 올린 눈금
  hyperStat: { d: 'M4 16v-4M10 16V8M16 16V4M2.5 16h15', label: '하이퍼스탯' },
  // 어빌리티: 휘장
  ability: { d: 'M10 2.5l6.5 3.5v5.5L10 17.5 3.5 11.5V6z', label: '어빌리티' },
  // 유니온: 배치판. 넷이 한 탭이지만 줄은 따로라 넷 다 같은 표식을 준다.
  unionRaider: { d: UNION, label: '유니온' },
  unionOccupied: { d: UNION, label: '유니온' },
  unionArtifact: { d: UNION, label: '유니온' },
  unionChampion: { d: UNION, label: '유니온' },
  // 레벨 상승: 위로
  abilityPoint: { d: 'M10 16.5V4M10 3.5l5 5M10 3.5l-5 5', label: '레벨 상승' },
};

export function SourceGlyph({ source }: { source: string }) {
  const mark = PATHS[source];
  if (!mark) return <>✦</>;
  return (
    <svg className="source-glyph" viewBox="0 0 20 20" role="img" aria-label={mark.label}>
      <path d={mark.d} />
    </svg>
  );
}
