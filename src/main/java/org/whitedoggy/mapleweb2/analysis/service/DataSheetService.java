package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.support.PresetSelector;
import org.whitedoggy.mapleweb2.analysis.support.SupportMethods;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.cash.CashItemParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public Mono<DataSheet> getOrLoadDataSheet(
            String characterName,
            LocalDate date,
            Supplier<Mono<CharacterSnapshot>> snapshotLoader
    ) {
        String cacheKey = dataSheetCacheKey(characterName, date);
        return cache.getOrLoad(cacheKey, DataSheet.class, DATASHEET_CACHE_TTL,
                () -> snapshotLoader.get().map(this::getCombatDataSheet));
    }

    public DataSheet getCombatDataSheet(CharacterSnapshot snapshot) {
        return getDataSheet(snapshot, getCombatPresetSelection(snapshot));
    }

    public DataSheet getCurrentDataSheet(CharacterSnapshot snapshot) {
        return getDataSheet(snapshot, getCurrentPresetSelection(snapshot));
    }

    public PresetSelection getCombatPresetSelection(CharacterSnapshot snapshot) {
        JsonNode itemEquip = snapshot.document(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = snapshot.document(NexonEndpoint.ABILITY);
        JsonNode hyper = snapshot.document(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = snapshot.document(NexonEndpoint.UNION_RAIDER);
        String characterClass = basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC));

        return new PresetSelection(
                presetSelector.chooseItemPreset(itemEquip),
                presetSelector.chooseAbilityPreset(ability, characterClass),
                presetSelector.chooseHyperPreset(hyper),
                presetSelector.chooseUnionPreset(unionRaider)
        );
    }

    public PresetSelection getCurrentPresetSelection(CharacterSnapshot snapshot) {
        JsonNode itemEquip = snapshot.document(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = snapshot.document(NexonEndpoint.ABILITY);
        JsonNode hyper = snapshot.document(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = snapshot.document(NexonEndpoint.UNION_RAIDER);

        return new PresetSelection(
                itemEquipmentParser.getCurrentPresetItemEquipment(itemEquip).orElse(1),
                abilityParser.getCurrentPresetAbility(ability),
                hyperStatParser.getCurrentPresetNo(hyper),
                presetSelector.chooseCurrentUnionPreset(unionRaider)
        );
    }

    public DataSheet getDataSheet(CharacterSnapshot snapshot, PresetSelection presetSelection) {
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        String characterClass = basicParser.characterClass(basic);
        Integer characterLevel = basicParser.characterLevel(basic);

        DataSheet dataSheet = getDataSheetFromSnapshot(snapshot.documents(), characterClass, presetSelection);
        long combatPower = combatCalculationService.estimateCombatPower(dataSheet, characterClass, characterLevel);
        dataSheet.setCombatPower(combatPower);
        return dataSheet;
    }

    private DataSheet getDataSheetFromSnapshot(
            Map<NexonEndpoint, JsonNode> documents,
            String characterClass,
            PresetSelection presetSelection
    ) {
        DataSheet dataSheet = new DataSheet();

        JsonNode stat = documents.get(NexonEndpoint.STAT);
        JsonNode symbol = documents.get(NexonEndpoint.SYMBOL_EQUIPMENT);
        JsonNode skill = documents.get(NexonEndpoint.SKILL_0);
        JsonNode hexa = documents.get(NexonEndpoint.HEXA_MATRIX_STAT);
        JsonNode petEquip = documents.get(NexonEndpoint.PET_EQUIPMENT);
        JsonNode cashEquip = documents.get(NexonEndpoint.CASH_ITEM_EQUIPMENT);
        JsonNode itemEquip = documents.get(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = documents.get(NexonEndpoint.ABILITY);
        JsonNode hyper = documents.get(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = documents.get(NexonEndpoint.UNION_RAIDER);
        JsonNode setEffect = documents.get(NexonEndpoint.SET_EFFECT);
        JsonNode unionArtifact = documents.get(NexonEndpoint.UNION_ARTIFACT);
        JsonNode unionChampion = documents.get(NexonEndpoint.UNION_CHAMPION);

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

        JsonNode presetItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquip, presetSelection.itemPreset());
        return buildDataSheet(presetItems, itemEquip, setEffect, ability, hyper, unionRaider, characterClass, presetSelection, dataSheet);
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
            DataSheet sharedDataSheet
    ) {
        DataSheet result = new DataSheet();
        result.copy(sharedDataSheet);

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
        StatSheet abilityPoint = new StatSheet("abilityPoint");

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
        return statSheetParser.parseNoPercentStat(symbolParser.getSymbolStatEffects(node), "symbol");
    }

    private StatSheet setSkill(SkillParseResult skillParseResult) {
        return statSheetParser.parse(skillParseResult.effects(), "skill");
    }

    private StatSheet setHexa(JsonNode node, String characterClass) {
        List<String> main = SupportMethods.getMainStat(characterClass);
        return statSheetParser.parseNoPercentStat(hexaParser.getCurrentHexa(node, main), "hexaStat");
    }

    private Map<String, ItemSnapShot> setPetEquip(JsonNode node) {
        Map<String, ItemSnapShot> petEquip = new HashMap<>();
        List<ItemRecord> itemRecords = petParser.getItemSnapShot(node);
        for (ItemRecord itemRecord : itemRecords) {
            petEquip.put("petEquip - " + itemRecord.slot(), itemRecord.itemSnapShot());
        }
        return petEquip;
    }

    private Map<String, ItemSnapShot> setCashEquip(JsonNode node) {
        Map<String, ItemSnapShot> itemEquip = new HashMap<>();
        JsonNode cashItems = node.path("cash_item_equipment_base");
        for (JsonNode item : cashItems) {
            ItemRecord itemRecord = cashItemParser.getItemSnapShot(item);
            itemEquip.put("cashEquip - " + itemRecord.slot(), itemRecord.itemSnapShot());
        }
        return itemEquip;
    }

    private Map<String, ItemSnapShot> setItemEquip(JsonNode items, JsonNode title, JsonNode dragon, JsonNode mechanic, String characterClass) {
        Map<String, ItemSnapShot> itemEquip = new HashMap<>();
        ItemRecord titleRecord = itemParser.getTitleItemSnapShot(title);
        itemEquip.put("itemEquip - " + titleRecord.slot(), titleRecord.itemSnapShot());

        for (JsonNode item : dragon) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "dragon");
            String slot = itemRecord.slot();
            itemEquip.put("dragonEquip - " + slot, itemRecord.itemSnapShot());
        }

        for (JsonNode item : mechanic) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "mechanic");
            String slot = itemRecord.slot();
            itemEquip.put("mechanicEquip - " + slot, itemRecord.itemSnapShot());
        }

        for (JsonNode item : items) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "itemEquip");
            String slot = itemRecord.slot();
            itemEquip.put("itemEquip - " + slot, itemRecord.itemSnapShot());
        }

        return itemEquip;
    }

    private StatSheet setSetEffect(JsonNode node, JsonNode presetItems, String characterClass) {
        return statSheetParser.parse(setEffectParser.getSetEffectByPreset(node, presetItems, characterClass), "setEffect");
    }

    private StatSheet setAbility(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(abilityParser.getCurrentAbilityByPreset(node, presetNo), "ability");
    }

    private StatSheet setHyperStat(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(hyperStatParser.getStatIncreaseEffects(node, presetNo), "hyperStat");
    }

    private StatSheet setUnionArtifact(JsonNode node) {
        return statSheetParser.parse(artifactParser.getArtifactEffectsFromCrystal(node), "unionArtifact");
    }

    private StatSheet setUnionChampion(JsonNode node) {
        return statSheetParser.parse(championParser.getChampionStats(node), "unionChampion");
    }

    private StatSheet setUnionOccupied(JsonNode node, int presetNo) {
        return statSheetParser.parse(raiderParser.getUnionOccupiedStatByPreset(node, presetNo), "unionOccupied");
    }

    private StatSheet setUnionRaider(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(raiderParser.getUnionRaiderStatByPreset(node, presetNo), "unionRaider");
    }

    private String dataSheetCacheKey(String characterName, LocalDate date) {
        return "maple:datasheet:v2:" + normalizeCharacterName(characterName) + ":" + date;
    }

    private String normalizeCharacterName(String characterName) {
        return characterName == null ? "" : characterName.trim().toLowerCase(Locale.ROOT);
    }
}
