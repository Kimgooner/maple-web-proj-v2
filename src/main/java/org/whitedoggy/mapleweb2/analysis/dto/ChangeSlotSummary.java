package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record ChangeSlotSummary(
        String slot,
        String changeType,
        String previousItemName,
        String currentItemName,
        List<StatDeltaSummary> deltas
) {
}
