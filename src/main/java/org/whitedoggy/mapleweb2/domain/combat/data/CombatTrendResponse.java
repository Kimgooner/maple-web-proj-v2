package org.whitedoggy.mapleweb2.domain.combat.data;

import java.util.List;

public record CombatTrendResponse(
        String characterName,
        String characterClass,
        List<CombatTrendPoint> recentTwoDayTrend,
        List<CombatTrendPoint> yearlyFifteenDayTrend
) {
}
