package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CombatCalculationService {
    private final GameData gameData;

    private boolean isMageClass(String characterClass) {
        return gameData.isMageClass(characterClass);
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

    /**
     * 제논의 스탯항 계수. 스탯항 = (STR + DEX + LUK) x 3.5 다.
     *
     * <p>3.5 = 4 x 0.875 이고 0.875는 제논 직업상수다. 제논은 STR/DEX/LUK이 모두
     * 주스탯이고 부스탯이 없어 일반 직업의 (주스탯 x 4 + 부스탯) 자리에 이 값이 들어간다.
     *
     * <p>여기까지가 '보정 전 전투력'이고, 여기에 직업 보정 상수가 한 번 더 곱해진다
     * ({@code game-data.yml}의 {@code job-correction}). 보정 전 전투력이 1억 이상이면
     * 상수가 0.75로 고정이라 3.5 x 0.75 = 2.625가 되는데, 예전에 쓰던 2.625가 바로 이
     * 구간의 값이었다. 그 아래에서는 상수가 커지므로 2.625 고정으로는 맞지 않는다.
     */
    private static final double XENON_STAT_FACTOR = 3.5;

    public long estimateCombatPower(DataSheet dataSheet, String characterClass, Integer characterLevel) {
        StatSheet sheet = dataSheet.getSumSheet();
        List<String> mainStats = gameData.mainStats(characterClass);
        List<String> subStats = gameData.subStats(characterClass);
        double finalMainStat = 0.0;
        double finalSubStat = 0.0;
        if (characterClass.equals("데몬어벤져")) {
            //TODO 데벤
        } else if (characterClass.equals("제논")) {
            // 제논은 주스탯이 셋이고 부스탯이 없다. 스탯항이 다른 직업의
            // (주스탯x4 + 부스탯)이 아니라 세 스탯 합의 XENON_STAT_FACTOR 배다.
            double sum = 0.0;
            for (String stat : mainStats) {
                sum += calculateStat(stat, sheet, characterLevel);
            }
            finalMainStat = sum * XENON_STAT_FACTOR / 4.0;
        } else {
            String main = mainStats.getFirst();
            finalMainStat = calculateStat(main, sheet, characterLevel);
            if (subStats.size() == 2) {
                String sub1 = subStats.getFirst();
                String sub2 = subStats.getLast();
                //System.out.println(calculateStat(sub1, sheet, characterLevel));
                //System.out.println(calculateStat(sub2, sheet, characterLevel));
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
        /*
        System.out.println("전투력 계산 ==============");
        System.out.println("최종 주스탯: " + finalMainStat);
        System.out.println("최종 부스탯: " + finalSubStat);
        System.out.println("스탯: " + finalStat);
        System.out.println("공격력/마력: " + power);
        System.out.println("데미지: " + damage);
        System.out.println("크리티컬 데미지: " + critDamage);
        System.out.println("최종 데미지: " + finalDamage);
        System.out.println("========================");
        */
        double base = (finalStat * power * damage * critDamage * finalDamage) / 1_000_000.0;
        if (gameData.hasJobCorrection(characterClass)) {
            // 주스탯 계산이 다른 직업은 직업 간 비교를 위해 보정 상수가 한 번 더 곱해진다.
            base *= gameData.jobCorrectionOf(base);
        }
        return (long) Math.floor(base);
    }
}
