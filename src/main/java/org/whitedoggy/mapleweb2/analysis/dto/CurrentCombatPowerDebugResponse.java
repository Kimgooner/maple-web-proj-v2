package org.whitedoggy.mapleweb2.analysis.dto;

import java.time.LocalDate;
import java.util.Map;

public record CurrentCombatPowerDebugResponse(
        String ocid,
        LocalDate date,
        CharacterInfo characterInfo,
        CurrentPresetInfo currentPreset,
        Long estimatedCombatPower,
        Long currentCombatPower,
        Long difference,
        Double errorRatePercent,
        boolean lucidTransformSuspected,
        /** 이 값을 믿어도 되는지 가르는 표시들. 오차를 재기 전에 이걸로 걸러야 한다. */
        DataQuality dataQuality,
        Map<String, StatSheetSummary> statSheets
) {
    /**
     * 계산값이나 API 값을 못 믿게 만드는 사정들.
     *
     * @param inactiveCharacter 7일 미접속. API stat 문서가 마지막 접속 시점에 멈춰 있다.
     * @param incompleteSnapshot 끝내 못 받은 문서가 있었다.
     * @param weaponMissing 무기를 못 읽었다. 그 전투력은 쓸 수 없는 값이다.
     * @param unknownWeapon 표에 없는 무기라 활 환산을 못 했다.
     * @param unionRaiderDataMissing 유니온 공격대 문서가 비었다.
     */
    public record DataQuality(
            boolean inactiveCharacter,
            boolean incompleteSnapshot,
            boolean weaponMissing,
            boolean unknownWeapon,
            boolean weaponNormalizationFailed,
            boolean unionRaiderDataMissing,
            int expiredArtifactCrystals,
            int expiredCashItems,
            int expiredPetEquipments,
            boolean expiredTitleOption
    ) {
    }
}
