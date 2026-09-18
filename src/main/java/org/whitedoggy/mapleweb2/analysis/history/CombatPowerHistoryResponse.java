package org.whitedoggy.mapleweb2.analysis.history;

import org.whitedoggy.mapleweb2.analysis.dto.CharacterInfo;
import org.whitedoggy.mapleweb2.analysis.dto.CurrentPresetInfo;

import java.time.LocalDate;
import java.util.List;

/**
 * 추이 조회 결과.
 *
 * @param requestedCount 구간이 원래 요청하는 지점 수(일간 30, 월간 12)
 * @param truncated      캐릭터가 없는 시점이 구간에 걸려 잘렸는가
 * @param truncatedFrom  잘려 나간 첫 날짜. 자르지 않았으면 null
 * @param points         과거 → 최신 순
 */
public record CombatPowerHistoryResponse(
        String ocid,
        String range,
        CharacterInfo characterInfo,
        /** 계산에 실제로 쓴 프리셋 번호 */
        CurrentPresetInfo preset,
        int requestedCount,
        int loadedCount,
        boolean truncated,
        LocalDate truncatedFrom,
        List<CombatPowerHistoryPoint> points
) {
}
