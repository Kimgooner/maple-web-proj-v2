package org.whitedoggy.mapleweb2.analysis.history;

import java.time.LocalDate;

/**
 * 추이의 한 지점.
 *
 * @param date           KST 00:00 기준 날짜
 * @param level          그 시점의 캐릭터 레벨
 * @param combatPower    우리가 다시 계산한 전투력
 * @param apiCombatPower 넥슨 API 가 준 전투력. 스냅샷이 비면 null
 */
public record CombatPowerHistoryPoint(
        LocalDate date,
        Integer level,
        Long combatPower,
        Long apiCombatPower
) {
}
