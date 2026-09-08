package org.whitedoggy.mapleweb2.analysis.history;

import java.time.LocalDate;

/**
 * 추이의 한 지점.
 *
 * @param date              KST 00:00 기준 날짜
 * @param level             그 시점의 캐릭터 레벨
 * @param combatPower       우리가 다시 계산한 전투력
 * @param apiCombatPower    넥슨 API 가 준 전투력. 스냅샷이 비면 null
 * @param solErdaFragments  HEXA 코어에 그때까지 들어간 솔 에르다 조각. 헥사 강화는 전투력에
 *                          잡히지 않아 이 축으로 따로 보여준다. 문서를 못 받았거나 6차 전이면
 *                          null 이고, 0(6차인데 아직 안 올림)과 구분해야 한다.
 */
public record CombatPowerHistoryPoint(
        LocalDate date,
        Integer level,
        Long combatPower,
        Long apiCombatPower,
        Long solErdaFragments
) {
}
