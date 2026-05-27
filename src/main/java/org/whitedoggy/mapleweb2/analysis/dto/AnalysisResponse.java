package org.whitedoggy.mapleweb2.analysis.dto;

import java.util.List;

public record AnalysisResponse(
        String ocid,
        String characterName,
        String characterClass,
        Integer characterLevel,
        String characterGuild,
        String characterWorld,
        String characterImage,
        List<DataSheetByDate> entries
) {
}
