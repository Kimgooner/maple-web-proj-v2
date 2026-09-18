package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.dto.CombatPowerBreakdown;
import org.whitedoggy.mapleweb2.analysis.dto.StatSheetSummary;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.util.ArrayList;
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
        return terms(dataSheet, characterClass, characterLevel).combatPower();
    }

    /**
     * 전투력과 그것을 이루는 항들, 그리고 소스별 구성 비율. 화면이 "어떻게 계산됐나"를 이 값으로 적는다.
     *
     * <p>전투력이 곱이라 "장비가 몇 %" 를 항에서 바로 나눠 줄 수 없다. 그래서 섀플리 값으로 잰다 —
     * 소스를 아무 차례로나 하나씩 더해 갈 때 그 소스가 늘린 전투력을 모든 차례에 대해 평균한 것.
     * 차례에 기대지 않고, 전부 더하면 정확히 전투력이 되어 비율이 100% 로 떨어진다. 소스가
     * 열여덟이면 조합 2^18 = 26만 가지를 다 계산한다. 소스별 시트를 미리 합쳐 두고 종합만 갈아
     * 끼우면 조합 하나가 시트 열여덟 장 더하기라 전부 해도 100ms 안이다.
     */
    public CombatPowerBreakdown breakdown(DataSheet dataSheet, String characterClass, Integer characterLevel) {
        CombatPowerBreakdown terms = terms(dataSheet, characterClass, characterLevel);
        Map<String, StatSheet> parts = dataSheet.contributions();
        List<String> names = List.copyOf(parts.keySet());
        double[] shapley = shapley(dataSheet, names, parts, characterClass, characterLevel);
        List<CombatPowerBreakdown.SourceShare> sources = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            double share = terms.combatPower() == 0 ? 0.0 : shapley[i] * 100.0 / terms.combatPower();
            sources.add(new CombatPowerBreakdown.SourceShare(
                    names.get(i), StatSheetSummary.of(parts.get(names.get(i))), share));
        }
        return new CombatPowerBreakdown(
                terms.mainStat(), terms.subStat(), terms.statTerm(), terms.usesMagic(), terms.power(),
                terms.damage(), terms.bossDamage(), terms.criticalDamage(), terms.finalDamage(),
                terms.correction(), terms.combatPower(),
                List.copyOf(sources), StatSheetSummary.of(dataSheet.getSumSheet()));
    }

    /**
     * 소스별 섀플리 값. {@code v[mask]} 는 그 조합만 넣은 전투력이고,
     * φ_i = Σ_{S∌i} |S|!(n-|S|-1)!/n! · (v(S∪{i}) − v(S)).
     *
     * <p>데몬어벤져는 주스탯이 종합 시트가 아니라 장비 목록·세트·AP 에서 직접 나오므로 종합만
     * 갈아 끼울 수 없다. 그 직업만 소스를 비운 사본을 만들어 다시 합친다 — 느리지만 한 직업이고
     * 오늘 앞머리는 5분 캐시라 견딜 만하다.
     */
    private double[] shapley(DataSheet dataSheet, List<String> names, Map<String, StatSheet> parts,
                             String characterClass, Integer characterLevel) {
        int n = names.size();
        boolean demonAvenger = gameData.isDemonAvenger(characterClass);
        int itemsBit = names.indexOf("items");
        long[] v = new long[1 << n];
        for (int mask = 1; mask < (1 << n); mask++) {
            DataSheet variant;
            if (demonAvenger) {
                java.util.Set<String> kept = new java.util.HashSet<>();
                for (int i = 0; i < n; i++) {
                    if ((mask & (1 << i)) != 0) kept.add(names.get(i));
                }
                variant = dataSheet.keeping(kept);
            } else {
                StatSheet sum = new StatSheet("종합");
                for (int i = 0; i < n; i++) {
                    if ((mask & (1 << i)) != 0) sum.merge(parts.get(names.get(i)));
                }
                variant = dataSheet.withSum(sum, itemsBit < 0 || (mask & (1 << itemsBit)) != 0);
            }
            v[mask] = terms(variant, characterClass, characterLevel).combatPower();
        }
        // 조합 크기별 가중치 |S|!(n-|S|-1)!/n!. 팩토리얼을 직접 곱하면 넘치므로 비율로 쌓는다.
        double[] weight = new double[n];
        for (int s = 0; s < n; s++) {
            double w = 1.0 / n;
            for (int k = 1; k <= s; k++) {
                w *= (double) k / (n - k);
            }
            weight[s] = w;
        }
        double[] phi = new double[n];
        for (int mask = 0; mask < (1 << n); mask++) {
            int size = Integer.bitCount(mask);
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) == 0) {
                    phi[i] += weight[size] * (v[mask | (1 << i)] - v[mask]);
                }
            }
        }
        return phi;
    }

    /** 식의 항과 전투력만. 소스별 몫은 비어 있다. */
    private CombatPowerBreakdown terms(DataSheet dataSheet, String characterClass, Integer characterLevel) {
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
        Double correction = null;
        if (gameData.isDemonAvenger(characterClass)) {
            correction = gameData.demonAvenger().correctionOf(base);
        } else if (gameData.hasJobCorrection(characterClass)) {
            // 주스탯 계산이 다른 직업은 직업 간 비교를 위해 보정 상수가 한 번 더 곱해진다.
            correction = gameData.jobCorrectionOf(base);
        }
        if (correction != null) {
            base *= correction;
        }
        return new CombatPowerBreakdown(
                finalMainStat, finalSubStat, finalStat, isMageClass(characterClass), power,
                sheet.getDAMAGE(), sheet.getBOSS_DAMAGE(), sheet.getCRITICAL_DAMAGE(),
                finalDamage - 100.0, correction, (long) Math.floor(base), List.of(), null);
    }

    /** 부위 HP 절반. 파서가 소스별로 내린 값이 있으면 그것, 없으면(펫·캐시) 부위 통째 내림. */
    private static int halvedHp(ItemSnapShot item) {
        Integer halved = item.getHpHalvedForDemonAvenger();
        return halved != null ? halved : item.getStatSheet().getHP() / 2;
    }

    /**
     * 데몬어벤져의 주스탯. HP 를 셋으로 갈라 환산한다 — 순수 HP(기본 + 레벨 + AP)는 14당 1,
     * 그 밖의 HP(장비 절반·스킬·컨버전·의지·HP% 로 불어난 몫)는 17.5당 1, 심볼·헥사처럼
     * HP% 를 받지 않는 고정 HP 도 17.5당 1. 스탯 자체는 내림 없이 소수로 둔다(넣으면 소수부와의 상관이
     * 생기지 않는다). 유일한 정수화는 HP% 를 곱한 총 HP 의 반올림이다.
     */
    private double demonAvengerMainStat(DataSheet dataSheet, Integer characterLevel) {
        GameData.DemonAvenger rule = gameData.demonAvenger();
        StatSheet sum = dataSheet.getSumSheet();
        int apHp = dataSheet.getAbilityPoint() == null ? 0 : dataSheet.getAbilityPoint().getHP();
        double pure = rule.baseHp() + (double) rule.hpPerLevel() * characterLevel + apHp;

        // 장비 HP 는 절반(내림). 내림 단위는 ItemSnapShot.hpHalvedForDemonAvenger 참고. 칭호만 전액이다 —
        // 칭호 HP 1000 착용자 1,840명이 전액으로 맞는다.
        int equipmentFull = 0;
        int equipmentHalf = 0;
        for (Map.Entry<String, ItemSnapShot> entry : dataSheet.getItemEquip().entrySet()) {
            int hp = entry.getValue().getStatSheet().getHP();
            equipmentFull += hp;
            equipmentHalf += entry.getKey().endsWith("칭호") ? hp : halvedHp(entry.getValue());
        }
        for (Map<String, ItemSnapShot> map : List.of(dataSheet.getPetEquip(), dataSheet.getCashEquip())) {
            for (ItemSnapShot item : map.values()) {
                int hp = item.getStatSheet().getHP();
                equipmentFull += hp;
                equipmentHalf += halvedHp(item);
            }
        }
        // 세트 효과 HP 도 절반인데 옵션 줄마다 내림된다 (DataSheetService.setEffectHpHalved).
        int setHp = dataSheet.getSetEffect() == null ? 0 : dataSheet.getSetEffect().getHP();
        double equipmentHalfTotal = equipmentHalf + dataSheet.getSetEffectHpHalved();
        equipmentFull += setHp;

        // 종합 시트의 HP 에서 순수 HP 와 장비 HP 를 걷어내면 스킬·컨버전·의지 같은 나머지가 남는다.
        double rest = sum.getHP() - apHp - equipmentFull;
        double extraFlat = equipmentHalfTotal + rest + rule.hpAdjustment();
        double multiplier = 1.0 + sum.getHP_PERCENT() / 100.0;
        double noPercent = sum.getHP_NO_PERCENT() + rule.noPercentHpAdjustment();

        // HP% 를 곱한 총 HP 는 게임이 정수로 내린다 (2,894명에서 내림 29 / 반올림 15 정수 일치).
        double totalWithPercent = Math.floor((pure + extraFlat) * multiplier);
        double extraHp = totalWithPercent + noPercent - pure;

        return pure / rule.pureHpDivisor() + extraHp / rule.extraHpDivisor();
    }
}
