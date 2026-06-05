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
        Map<String, StatSheetSummary> statSheets
) {
}
