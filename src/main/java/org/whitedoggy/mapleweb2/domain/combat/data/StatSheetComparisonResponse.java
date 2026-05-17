package org.whitedoggy.mapleweb2.domain.combat.data;

import java.time.LocalDate;

public record StatSheetComparisonResponse(
        String characterName,
        String characterClass,
        LocalDate date,
        CommonStatSheetView shared,
        PresetStatSheetView currentPreset,
        PresetStatSheetView combatPreset
) {
}
