/**
 * 추이를 받는 동안 차트 자리에 두는 판.
 *
 * <p>지점이 오는 대로 선을 이어 그리면 30초 동안 그래프가 계속 모양을 바꾼다. 축이 매번
 * 다시 잡혀 선이 요동치고, 반쯤 그려진 그래프는 "이 캐릭터가 이렇게 생겼다"고 잘못 읽힌다.
 * 다 받을 때까지 진행률만 보여 주고, 완성된 그래프를 한 번에 내놓는다.
 */
export function ChartLoading({ received, total }: { received: number; total: number }) {
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
    </div>
  );
}
