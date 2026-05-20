package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.ItemRecord;
import org.whitedoggy.mapleweb2.analysis.data.ItemSheet;
import org.whitedoggy.mapleweb2.analysis.dto.CharacterSnapshot;
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
import org.whitedoggy.mapleweb2.external.nexon.client.NexonApiClient;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataSheetBuilder {
    private final NexonApiClient nexonApiClient;
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

    public Mono<DataSheetResponse> getStatSheets(String characterName, LocalDate date) {
        return nexonApiClient.getOcid(characterName)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("캐릭터 OCID를 조회할 수 없습니다: " + characterName)))
                .flatMap(ocidResponse -> nexonApiClient.fetchSnapshot(ocidResponse.ocid(), date))
                .map(this::buildDataSheet);
    }

    public DataSheetResponse buildFromSnapshot(CharacterSnapshot snapshot) {
        return buildDataSheet(snapshot);
    }

    private DataSheetResponse buildDataSheet(CharacterSnapshot snapshot) {
        DataSheet dataSheet = new DataSheet();

        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        JsonNode stat = snapshot.document(NexonEndpoint.STAT);
        JsonNode symbol = snapshot.document(NexonEndpoint.SYMBOL_EQUIPMENT);
        JsonNode skill = snapshot.document(NexonEndpoint.SKILL_0);
        JsonNode hexa = snapshot.document(NexonEndpoint.HEXA_MATRIX_STAT);
        JsonNode petEquip = snapshot.document(NexonEndpoint.PET_EQUIPMENT);
        JsonNode cashEquip = snapshot.document(NexonEndpoint.CASH_ITEM_EQUIPMENT);
        JsonNode itemEquip = snapshot.document(NexonEndpoint.ITEM_EQUIPMENT);
        JsonNode ability = snapshot.document(NexonEndpoint.ABILITY);
        JsonNode hyper = snapshot.document(NexonEndpoint.HYPER_STAT);
        JsonNode unionRaider = snapshot.document(NexonEndpoint.UNION_RAIDER);
        JsonNode setEffect = snapshot.document(NexonEndpoint.SET_EFFECT);
        JsonNode unionArtifact = snapshot.document(NexonEndpoint.UNION_ARTIFACT);
        JsonNode unionChampion = snapshot.document(NexonEndpoint.UNION_CHAMPION);

        dataSheet.setOcid(snapshot.ocid());
        dataSheet.setDate(snapshot.date());
        setBasics(dataSheet, basic);
        dataSheet.setAbilityPoint(setAP(stat));
        dataSheet.setSymbol(setSymbol(symbol));
        SkillParseResult skillParseResult = skillParser.getCombatRelevantSkillEffects(skill);
        dataSheet.setSkill(setSkill(skillParseResult));
        dataSheet.setLucidTransformSuspected(skillParseResult.lucidTransformSuspected());
        dataSheet.setHexaStat(setHexa(hexa, dataSheet));
        dataSheet.setPetEquip(setPetEquip(petEquip));
        dataSheet.setCashEquip(setCashEquip(cashEquip));
        dataSheet.setUnionArtifact(setUnionArtifact(unionArtifact));
        dataSheet.setUnionChampion(setUnionChampion(unionChampion));

        String characterClass = dataSheet.getCharacterClass();

        PresetSelection currentPreset = new PresetSelection(
                itemEquipmentParser.getCurrentPresetItemEquipment(itemEquip).orElse(1),
                abilityParser.getCurrentPresetAbility(ability),
                hyperStatParser.getCurrentPresetNo(hyper),
                presetSelector.chooseCurrentUnionPreset(unionRaider)
        );

        PresetSelection combatPreset = new PresetSelection(
                presetSelector.chooseItemPreset(itemEquip),
                presetSelector.chooseAbilityPreset(ability, characterClass),
                presetSelector.chooseHyperPreset(hyper),
                presetSelector.chooseUnionPreset(unionRaider)
        );

        JsonNode presetItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquip, combatPreset.itemPreset());
        JsonNode currentItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquip, currentPreset.itemPreset());

        dataSheet.setCurrentCombatPower(getCurrentCombatPower(dataSheet, stat));

        return new DataSheetResponse(
                getDataSheet(currentItems, itemEquip, setEffect, ability, hyper, unionRaider, characterClass, currentPreset, dataSheet),
                getDataSheet(presetItems, itemEquip, setEffect, ability, hyper, unionRaider, characterClass, combatPreset, dataSheet)
        );
    }

    private DataSheet getDataSheet(
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
        return result;
    }

    private long getCurrentCombatPower(DataSheet dataSheet, JsonNode node) {
        JsonNode stats = node.path("final_stat");
        for (JsonNode stat : stats) {
            String statName = Jsons.text(stat, "stat_name");
            if (statName.equals("전투력")) {
                return Long.parseLong(Jsons.text(stat, "stat_value"));
            }
        }
        return 0L;
    }

    private void setBasics(DataSheet dataSheet, JsonNode node) {
        dataSheet.setCharacterName(basicParser.characterName(node));
        dataSheet.setCharacterClass(basicParser.characterClass(node));
        dataSheet.setCharacterLevel(basicParser.characterLevel(node));
        dataSheet.setCharacterImage(basicParser.characterWorld(node));
        dataSheet.setCharacterWorld(basicParser.characterWorld(node));
        dataSheet.setCharacterGuild(basicParser.characterGuild(node));
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

    private StatSheet setHexa(JsonNode node, DataSheet dataSheet) {
        List<String> main = SupportMethods.getMainStat(dataSheet.getCharacterClass());
        return statSheetParser.parseNoPercentStat(hexaParser.getCurrentHexa(node, main), "헥사 스텟");
    }

    private Map<String, ItemSheet> setPetEquip(JsonNode node) {
        Map<String, ItemSheet> petEquip = new HashMap<>();
        petEquip.put("펫 장비 1", new ItemSheet("펫 장비 1", statSheetParser.parse(petParser.getPetEquip(node, 1), "펫 장비 1")));
        petEquip.put("펫 장비 2", new ItemSheet("펫 장비 2", statSheetParser.parse(petParser.getPetEquip(node, 2), "펫 장비 2")));
        petEquip.put("펫 장비 3", new ItemSheet("펫 장비 3", statSheetParser.parse(petParser.getPetEquip(node, 3), "펫 장비 3")));
        return petEquip;
    }

    private Map<String, ItemSheet> setCashEquip(JsonNode node) {
        List<ItemRecord> itemRecords = cashItemParser.getCashEquip(node);
        Map<String, ItemSheet> itemSheets = new HashMap<>();
        for (ItemRecord itemRecord : itemRecords) {
            itemSheets.put(itemRecord.slot(), new ItemSheet(itemRecord.name(), statSheetParser.parse(itemRecord.effects(), itemRecord.slot())));
        }
        return itemSheets;
    }

    private Map<String, ItemSheet> setItemEquip(JsonNode items, JsonNode title, JsonNode dragon, JsonNode mechanic, String characterClass) {
        Map<String, ItemSheet> itemEquip = new LinkedHashMap<>();
        putItemSheet(itemEquip, "칭호", Jsons.text(title, "item_name"), itemParser.getTitleStatEffects(title), "칭호, " + Jsons.text(title, "item_name"));

        for (JsonNode item : dragon) {
            String slot = Jsons.text(item, "item_equipment_slot");
            String name = Jsons.text(item, "item_name");
            putItemSheet(itemEquip, slot, name, itemParser.getItemStatEffects(item, characterClass), slot + ", " + name);
        }

        for (JsonNode item : mechanic) {
            String slot = Jsons.text(item, "item_equipment_slot");
            String name = Jsons.text(item, "item_name");
            putItemSheet(itemEquip, slot, name, itemParser.getItemStatEffects(item, characterClass), slot + ", " + name);
        }

        for (JsonNode item : items) {
            String slot = Jsons.text(item, "item_equipment_slot");
            String name = Jsons.text(item, "item_name");
            putItemSheet(itemEquip, slot, name, itemParser.getItemStatEffects(item, characterClass), slot + ", " + name);
        }

        return itemEquip;
    }

    private void putItemSheet(Map<String, ItemSheet> itemEquip, String slot, String name, List<String> effects, String sheetName) {
        String key = slot;
        if (itemEquip.containsKey(key)) {
            key = slot + ", " + name;
        }
        itemEquip.put(key, new ItemSheet(name, statSheetParser.parse(effects, sheetName)));
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
}
