package org.whitedoggy.mapleweb2.domain.combat.data;

import java.util.Map;

public record PresetStatSheetView(
        PresetSelection presets,
        Map<String, StatSheet> sheets,
        StatSheet total,
        StatSheet combinedTotal,
        Long apiCombatPower,
        long estimatedCombatPower
) {
}
