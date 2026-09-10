package org.whitedoggy.mapleweb2.analysis.dto;

public record StatSheetSummary(
        int str,
        int dex,
        int intStat,
        int luk,
        int hp,
        int allStat,
        int strPerLevel9,
        int dexPerLevel9,
        int intPerLevel9,
        int lukPerLevel9,
        int strNoPercent,
        int dexNoPercent,
        int intNoPercent,
        int lukNoPercent,
        int hpNoPercent,
        int allStatNoPercent,
        int attackPower,
        int magicPower,
        int strPercent,
        int dexPercent,
        int intPercent,
        int lukPercent,
        int hpPercent,
        int allStatPercent,
        int attackPowerPercent,
        int magicPowerPercent,
        double damage,
        double bossDamage,
        double criticalDamage,
        double finalDamage,
        /** 전투력에는 안 들어가는 값. 실전에서 갈리는 것이라 화면에만 싣는다. */
        int cooldownSecond,
        double cooldownSkipPercent
) {
}
