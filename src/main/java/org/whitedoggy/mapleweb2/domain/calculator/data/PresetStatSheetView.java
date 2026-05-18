package org.whitedoggy.mapleweb2.domain.calculator.data;

import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.Map;

public record PresetStatSheetView(
        PresetSelection presets,
        Map<String, Integer> appliedSets,
        Map<String, StatSheet> sheets,
        StatSheet total,
        StatSheet combinedTotal,
        Long apiCombatPower,
        long estimatedCombatPower
) {
}
