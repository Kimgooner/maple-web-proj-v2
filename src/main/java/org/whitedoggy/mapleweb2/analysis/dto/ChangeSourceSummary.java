package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record ChangeSourceSummary(
        String source,
        List<StatDeltaSummary> deltas,
        List<EntryChangeSummary> entries,
        /** 캐릭터가 아니라 넥슨 데이터가 바뀐 것일 때 줄에 붙일 말. 아니면 null. */
        String notice
) {
}
