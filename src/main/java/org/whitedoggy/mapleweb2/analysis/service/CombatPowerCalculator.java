package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.support.SupportMethods;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.List;

import static org.whitedoggy.mapleweb2.domain.common.stat.JobStatTable.JOBS;

@Service
@RequiredArgsConstructor
public class CombatPowerCalculator {
    private final List<String> MAGE_CLASSES = List.of(
            "비숍",
            "아크메이지(불,독)",
            "아크메이지(썬,콜)",
            "플레임위자드",
            "배틀메이지",
            "에반",
            "일리움",
            "라라",
            "키네시스",
            "루미너스"
    );

    private boolean isMageClass(String characterClass) {
        return MAGE_CLASSES.stream().anyMatch(characterClass::contains);
    }

    private Integer statPerLevel(String statName, StatSheet sheet, Integer characterLevel) {
        switch (statName) {
            case "STR" -> {
                return (characterLevel / 9) * sheet.getSTR_PER_LEVEL9();
            }
            case "DEX" -> {
                return (characterLevel / 9) * sheet.getDEX_PER_LEVEL9();
            }
            case "LUK" -> {
                return (characterLevel / 9) * sheet.getLUK_PER_LEVEL9();
            }
            case "INT" -> {
                return (characterLevel / 9) * sheet.getINT_PER_LEVEL9();
            }
        }
        return 0;
    }

    private Integer calculateStat(String statName, StatSheet sheet, Integer characterLevel) {
        switch (statName) {
            case "STR" -> {
                return (int) Math.floor((((sheet.getSTR() + statPerLevel(statName, sheet, characterLevel) + sheet.getALL_STAT()) * (100.0 + sheet.getSTR_PERCENT() + sheet.getALL_STAT_PERCENT())) / 100.0) + sheet.getSTR_NO_PERCENT() + sheet.getALL_STAT_NO_PERCENT());
            }
            case "DEX" -> {
                return (int) Math.floor((((sheet.getDEX() + statPerLevel(statName, sheet, characterLevel) + sheet.getALL_STAT()) * (100.0 + sheet.getDEX_PERCENT() + sheet.getALL_STAT_PERCENT())) / 100.0) + sheet.getDEX_NO_PERCENT() + sheet.getALL_STAT_NO_PERCENT());
            }
            case "LUK" -> {
                return (int) Math.floor((((sheet.getLUK() + statPerLevel(statName, sheet, characterLevel) + sheet.getALL_STAT()) * (100.0 + sheet.getLUK_PERCENT() + sheet.getALL_STAT_PERCENT())) / 100.0) + sheet.getLUK_NO_PERCENT() + sheet.getALL_STAT_NO_PERCENT());
            }
            case "INT" -> {
                return (int) Math.floor((((sheet.getINT() + statPerLevel(statName, sheet, characterLevel) + sheet.getALL_STAT()) * (100.0 + sheet.getINT_PERCENT() + sheet.getALL_STAT_PERCENT())) / 100.0) + sheet.getINT_NO_PERCENT() + sheet.getALL_STAT_NO_PERCENT());
            }
        }
        return 0;
    }

    private long estimateCombatPower(DataSheet dataSheet) {
        String characterClass = dataSheet.getCharacterClass();
        Integer characterLevel = dataSheet.getCharacterLevel();
        StatSheet sheet = dataSheet.getSumSheet();
        List<String> mainStats = SupportMethods.getMainStat(characterClass);
        List<String> subStats = SupportMethods.getSubStat(characterClass);
        double finalMainStat = 0.0;
        double finalSubStat = 0.0;
        if (characterClass.equals("데몬어벤져")) {
            //TODO 데벤
        } else if (characterClass.equals("제논")) {
            //TODO 제논
        } else {
            String main = mainStats.getFirst();
            finalMainStat = calculateStat(main, sheet, characterLevel);
            if (subStats.size() == 2) {
                String sub1 = subStats.getFirst();
                String sub2 = subStats.getLast();
                System.out.println(calculateStat(sub1, sheet, characterLevel));
                System.out.println(calculateStat(sub2, sheet, characterLevel));
                finalSubStat = calculateStat(sub1, sheet, characterLevel) + calculateStat(sub2, sheet, characterLevel);

            } else {
                String sub = subStats.getFirst();
                finalSubStat = calculateStat(sub, sheet, characterLevel);
            }
        }
        double finalStat = ((finalMainStat * 4) + finalSubStat) / 100.0;
        double power;
        if (isMageClass(characterClass)) {
            power = Math.floor((sheet.getMAGIC_POWER() * (100.0 + sheet.getMAGIC_POWER_PERCENT())) / 100.0);
        } else {
            power = Math.floor((sheet.getATTACK_POWER() * (100.0 + sheet.getATTACK_POWER_PERCENT())) / 100.0);
        }
        double damage = 100.0 + sheet.getDAMAGE() + sheet.getBOSS_DAMAGE();
        double critDamage = 135.0 + sheet.getCRITICAL_DAMAGE();
        double finalDamage = 100.0 + sheet.getFINAL_DAMAGE();
        System.out.println("전투력 계산 ==============");
        System.out.println("최종 주스탯: " + finalMainStat);
        System.out.println("최종 부스탯: " + finalSubStat);
        System.out.println("스탯: " + finalStat);
        System.out.println("공격력/마력: " + power);
        System.out.println("데미지: " + damage);
        System.out.println("크리티컬 데미지: " + critDamage);
        System.out.println("최종 데미지: " + finalDamage);
        System.out.println("========================");
        return (long) Math.floor((finalStat * power * damage * critDamage * finalDamage) / 1_000_000.0);
    }
}
