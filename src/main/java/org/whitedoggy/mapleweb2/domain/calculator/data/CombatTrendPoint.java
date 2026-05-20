package org.whitedoggy.mapleweb2.domain.calculator.data;

import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;

import java.time.LocalDate;

public record CombatTrendPoint(
        LocalDate date,
        long apiCombatPower,
        long estimatedCombatPower,
        PresetSelection selectedPresets
) {
}
