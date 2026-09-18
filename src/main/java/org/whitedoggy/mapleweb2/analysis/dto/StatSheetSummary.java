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
        double strPercent,
        double dexPercent,
        double intPercent,
        double lukPercent,
        double hpPercent,
        double allStatPercent,
        double attackPowerPercent,
        double magicPowerPercent,
        double damage,
        double bossDamage,
        double criticalDamage,
        double finalDamage,
        /** 전투력에는 안 들어가는 값. 실전에서 갈리는 것이라 화면에만 싣는다. */
        int cooldownSecond,
        double cooldownSkipPercent
) {
}
