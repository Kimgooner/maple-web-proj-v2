package org.whitedoggy.mapleweb2.domain.combat.data;

import java.time.LocalDate;

public record CombatTrendPoint(
        LocalDate date,
        long apiCombatPower,
        long estimatedCombatPower,
        PresetSelection selectedPresets
) {
}
