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
 * @param solErdaFragmentsRequired 지금 가진 코어를 모두 만렙까지 올리는 데 드는 조각.
 *                          화면이 조각 축을 0~100%로 그릴 때의 분모다. 코어를 새로 열면 늘어난다.
 * @param cooldownSecond    스킬 재사용 대기시간 감소(초). 모자 잠재에서 온다.
 * @param cooldownSkipPercent 스킬 사용 시 재사용 대기시간이 통째로 미적용될 확률(%). 어빌리티에서 온다.
 *                          둘 다 전투력에는 안 들어가지만 실전에서 크게 갈려 화면에 따로 싣는다.
 *                          시트를 못 만든 지점은 null 이고, 0(없음)과 구분해야 한다.
 * @param expired           기간이 지나 스탯이 빠진 것들. 화면이 "만료됨"으로 알린다.
 */
public record CombatPowerHistoryPoint(
        LocalDate date,
        Integer level,
        Long combatPower,
        Long apiCombatPower,
        Long solErdaFragments,
        Long solErdaFragmentsRequired,
        Integer cooldownSecond,
        Double cooldownSkipPercent,
        Expired expired
) {
    /**
     * 기간이 지나 계산에서 빠진 항목 수. 전부 0이면 {@code null} 로 두어 캐시와 응답이
     * 괜히 커지지 않게 한다.
     *
     * @param titleOption 칭호의 옵션 기간이 지났는가
     */
    public record Expired(
            int artifactCrystals,
            int cashItems,
            int petEquipments,
            boolean titleOption
    ) {
        public static Expired of(int artifactCrystals, int cashItems, int petEquipments, boolean titleOption) {
            boolean none = artifactCrystals == 0 && cashItems == 0 && petEquipments == 0 && !titleOption;
            return none ? null : new Expired(artifactCrystals, cashItems, petEquipments, titleOption);
        }
    }
}
