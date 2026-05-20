package org.whitedoggy.mapleweb2.domain.calculator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterSnapshot;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.cash.CashItemParser;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.domain.skill.SkillParser;
import org.whitedoggy.mapleweb2.domain.symbol.SymbolParser;
import org.whitedoggy.mapleweb2.domain.union.artifact.ArtifactParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.union.champion.ChampionParser;
import org.whitedoggy.mapleweb2.domain.calculator.data.*;
import org.whitedoggy.mapleweb2.domain.calculator.parser.*;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemParser;
import org.whitedoggy.mapleweb2.domain.pet.PetParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.floor;
import static org.whitedoggy.mapleweb2.domain.common.stat.JobStatTable.JOBS;

@Service
@RequiredArgsConstructor
public class StatSheetComparisonService {
    private static final List<String> MAGE_CLASSES = List.of(
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

    private final NexonApiClient nexonApiClient;
    private final BasicParser basicParser;
    private final StatParser statParser;
    private final ItemEquipmentParser itemEquipmentParser;
    private final ItemParser itemParser;
    private final AbilityParser abilityParser;
    private final HyperStatParser hyperStatParser;
    private final RaiderParser raiderParser;
    private final SymbolParser symbolParser;
    private final PetParser petParser;
    private final SkillParser skillParser;
    private final ArtifactParser artifactParser;
    private final ChampionParser championParser;
    private final SetEffectParser setEffectParser;
    private final StatSheetParser statSheetParser;
    private final HexaParser hexaParser;
    private final CashItemParser cashItemParser;

    public Mono<StatSheetComparisonResponse> getStatSheets(String characterName, LocalDate date) {
        return nexonApiClient.getOcid(characterName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                .flatMap(ocidResponse -> nexonApiClient.fetchSnapshot(ocidResponse.ocid(), date))
                .map(snapshot -> buildResponse(characterName, date, snapshot));
    }

    private StatSheetComparisonResponse buildResponse(String characterName, LocalDate date, CharacterSnapshot snapshot) {
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        JsonNode stat = snapshot.document(NexonEndpoint.STAT);
        JsonNode itemEquipment = snapshot.document(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = snapshot.document(NexonEndpoint.ABILITY);
        JsonNode hyperStat = snapshot.document(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = snapshot.document(NexonEndpoint.UNION_RAIDER);

        String characterClass = basicParser.characterClass(basic);
        Integer characterLevel = basicParser.characterLevel(basic);
        List<String> mainStat = JOBS.get(characterClass).mainStats();
        List<String> subStat = JOBS.get(characterClass).subStats();

        CommonStatSheetView sharedView = buildSharedView(snapshot, mainStat, characterClass, characterLevel);

        PresetSelection currentPreset = new PresetSelection(
                itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(1),
                abilityParser.getCurrentPresetAbility(ability),
                hyperStatParser.getCurrentPresetNo(hyperStat),
                chooseCurrentUnionPreset(unionRaider)
        );

        PresetSelection combatPreset = new PresetSelection(
                chooseItemPreset(itemEquipment),
                chooseAbilityPreset(ability, characterClass),
                chooseHyperPreset(hyperStat),
                chooseUnionPreset(unionRaider)
        );

        Long apiCombatPower = Optional.ofNullable(statParser.currentCombatPower(stat))
                .map(Jsons::parseDouble)
                .map(Math::floor)
                .map(Double::longValue)
                .orElse(null);

        return new StatSheetComparisonResponse(
                characterName,
                characterClass,
                date,
                sharedView,
                buildPresetView(snapshot, sharedView.total(), characterClass, currentPreset, apiCombatPower, mainStat, subStat, characterLevel),
                buildPresetView(snapshot, sharedView.total(), characterClass, combatPreset, null, mainStat, subStat, characterLevel)
        );
    }

    private StatSheet buildAbilityPointStat(String characterClass, Integer characterLevel, List<String> mainStat) {
        StatSheet apSheet = new StatSheet("abilityPoint");
        if(characterClass.equals("제논")){
            return apSheet;
        }
        if(characterClass.equals("데몬어벤져")){
            return apSheet;
        }
        String main = mainStat.getFirst();
        apSheet.setSTR(4);
        apSheet.setDEX(4);
        apSheet.setLUK(4);
        apSheet.setINT(4);
        switch(main){
            case "STR" -> apSheet.setSTR(14 + characterLevel * 5 + apSheet.getSTR());
            case "DEX" -> apSheet.setDEX(14 + characterLevel * 5 + apSheet.getDEX());
            case "LUK" -> apSheet.setLUK(14 + characterLevel * 5 + apSheet.getLUK());
            case "INT" -> apSheet.setINT(14 + characterLevel * 5 + apSheet.getINT());
        }
        return apSheet;
    }

    private CommonStatSheetView buildSharedView(CharacterSnapshot snapshot, List<String> mainStats, String characterClass, Integer characterLevel) {
        Map<String, StatSheet> sheets = new LinkedHashMap<>();
        sheets.put("levelPoint", buildAbilityPointStat(characterClass, characterLevel, mainStats));
        sheets.put("symbol", statSheetParser.parseNoPercentStat(symbolParser.getSymbolStatEffects(snapshot.document(NexonEndpoint.SYMBOL_EQUIPMENT)), "심볼"));
        sheets.put("pet", statSheetParser.parse(petParser.getPetEquipmentEffects(snapshot.document(NexonEndpoint.PET_EQUIPMENT)), "펫 장비"));
        sheets.put("cashItem", statSheetParser.parse(cashItemParser.getCashStatEffect(snapshot.document(NexonEndpoint.CASH_ITEM_EQUIPMENT)), "캐시 장비"));
        sheets.put("skill0", statSheetParser.parse(skillParser.getCombatRelevantSkillEffects(snapshot.document(NexonEndpoint.SKILL_0)), "0차 스킬"));
        sheets.put("hexaStat", statSheetParser.parseNoPercentStat(hexaParser.getCurrentHexa(snapshot.document(NexonEndpoint.HEXA_MATRIX_STAT), mainStats), "헥사 스텟"));
        sheets.put("unionArtifact", statSheetParser.parse(artifactParser.getArtifactEffects(snapshot.document(NexonEndpoint.UNION_ARTIFACT)), "유니온 아티팩트"));
        sheets.put("unionChampion", statSheetParser.parse(championParser.getChampionStats(snapshot.document(NexonEndpoint.UNION_CHAMPION)), "유니온 챔피언"));
        return new CommonStatSheetView(sheets, sumSheets(sheets));
    }

    private PresetStatSheetView buildPresetView(
            CharacterSnapshot snapshot,
            StatSheet sharedTotal,
            String characterClass,
            PresetSelection presetSelection,
            Long apiCombatPower,
            List<String> mainStats,
            List<String> subStats,
            Integer characterLevel
    ) {
        JsonNode itemEquipment = snapshot.document(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = snapshot.document(NexonEndpoint.ABILITY);
        JsonNode hyperStat = snapshot.document(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = snapshot.document(NexonEndpoint.UNION_RAIDER);
        JsonNode setEffect = snapshot.document(NexonEndpoint.SET_EFFECT);

        JsonNode presetItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquipment, presetSelection.itemPreset());
        JsonNode title = itemEquipmentParser.getTitleItem(itemEquipment);
        JsonNode dragon = itemEquipmentParser.getDragonItem(itemEquipment);
        JsonNode mechanic = itemEquipmentParser.getMechanicItem(itemEquipment);

        Map<String, StatSheet> sheets = new LinkedHashMap<>();
        sheets.put("itemEquipment", buildItemSheet(presetItems, title, dragon, mechanic, characterClass));
        sheets.put("setEffect", statSheetParser.parse(setEffectParser.getSetEffectByPreset(setEffect, presetItems, characterClass), "세트 효과"));
        sheets.put("ability", statSheetParser.parseNoPercentStat(abilityParser.getCurrentAbilityByPreset(ability, presetSelection.abilityPreset()), "어빌리티"));
        sheets.put("hyperStat", statSheetParser.parseNoPercentStat(hyperStatParser.getStatIncreaseEffects(hyperStat, presetSelection.hyperStatPreset()), "하이퍼 스탯"));
        sheets.put("unionRaider-occupied", statSheetParser.parse(raiderParser.getUnionOccupiedStatByPreset(unionRaider, presetSelection.unionRaiderPreset()), "유니온 점령 효과"));
        sheets.put("unionRaider-raider", statSheetParser.parseNoPercentStat(raiderParser.getUnionRaiderStatByPreset(unionRaider, presetSelection.unionRaiderPreset()), "유니온 공격대원 효과"));

        StatSheet presetTotal = sumSheets(sheets);
        StatSheet combinedTotal = sharedTotal.plus(presetTotal);
        long estimatedCombatPower = estimateCombatPower(characterClass, combinedTotal, mainStats, subStats, characterLevel);

        return new PresetStatSheetView(
                presetSelection,
                setEffectParser.getAppliedSetCounts(setEffect, presetItems, characterClass),
                sheets,
                presetTotal,
                combinedTotal,
                apiCombatPower,
                estimatedCombatPower
        );
    }

    private Map<String, Integer> AdditionalItem = Map.of(
            "보우마스터", 9,
            "신궁", 9,
            "윈드브레이커", 9,
            "와일드헌터", 9,
            "나이트로드", 29,
            "나이트워커", 29,
            "캡틴", 22
    );

    private StatSheet buildItemSheet(JsonNode items, JsonNode title, JsonNode dragon, JsonNode mechanic, String characterClass) {
        StatSheet total = new StatSheet("장비 목록");
        total.merge(statSheetParser.parse(itemParser.getTitleStatEffects(title), "칭호"));

        for (JsonNode item : dragon) {
            String name = Jsons.text(item, "item_name");
            total.merge(statSheetParser.parse(itemParser.getItemStatEffects(item, characterClass), name));
        }

        for (JsonNode item : mechanic) {
            String name = Jsons.text(item, "item_name");
            total.merge(statSheetParser.parse(itemParser.getItemStatEffects(item, characterClass), name));
        }

        if(AdditionalItem.containsKey(characterClass)){
            StatSheet Additional = new StatSheet("소비 아이템");
            Additional.setATTACK_POWER(AdditionalItem.get(characterClass));
            total.merge(Additional);
        }

        if(characterClass.equals("제로")){
            total.merge(itemParser.getItemStatEffectsFromList(items, characterClass));
        }
        else {
            for (JsonNode item : items) {
                String part = Jsons.text(item, "item_equipment_slot");
                String name = Jsons.text(item, "item_name");
                total.merge(statSheetParser.parse(itemParser.getItemStatEffects(item, characterClass), part + ", " + name));
            }
        }
        return total;
    }

    private StatSheet sumSheets(Map<String, StatSheet> sheets) {
        StatSheet total = new StatSheet("총합");
        for (StatSheet sheet : sheets.values()) {
            total.merge(sheet);
        }
        return total;
    }

    private int chooseItemPreset(JsonNode itemEquipment) {
        return itemEquipmentParser.availablePresets(itemEquipment).stream()
                .max(Comparator.<Integer>comparingInt(preset -> itemEquipmentParser.scorePreset(itemEquipmentParser.getItemEquipmentByPreset(itemEquipment, preset)))
                        .thenComparingInt(preset -> preset == itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(-1) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(1));
    }

    private int chooseAbilityPreset(JsonNode ability, String characterClass) {
        return abilityParser.availablePresets(ability).stream()
                .max(Comparator.<Integer>comparingInt(preset -> abilityParser.scorePreset(ability, preset, characterClass))
                        .thenComparingInt(preset -> preset == abilityParser.getCurrentPresetAbility(ability) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(abilityParser.getCurrentPresetAbility(ability));
    }

    private int chooseHyperPreset(JsonNode hyperStat) {
        return hyperStatParser.availablePresets(hyperStat).stream()
                .max(Comparator.<Integer>comparingInt(preset -> hyperStatParser.scorePreset(hyperStat, preset))
                        .thenComparingInt(preset -> preset == hyperStatParser.getCurrentPresetNo(hyperStat) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(hyperStatParser.getCurrentPresetNo(hyperStat));
    }

    private int chooseUnionPreset(JsonNode unionRaider) {
        return raiderParser.availablePresets(unionRaider).stream()
                .max(Comparator.<Integer>comparingInt(preset -> raiderParser.scorePreset(unionRaider, preset))
                        .thenComparingInt(preset -> -preset))
                .orElse(1);
    }

    private int chooseCurrentUnionPreset(JsonNode unionRaider) {
        List<String> currentRaiderStats = raiderParser.getUnionRaiderStat(unionRaider);
        List<String> currentOccupiedStats = raiderParser.getUnionOccupiedStat(unionRaider);

        return raiderParser.availablePresets(unionRaider).stream()
                .filter(preset -> currentRaiderStats.equals(raiderParser.getUnionRaiderStatByPreset(unionRaider, preset)))
                .filter(preset -> currentOccupiedStats.equals(raiderParser.getUnionOccupiedStatByPreset(unionRaider, preset)))
                .findFirst()
                .orElse(raiderParser.availablePresets(unionRaider).stream().min(Integer::compareTo).orElse(1));
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

    private long estimateCombatPower(String characterClass, StatSheet sheet, List<String> mainStats, List<String> subStats, Integer characterLevel) {
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


        /*
        double mainStat;
        double subStat;

        if (characterClass.contains("데몬어벤져")) {
            mainStat = finalStat(sheet.getHP(), sheet.getALL_STAT(), sheet.getHP_PERCENT(), 0, sheet.getHP_NO_PERCENT(), 0);
            subStat = finalStat(sheet.getSTR(), sheet.getALL_STAT(), sheet.getSTR_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getSTR_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT());
        } else if (characterClass.contains("제논")) {
            double str = finalStat(sheet.getSTR(), sheet.getALL_STAT(), sheet.getSTR_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getSTR_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT());
            double dex = finalStat(sheet.getDEX(), sheet.getALL_STAT(), sheet.getDEX_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getDEX_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT());
            double luk = finalStat(sheet.getLUK(), sheet.getALL_STAT(), sheet.getLUK_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getLUK_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT());
            mainStat = str + dex + luk;
            subStat = 0;
        } else {
            List<Map.Entry<String, Double>> stats = List.of(
                    Map.entry("STR", finalStat(sheet.getSTR(), sheet.getALL_STAT(), sheet.getSTR_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getSTR_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT())),
                    Map.entry("DEX", finalStat(sheet.getDEX(), sheet.getALL_STAT(), sheet.getDEX_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getDEX_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT())),
                    Map.entry("INT", finalStat(sheet.getINT(), sheet.getALL_STAT(), sheet.getINT_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getINT_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT())),
                    Map.entry("LUK", finalStat(sheet.getLUK(), sheet.getALL_STAT(), sheet.getLUK_PERCENT(), sheet.getALL_STAT_PERCENT(), sheet.getLUK_NO_PERCENT(), sheet.getALL_STAT_NO_PERCENT()))
            );
            Map.Entry<String, Double> main = stats.stream().max(Map.Entry.comparingByValue()).orElse(Map.entry("STR", 0.0));
            Map.Entry<String, Double> sub = stats.stream()
                    .filter(entry -> !entry.getKey().equals(main.getKey()))
                    .max(Map.Entry.comparingByValue())
                    .orElse(Map.entry("DEX", 0.0));
            mainStat = main.getValue();
            subStat = sub.getValue();
        }

        double attackOrMagic = finalAttackOrMagic(characterClass, sheet);
        double damage = 100 + sheet.getDAMAGE() + sheet.getBOSS_DAMAGE();
        double criticalDamage = 135 + sheet.getCRITICAL_DAMAGE();
        double finalDamage = 100 + sheet.getFINAL_DAMAGE();
        double statFactor = (mainStat * 4 + subStat) / 100.0;

        return (long) Math.floor(statFactor * attackOrMagic * damage * criticalDamage * finalDamage / 1_000_000.0);
         */

    private double finalStat(int stat, int allStat, int statPercent, int allStatPercent, int statNoPercent, int allStatNoPercent) {
        double base = stat + allStat;
        double percent = 100 + statPercent + allStatPercent;
        double noPercent = statNoPercent + allStatNoPercent;
        return floor(base * percent / 100.0 + noPercent);
    }

    private double finalAttackOrMagic(String characterClass, StatSheet sheet) {
        double attack = floor(sheet.getATTACK_POWER() * (100 + sheet.getATTACK_POWER_PERCENT()) / 100.0);
        double magic = floor(sheet.getMAGIC_POWER() * (100 + sheet.getMAGIC_POWER_PERCENT()) / 100.0);
        return isMageClass(characterClass) ? magic : Math.max(attack, magic);
    }

    private boolean isMageClass(String characterClass) {
        return MAGE_CLASSES.stream().anyMatch(characterClass::contains);
    }
}
