package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.dto.DataSheetResponse;
import org.whitedoggy.mapleweb2.analysis.support.PresetSelector;
import org.whitedoggy.mapleweb2.analysis.support.SupportMethods;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.domain.cash.CashItemParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemParser;
import org.whitedoggy.mapleweb2.domain.pet.PetParser;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.domain.skill.SkillParseResult;
import org.whitedoggy.mapleweb2.domain.skill.SkillParser;
import org.whitedoggy.mapleweb2.domain.symbol.SymbolParser;
import org.whitedoggy.mapleweb2.domain.union.artifact.ArtifactParser;
import org.whitedoggy.mapleweb2.domain.union.champion.ChampionParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DataSheetService {
    private static final Duration DATASHEET_CACHE_TTL = Duration.ofHours(6);

    private final BasicParser basicParser;
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
    private final PresetSelector presetSelector;
    private final CombatCalculationService combatCalculationService;
    private final MapleCache cache;

    public Mono<DataSheetResponse> getOrLoadDataSheet(
            String characterName,
            LocalDate date,
            Supplier<Mono<CharacterSnapshot>> snapshotLoader
    ) {
        String cacheKey = dataSheetCacheKey(characterName, date);
        return cache.getOrLoad(cacheKey, DataSheetResponse.class, DATASHEET_CACHE_TTL,
                () -> snapshotLoader.get()
                        .map(snapshot -> getDataSheet(snapshot)));
    }

    public DataSheetResponse getDataSheet(CharacterSnapshot snapshot){
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        String characterName = basicParser.characterName(basic);
        String characterClass = basicParser.characterClass(basic);
        Integer characterLevel = basicParser.characterLevel(basic);
        String characterWorld = basicParser.characterWorld(basic);
        String characterGuild = basicParser.characterGuild(basic);
        String characterImage = basicParser.characterImage(basic);

        return new DataSheetResponse(
                snapshot.ocid(),
                snapshot.date(),
                characterName,
                characterClass,
                characterLevel,
                characterGuild,
                characterWorld,
                characterImage,
                getDataSheet(snapshot, characterClass, characterLevel)
        );
    }

    private DataSheet getDataSheet(CharacterSnapshot snapshot, String characterClass, Integer characterLevel){
        DataSheet dataSheet = getDataSheetFromSnapShot(snapshot.documents(), characterClass);
        Long combatPower = combatCalculationService.estimateCombatPower(dataSheet, characterClass, characterLevel);
        dataSheet.setCombatPower(combatPower);
        return dataSheet;
    }

    private DataSheet getDataSheetFromSnapShot(Map<NexonEndpoint, JsonNode> document, String characterClass) {
        DataSheet dataSheet = new DataSheet();

        JsonNode stat = document.get(NexonEndpoint.STAT);
        JsonNode symbol = document.get(NexonEndpoint.SYMBOL_EQUIPMENT);
        JsonNode skill = document.get(NexonEndpoint.SKILL_0);
        JsonNode hexa = document.get(NexonEndpoint.HEXA_MATRIX_STAT);
        JsonNode petEquip = document.get(NexonEndpoint.PET_EQUIPMENT);
        JsonNode cashEquip = document.get(NexonEndpoint.CASH_ITEM_EQUIPMENT);
        JsonNode itemEquip = document.get(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = document.get(NexonEndpoint.ABILITY);
        JsonNode hyper = document.get(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = document.get(NexonEndpoint.UNION_RAIDER);
        JsonNode setEffect = document.get(NexonEndpoint.SET_EFFECT);
        JsonNode unionArtifact = document.get(NexonEndpoint.UNION_ARTIFACT);
        JsonNode unionChampion = document.get(NexonEndpoint.UNION_CHAMPION);

        dataSheet.setAbilityPoint(setAP(stat));
        dataSheet.setSymbol(setSymbol(symbol));
        SkillParseResult skillParseResult = skillParser.getCombatRelevantSkillEffects(skill);
        dataSheet.setSkill(setSkill(skillParseResult));
        dataSheet.setLucidTransformSuspected(skillParseResult.lucidTransformSuspected());
        dataSheet.setHexaStat(setHexa(hexa, characterClass));
        dataSheet.setPetEquip(setPetEquip(petEquip));
        dataSheet.setCashEquip(setCashEquip(cashEquip));
        dataSheet.setUnionArtifact(setUnionArtifact(unionArtifact));
        dataSheet.setUnionChampion(setUnionChampion(unionChampion));

        PresetSelection combatPreset = new PresetSelection(
                presetSelector.chooseItemPreset(itemEquip),
                presetSelector.chooseAbilityPreset(ability, characterClass),
                presetSelector.chooseHyperPreset(hyper),
                presetSelector.chooseUnionPreset(unionRaider)
        );

        JsonNode presetItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquip, combatPreset.itemPreset());

        return buildDataSheet(presetItems, itemEquip, setEffect, ability, hyper, unionRaider, characterClass, combatPreset, dataSheet);
    }

    private DataSheet buildDataSheet(
            JsonNode presetItems,
            JsonNode itemEquip,
            JsonNode setEffect,
            JsonNode ability,
            JsonNode hyper,
            JsonNode unionRaider,
            String characterClass,
            PresetSelection preset,
            DataSheet dataSheet
    ) {
        DataSheet result = new DataSheet();
        result.copy(dataSheet);

        result.setItemEquip(setItemEquip(
                presetItems,
                itemEquipmentParser.getTitleItem(itemEquip),
                itemEquipmentParser.getDragonItem(itemEquip),
                itemEquipmentParser.getMechanicItem(itemEquip),
                characterClass
        ));
        result.setSetEffect(setSetEffect(setEffect, presetItems, characterClass));
        result.setAbility(setAbility(ability, preset.abilityPreset()));
        result.setHyperStat(setHyperStat(hyper, preset.hyperStatPreset()));
        result.setUnionOccupied(setUnionOccupied(unionRaider, preset.unionRaiderPreset()));
        result.setUnionRaider(setUnionRaider(unionRaider, preset.unionRaiderPreset()));
        result.buildSum();
        return result;
    }

    private StatSheet setAP(JsonNode node) {
        StatSheet abilityPoint = new StatSheet("어빌리티 포인트");

        JsonNode stats = node.path("final_stat");
        for (JsonNode stat : stats) {
            String statName = Jsons.text(stat, "stat_name");
            if (statName.equals("AP 배분 STR")) {
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setSTR(statValue);
            }
            if (statName.equals("AP 배분 DEX")) {
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setDEX(statValue);
            }
            if (statName.equals("AP 배분 LUK")) {
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setLUK(statValue);
            }
            if (statName.equals("AP 배분 INT")) {
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setINT(statValue);
            }
            if (statName.equals("AP 배분 HP")) {
                Integer statValue = Integer.parseInt(Jsons.text(stat, "stat_value"));
                abilityPoint.setHP(statValue);
            }
        }
        return abilityPoint;
    }

    private StatSheet setSymbol(JsonNode node) {
        return statSheetParser.parseNoPercentStat(symbolParser.getSymbolStatEffects(node), "심볼");
    }

    private StatSheet setSkill(SkillParseResult skillParseResult) {
        return statSheetParser.parse(skillParseResult.effects(), "스킬");
    }

    private StatSheet setHexa(JsonNode node, String characterClass) {
        List<String> main = SupportMethods.getMainStat(characterClass);
        return statSheetParser.parseNoPercentStat(hexaParser.getCurrentHexa(node, main), "헥사 스텟");
    }

    private Map<String, ItemSnapShot> setPetEquip(JsonNode node) {
        Map<String, ItemSnapShot> petEquip = new HashMap<>();
        List<ItemRecord> itemRecords = petParser.getItemSnapShot(node);
        for (ItemRecord itemRecord : itemRecords) {
            petEquip.put("펫 장비 - " + itemRecord.slot(), itemRecord.itemSnapShot());
        }
        return petEquip;
    }

    private Map<String, ItemSnapShot> setCashEquip(JsonNode node) {
        Map<String, ItemSnapShot> itemEquip = new HashMap<>();
        JsonNode cashItems = node.path("cash_item_equipment_base");
        for (JsonNode item : cashItems) {
            ItemRecord itemRecord = cashItemParser.getItemSnapShot(item);
            itemEquip.put("캐시 장비 - " + itemRecord.slot(), itemRecord.itemSnapShot());
        }
        return itemEquip;
    }

    private Map<String, ItemSnapShot> setItemEquip(JsonNode items, JsonNode title, JsonNode dragon, JsonNode mechanic, String characterClass) {
        Map<String, ItemSnapShot> itemEquip = new HashMap<>();
        ItemRecord titleRecord = itemParser.getTitleItemSnapShot(title);
        itemEquip.put("장비 - " + titleRecord.slot(), titleRecord.itemSnapShot());

        for (JsonNode item : dragon) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "드래곤");
            String slot = itemRecord.slot();
            itemEquip.put("드래곤 장비 - " + slot, itemRecord.itemSnapShot());
        }

        for (JsonNode item : mechanic) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "메카닉");
            String slot = itemRecord.slot();
            itemEquip.put("메카닉 장비 - " + slot, itemRecord.itemSnapShot());
        }

        for (JsonNode item : items) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "장비");
            String slot = itemRecord.slot();
            itemEquip.put("장비 - " + slot, itemRecord.itemSnapShot());
        }

        return itemEquip;
    }

    private StatSheet setSetEffect(JsonNode node, JsonNode presetItems, String characterClass) {
        return statSheetParser.parse(setEffectParser.getSetEffectByPreset(node, presetItems, characterClass), "세트 효과");
    }

    private StatSheet setAbility(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(abilityParser.getCurrentAbilityByPreset(node, presetNo), "어빌리티");
    }

    private StatSheet setHyperStat(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(hyperStatParser.getStatIncreaseEffects(node, presetNo), "하이퍼 스탯");
    }

    private StatSheet setUnionArtifact(JsonNode node) {
        return statSheetParser.parse(artifactParser.getArtifactEffectsFromCrystal(node), "유니온 아티팩트");
    }

    private StatSheet setUnionChampion(JsonNode node) {
        return statSheetParser.parse(championParser.getChampionStats(node), "유니온 챔피언");
    }

    private StatSheet setUnionOccupied(JsonNode node, int presetNo) {
        return statSheetParser.parse(raiderParser.getUnionOccupiedStatByPreset(node, presetNo), "유니온 점령 효과");
    }

    private StatSheet setUnionRaider(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(raiderParser.getUnionRaiderStatByPreset(node, presetNo), "유니온 공격대원");
    }

    private String dataSheetCacheKey(String characterName, LocalDate date) {
        return "maple:datasheet:v1:" + normalizeCharacterName(characterName) + ":" + date;
    }

    private String normalizeCharacterName(String characterName) {
        return characterName == null ? "" : characterName.trim().toLowerCase(Locale.ROOT);
    }
}
