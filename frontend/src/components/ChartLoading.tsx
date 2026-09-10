/**
 * 추이를 받는 동안 차트 자리에 두는 판.
 *
 * <p>지점이 오는 대로 선을 이어 그리면 30초 동안 그래프가 계속 모양을 바꾼다. 축이 매번
 * 다시 잡혀 선이 요동치고, 반쯤 그려진 그래프는 "이 캐릭터가 이렇게 생겼다"고 잘못 읽힌다.
 * 다 받을 때까지 진행률만 보여 주고, 완성된 그래프를 한 번에 내놓는다.
 */
export function ChartLoading({
  received,
  total,
  concurrent = 0,
}: {
  received: number;
  total: number;
  /**
   * 함께 조회 중인 사람 수(자기 포함). 0 이면 아무것도 적지 않는다.
   *
   * <p>기다림이 길어질 때만 넘긴다 - 콜드 조회 하나가 넥슨을 510회 쓰고 초당 호출에 상한이
   * 있어서, 사람이 몰리면 다 같이 느려진다. 서버가 멈춘 것과 줄을 선 것이 화면에서는
   * 똑같이 보이므로, 오래 걸릴 때는 이유를 적어 준다.
   */
  concurrent?: number;
}) {
  const percent = total > 0 ? Math.round((received / total) * 100) : 0;
  return (
    <div className="chart-loading" role="status" aria-live="polite">
      <div className="chart-loading-text">
        캐릭터의 성장 기록을 불러오고 있어요
        <span className="number">{total > 0 ? ` ${received} / ${total} 지점` : ''}</span>
      </div>
      <div className="chart-loading-track" aria-hidden="true">
        {/* 전체 지점 수를 모르는 첫 순간에는 칸을 못 채우므로 왕복하는 띠로 대신한다. */}
        <div className={total > 0 ? 'chart-loading-bar' : 'chart-loading-bar indeterminate'}
             style={total > 0 ? { width: `${percent}%` } : undefined} />
      </div>
      {concurrent >= 2 && (
        <div className="chart-loading-crowd">
          지금 <span className="number">{concurrent}</span>명이 함께 조회하고 있어요
        </div>
      )}
    </div>
  );
}
