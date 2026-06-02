package org.whitedoggy.mapleweb2.analysis.dto;

import java.time.LocalDate;

public record CombatPowerDetailResponse(
        String ocid,
        LocalDate previousDate,
        LocalDate currentDate,
        CombatPowerChangeSummary changeSummary
) {
}
