package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.util.List;
import java.util.Map;

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

    public long estimateCombatPower(DataSheet dataSheet, String characterClass, Integer characterLevel) {
        StatSheet sheet = dataSheet.getSumSheet();
        List<String> mainStats = gameData.mainStats(characterClass);
        List<String> subStats = gameData.subStats(characterClass);
        double finalMainStat = 0.0;
        double finalSubStat = 0.0;
        if (gameData.isDemonAvenger(characterClass)) {
            // 주스탯이 HP 다. 스탯항의 '주스탯' 자리에 HP 를 환산한 값이 들어가고,
            // 부스탯 STR 은 다른 직업과 같다. 식은 game-data.yml 의 demon-avenger 항목.
            finalMainStat = demonAvengerMainStat(dataSheet, characterLevel);
            finalSubStat = calculateStat(subStats.getFirst(), sheet, characterLevel);
        } else if (gameData.jobStatFactorOf(characterClass) != null) {
            // 제논은 주스탯이 셋이고 부스탯이 없다. 스탯항이 다른 직업의
            // (주스탯x4 + 부스탯)이 아니라 세 스탯 합에 직업별 계수를 곱한 값이다.
            // 계수는 game-data.yml 의 job-stat-factor 에 있다.
            double sum = 0.0;
            for (String stat : mainStats) {
                sum += calculateStat(stat, sheet, characterLevel);
            }
            finalMainStat = sum * gameData.jobStatFactorOf(characterClass) / 4.0;
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
        // 최종 데미지가 설명문에만 적힌 장비(루인 포스실드)를 더한다. 최종 데미지는
        // 곱연산이라 더하지 않고 곱한다 - 해방 스킬 10%와 겹치면 121%가 된다.
        for (var item : dataSheet.getItemEquip().values()) {
            Double percent = gameData.itemFinalDamageOf(item.getItemName());
            if (percent != null) {
                finalDamage *= (100.0 + percent) / 100.0;
            }
        }
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
        if (gameData.isDemonAvenger(characterClass)) {
            base *= gameData.demonAvenger().correctionOf(base);
        } else if (gameData.hasJobCorrection(characterClass)) {
            // 주스탯 계산이 다른 직업은 직업 간 비교를 위해 보정 상수가 한 번 더 곱해진다.
            base *= gameData.jobCorrectionOf(base);
        }
        return (long) Math.floor(base);
    }

    /**
     * 데몬어벤져의 주스탯. HP 를 셋으로 갈라 환산한다 — 순수 HP(기본 + 레벨 + AP)는 14당 1,
     * 그 밖의 HP(장비 절반·스킬·컨버전·의지·HP% 로 불어난 몫)는 17.5당 1, 심볼·헥사처럼
     * HP% 를 받지 않는 고정 HP 도 17.5당 1. 내림은 없다 — 넣으면 소수부와의 상관이 생기지 않는다.
     */
    private double demonAvengerMainStat(DataSheet dataSheet, Integer characterLevel) {
        GameData.DemonAvenger rule = gameData.demonAvenger();
        StatSheet sum = dataSheet.getSumSheet();
        int apHp = dataSheet.getAbilityPoint() == null ? 0 : dataSheet.getAbilityPoint().getHP();
        double pure = rule.baseHp() + (double) rule.hpPerLevel() * characterLevel + apHp;

        // 장비 HP 는 부위마다 절반(내림). 칭호만 전액이다 — 칭호 HP 1000 착용자 1,840명이 전액으로 맞는다.
        int equipmentFull = 0;
        int equipmentHalf = 0;
        for (Map.Entry<String, ItemSnapShot> entry : dataSheet.getItemEquip().entrySet()) {
            int hp = entry.getValue().getStatSheet().getHP();
            equipmentFull += hp;
            equipmentHalf += entry.getKey().endsWith("칭호") ? hp : hp / 2;
        }
        for (Map<String, ItemSnapShot> map : List.of(dataSheet.getPetEquip(), dataSheet.getCashEquip())) {
            for (ItemSnapShot item : map.values()) {
                int hp = item.getStatSheet().getHP();
                equipmentFull += hp;
                equipmentHalf += hp / 2;
            }
        }
        int setHp = dataSheet.getSetEffect() == null ? 0 : dataSheet.getSetEffect().getHP();
        equipmentFull += setHp;
        equipmentHalf += setHp / 2;

        // 종합 시트의 HP 에서 순수 HP 와 장비 HP 를 걷어내면 스킬·컨버전·의지 같은 나머지가 남는다.
        double rest = sum.getHP() - apHp - equipmentFull;
        double extraFlat = equipmentHalf + rest + rule.hpAdjustment();
        double multiplier = 1.0 + sum.getHP_PERCENT() / 100.0;
        double noPercent = sum.getHP_NO_PERCENT() + rule.noPercentHpAdjustment();

        return pure / rule.pureHpDivisor()
                + (pure * (multiplier - 1.0) + extraFlat * multiplier + noPercent) / rule.extraHpDivisor();
    }
}
