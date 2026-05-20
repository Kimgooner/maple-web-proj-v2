package org.whitedoggy.mapleweb2.analysis.dto;

public record CombatPowerSummary(
        long estimatedCombatPower,
        Long currentCombatPower,
        Long difference,
        Double errorRatePercent,
        boolean lucidTransformSuspected
) {
}
