package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record AnalysisResponse(
        String ocid,
        CharacterInfo characterInfo,
        List<DataSheetByDate> entries
) {
}
