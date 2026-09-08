package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

/** 옵션 / 잠재 / 익셉셔널처럼 갈라 놓은 스탯 변화 한 묶음. 비어 있는 종류는 담지 않는다. */
public record StatDeltaGroupSummary(
        String category,
        List<StatDeltaSummary> deltas
) {
}
