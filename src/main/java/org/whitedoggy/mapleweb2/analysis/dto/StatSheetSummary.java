package org.whitedoggy.mapleweb2.analysis.dto;

import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

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
    public static StatSheetSummary of(StatSheet sheet) {
        return new StatSheetSummary(
                sheet.getSTR(), sheet.getDEX(), sheet.getINT(), sheet.getLUK(), sheet.getHP(), sheet.getALL_STAT(),
                sheet.getSTR_PER_LEVEL9(), sheet.getDEX_PER_LEVEL9(), sheet.getINT_PER_LEVEL9(), sheet.getLUK_PER_LEVEL9(),
                sheet.getSTR_NO_PERCENT(), sheet.getDEX_NO_PERCENT(), sheet.getINT_NO_PERCENT(), sheet.getLUK_NO_PERCENT(),
                sheet.getHP_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT(),
                sheet.getATTACK_POWER(), sheet.getMAGIC_POWER(),
                sheet.getSTR_PERCENT(), sheet.getDEX_PERCENT(), sheet.getINT_PERCENT(), sheet.getLUK_PERCENT(),
                sheet.getHP_PERCENT(), sheet.getALL_STAT_PERCENT(),
                sheet.getATTACK_POWER_PERCENT(), sheet.getMAGIC_POWER_PERCENT(),
                sheet.getDAMAGE(), sheet.getBOSS_DAMAGE(), sheet.getCRITICAL_DAMAGE(), sheet.getFINAL_DAMAGE(),
                sheet.getCOOLDOWN_SECOND(), sheet.getCOOLDOWN_SKIP_PERCENT());
    }
}
