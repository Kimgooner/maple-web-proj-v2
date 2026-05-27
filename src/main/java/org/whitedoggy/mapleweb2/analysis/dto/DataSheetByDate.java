package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.analysis.data.DataSheet;

import java.time.LocalDate;

public record DataSheetByDate(
        LocalDate date,
        DataSheet dataSheet
) {
}
