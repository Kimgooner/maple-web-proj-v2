package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record CombatPowerChangeSummary(
        List<ChangeSourceSummary> coreChanges,
        List<ChangeSlotSummary> petChanges,
        List<ChangeSlotSummary> cashChanges,
        List<ChangeSlotSummary> itemChanges
) {
}
