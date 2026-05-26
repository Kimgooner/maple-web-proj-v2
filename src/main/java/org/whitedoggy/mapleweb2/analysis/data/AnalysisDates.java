package org.whitedoggy.mapleweb2.analysis.data;

import java.time.LocalDate;
import java.util.List;

public record AnalysisDates(
        LocalDate today,
        List<LocalDate> historicalDates
) {
}
