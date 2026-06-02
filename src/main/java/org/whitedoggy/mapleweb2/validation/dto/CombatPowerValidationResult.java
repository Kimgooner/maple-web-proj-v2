package org.whitedoggy.mapleweb2.validation.dto;

public record CombatPowerValidationResult(
        String characterName,
        String worldName,
        String className,
        Integer characterLevel,
        Long estimatedCombatPower,
        Long currentCombatPower,
        Long difference,
        Double errorRatePercent,
        boolean lucidTransformSuspected,
        String errorMessage
) {
}
