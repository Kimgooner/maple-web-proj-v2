package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.analysis.data.DataSheet;

public record DataSheetResponse(
        DataSheet CurrentPresetDataSheet,
        DataSheet CombatPresetdataSheet
) {
}
