package org.whitedoggy.mapleweb2.validation.dto;

import java.time.LocalDate;
import java.util.List;

public record CombatPowerValidationResponse(
        LocalDate date,
        List<CombatPowerValidationGroup> groups
) {
}
