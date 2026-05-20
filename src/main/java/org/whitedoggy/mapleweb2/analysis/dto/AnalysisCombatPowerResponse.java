package org.whitedoggy.mapleweb2.analysis.dto;

import java.time.LocalDate;

public record AnalysisCombatPowerResponse(
        String characterName,
        String characterClass,
        LocalDate date,
        CombatPowerSummary currentPresetDataSheet,
        CombatPowerSummary combatPresetDataSheet
) {
}
