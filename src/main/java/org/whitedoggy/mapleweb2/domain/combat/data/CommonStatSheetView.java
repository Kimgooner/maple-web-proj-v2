package org.whitedoggy.mapleweb2.domain.combat.data;

import java.util.Map;

public record CommonStatSheetView(
        Map<String, StatSheet> sheets,
        StatSheet total
) {
}
