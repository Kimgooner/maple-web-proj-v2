package org.whitedoggy.mapleweb2.analysis.dto;

import java.time.LocalDate;

public record AnalysisCombatPowerResponse(
        LocalDate date,
        CombatPowerSummary currentPresetDataSheet,
        CombatPowerSummary combatPresetDataSheet
) {
}
