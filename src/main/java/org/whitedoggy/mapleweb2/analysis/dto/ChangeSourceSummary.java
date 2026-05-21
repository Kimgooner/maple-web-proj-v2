package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record ChangeSourceSummary(
        String source,
        List<StatDeltaSummary> deltas
) {
}
