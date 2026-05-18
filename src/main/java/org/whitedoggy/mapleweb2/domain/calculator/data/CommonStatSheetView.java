package org.whitedoggy.mapleweb2.domain.calculator.data;

import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.Map;

public record CommonStatSheetView(
        Map<String, StatSheet> sheets,
        StatSheet total
) {
}
