package org.whitedoggy.mapleweb2.validation.dto;

import java.util.List;

public record CombatPowerValidationGroup(
        String groupName,
        List<CombatPowerValidationResult> results
) {
}
