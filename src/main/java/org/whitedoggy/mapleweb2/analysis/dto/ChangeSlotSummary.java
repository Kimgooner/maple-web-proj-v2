package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record ChangeSlotSummary(
        String slot,
        String previousSlot,
        String currentSlot,
        String changeType,
        String previousItemName,
        String currentItemName,
        String previousItemIcon,
        String currentItemIcon,
        List<StatDeltaSummary> deltas,
        List<StatDeltaGroupSummary> deltaGroups
) {
}
