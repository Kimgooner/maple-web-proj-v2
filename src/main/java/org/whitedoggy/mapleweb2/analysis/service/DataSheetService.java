package org.whitedoggy.mapleweb2.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.support.CacheTtlPolicy;
import org.whitedoggy.mapleweb2.analysis.support.PresetSelector;
import org.whitedoggy.mapleweb2.analysis.support.SourceEntryExtractor;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.cash.CashItemParser;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaCoreParser;
import org.whitedoggy.mapleweb2.domain.hexa.HexaParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.domain.item.support.WeaponData;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemParser;
import org.whitedoggy.mapleweb2.domain.otherstat.OtherStatParser;
import org.whitedoggy.mapleweb2.domain.pet.PetParser;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.domain.skill.ChallengersBuffs;
import org.whitedoggy.mapleweb2.domain.skill.SkillParseResult;
import org.whitedoggy.mapleweb2.domain.skill.SkillParser;
import org.whitedoggy.mapleweb2.domain.symbol.SymbolParser;
import org.whitedoggy.mapleweb2.domain.union.artifact.ArtifactParseResult;
import org.whitedoggy.mapleweb2.domain.union.artifact.ArtifactParser;
import org.whitedoggy.mapleweb2.domain.union.champion.ChampionParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import org.whitedoggy.mapleweb2.global.cache.MapleCache;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DataSheetService {
    /** TTL 을 정할 때 쓰는 "오늘". 넥슨의 날짜 경계와 같은 기준이어야 한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BasicParser basicParser;
    private final ItemEquipmentParser itemEquipmentParser;
    private final ItemParser itemParser;
    private final AbilityParser abilityParser;
    private final HyperStatParser hyperStatParser;
    private final RaiderParser raiderParser;
    private final SymbolParser symbolParser;
    private final PetParser petParser;
    private final SkillParser skillParser;
    private final ChallengersBuffs challengersBuffs;
    private final ArtifactParser artifactParser;
    private final ChampionParser championParser;
    private final WeaponData weaponData;
    private final SetEffectParser setEffectParser;
    private final StatSheetParser statSheetParser;
    private final HexaParser hexaParser;
    private final HexaCoreParser hexaCoreParser;
    private final OtherStatParser otherStatParser;
    private final CashItemParser cashItemParser;
    private final PresetSelector presetSelector;
    private final SourceEntryExtractor sourceEntryExtractor;
    private final CombatCalculationService combatCalculationService;
    private final MapleCache cache;
    private final GameData gameData;

    public Mono<DataSheet> getOrLoadDataSheet(
            String ocid,
            LocalDate date,
            Supplier<Mono<CharacterSnapshot>> snapshotLoader
    ) {
        String cacheKey = dataSheetCacheKey(ocid, date);
        return cache.get(cacheKey, DataSheet.class)
                .switchIfEmpty(Mono.defer(() -> snapshotLoader.get()
                        .map(this::getCombatDataSheet)
                        .flatMap(dataSheet -> cache.put(
                                cacheKey,
                                dataSheet,
                                CacheTtlPolicy.forDataSheet(date, LocalDateTime.now(KST), dataSheet)))));
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
                raiderParser.getUnionCurrentUse(unionRaider)
        );
    }

    public DataSheet getDataSheet(CharacterSnapshot snapshot, PresetSelection presetSelection) {
        // 아티팩트 만료 판정 기준일. 스냅샷이 가리키는 시점이다.
        LocalDate referenceDate = snapshot.date();
        JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
        String characterClass = basicParser.characterClass(basic);
        Integer characterLevel = basicParser.characterLevel(basic);

        DataSheet dataSheet = getDataSheetFromSnapshot(snapshot.documents(), characterClass, presetSelection, referenceDate);
        dataSheet.setIncompleteSnapshot(snapshot.hasMissingDocuments());
        dataSheet.setSolErdaFragments(
                hexaCoreParser.solErdaFragments(snapshot.document(NexonEndpoint.HEXA_MATRIX)));
        long combatPower = combatCalculationService.estimateCombatPower(dataSheet, characterClass, characterLevel);
        combatPower = applyPirateBlessIfBetter(dataSheet, characterClass, characterLevel, combatPower);
        dataSheet.setCombatPower(combatPower);
        return dataSheet;
    }

    private DataSheet getDataSheetFromSnapshot(
            Map<NexonEndpoint, JsonNode> documents,
            String characterClass,
            PresetSelection presetSelection,
            LocalDate referenceDate
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
        JsonNode otherStat = documents.get(NexonEndpoint.OTHER_STAT);

        dataSheet.setAbilityPoint(setAP(stat));
        dataSheet.setSymbol(setSymbol(symbol));
        String worldName = basicParser.characterWorld(documents.get(NexonEndpoint.BASIC));
        SkillParseResult skillParseResult = skillParser.getCombatRelevantSkillEffects(skill, worldName);
        dataSheet.setSkill(setSkill(skillParseResult));
        dataSheet.setLucidTransformSuspected(skillParseResult.lucidTransformSuspected());
        dataSheet.setHexaStat(setHexa(hexa, characterClass));
        dataSheet.setOtherStat(setOtherStat(otherStat));
        dataSheet.setPetEquip(setPetEquip(petEquip, referenceDate));
        dataSheet.setCashEquip(setCashEquip(cashEquip, referenceDate, characterClass));
        dataSheet.setExpiredPetEquipments(countExpired(dataSheet.getPetEquip()));
        dataSheet.setExpiredCashItems(countExpired(dataSheet.getCashEquip()));
        ArtifactParseResult artifactResult = artifactParser.parseCrystals(unionArtifact, referenceDate);
        dataSheet.setUnionArtifact(statSheetParser.parse(artifactResult.effects(), "unionArtifact"));
        dataSheet.setExpiredArtifactCrystals(artifactResult.expiredCrystals());
        dataSheet.setUnionRaiderDataMissing(isUnionRaiderDataMissing(unionRaider, worldName));
        dataSheet.setInactiveCharacter(basicParser.isInactive(documents.get(NexonEndpoint.BASIC)));
        dataSheet.setUnionChampion(setUnionChampion(unionChampion,
                basicParser.characterName(documents.get(NexonEndpoint.BASIC))));
        dataSheet.setUnionChampionUnverified(championParser.badgeUnverified(unionChampion));

        JsonNode presetItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquip, presetSelection.itemPreset());
        DataSheet result = buildDataSheet(presetItems, itemEquip, setEffect, ability, hyper, unionRaider, characterClass, presetSelection, dataSheet, referenceDate);
        result.setSourceEntries(sourceEntryExtractor.extract(
                documents, presetItems, presetSelection, characterClass, worldName, referenceDate));
        return result;
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
            DataSheet sharedDataSheet,
            LocalDate referenceDate
    ) {
        DataSheet result = new DataSheet();
        result.copy(sharedDataSheet);

        result.setItemEquip(setItemEquip(
                presetItems,
                itemEquipmentParser.getTitleItem(itemEquip),
                itemEquipmentParser.getDragonItem(itemEquip),
                itemEquipmentParser.getMechanicItem(itemEquip),
                characterClass,
                referenceDate
        ));
        // 무기를 안 낀 프리셋은 활 환산할 대상이 없다. 무기 공격력이 통째로 빠진
        // 값이 나오므로(실측 4명 -80~-95%) 정규화 실패와 같이 다룬다.
        // 왜 실패했는지는 따로 남긴다 - 미착용·미등록·표 결손은 성격이 다르다.
        ItemSnapShot weapon = result.getItemEquip().get("장비 - 무기");
        result.setWeaponMissing(weapon == null);
        result.setUnknownWeapon(
                weapon != null && !weaponData.isRegisteredWeapon(weapon.getItemName()));
        result.setWeaponNormalizationFailed(
                weapon == null
                        || result.getItemEquip().values().stream()
                                .anyMatch(ItemSnapShot::isWeaponNormalizationFailed));
        result.setExpiredTitleOption(
                result.getItemEquip().get("장비 - 칭호") != null
                        && result.getItemEquip().get("장비 - 칭호").getExpired() != null);
        result.setConversionStarforce(setConversionStarforce(presetItems, characterClass));
        result.setSetEffect(setSetEffect(setEffect, presetItems, characterClass));
        result.setConsumableItem(setConsumableItem(characterClass));
        result.setAbility(setAbility(ability, preset.abilityPreset(), result.getAbilityPoint()));
        result.setHyperStat(setHyperStat(hyper, preset.hyperStatPreset()));
        result.setUnionOccupied(setUnionOccupied(unionRaider, preset.unionRaiderPreset()));
        result.setUnionRaider(setUnionRaider(unionRaider, preset.unionRaiderPreset()));
        result.buildSum();
        return result;
    }

    /**
     * 모험가 해적이면 파이렛 블레스를 켠 쪽도 계산해 더 높은 쪽을 쓴다.
     *
     * <p>API가 이 스킬의 사용 여부를 주지 않는다. 다만 켜서 전투력이 떨어지는 세팅이라면
     * 아무도 켜지 않으므로, 높은 쪽이 실제 상태다. 손해라면 켠 적 없는 것으로 보고
     * 종합 시트도 원래대로 되돌린다.
     *
     * @return 둘 중 높은 전투력
     */
    private long applyPirateBlessIfBetter(
            DataSheet dataSheet, String characterClass, Integer characterLevel, long combatPower) {
        if (!gameData.isAdventurePirate(characterClass)) {
            return combatPower;
        }
        dataSheet.buildSum(true);
        long swapped = combatCalculationService.estimateCombatPower(dataSheet, characterClass, characterLevel);
        if (swapped > combatPower) {
            dataSheet.setPirateBlessApplied(true);
            return swapped;
        }
        dataSheet.buildSum(false);
        return combatPower;
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
        List<String> main = gameData.mainStats(characterClass);
        return statSheetParser.parseNoPercentStat(hexaParser.getCurrentHexa(node, main), "hexaStat");
    }

    private StatSheet setOtherStat(JsonNode node) {
        return statSheetParser.parse(otherStatParser.getOtherStatEffects(node), "otherStat");
    }

    private Map<String, ItemSnapShot> setPetEquip(JsonNode node, LocalDate referenceDate) {
        Map<String, ItemSnapShot> petEquip = new HashMap<>();
        List<ItemRecord> itemRecords = petParser.getItemSnapShot(node, referenceDate);
        for (ItemRecord itemRecord : itemRecords) {
            petEquip.put("펫 장비 - " + itemRecord.slot(), itemRecord.itemSnapShot());
        }
        return petEquip;
    }

    /**
     * 캐시 장비.
     *
     * <p>엔젤릭버스터·제로는 이중 캐시 장비 시스템이 있어
     * {@code additional_cash_item_equipment_base}가 따로 온다. 부위 이름이 겹치므로
     * 키를 나눠 담는다 — 한 맵에 같은 키로 넣으면 한쪽이 덮여 사라진다.
     */
    private Map<String, ItemSnapShot> setCashEquip(JsonNode node, LocalDate referenceDate, String characterClass) {
        Map<String, ItemSnapShot> itemEquip = new HashMap<>();
        putCashItems(itemEquip, node.path("cash_item_equipment_base"), "캐시 장비 - ", referenceDate);
        // 제로는 알파·베타가 장비 창을 공유해 같은 캐시 아이템이 두 목록에 그대로 다시 온다.
        // 표본 37명 중 29명이 base와 이름·옵션까지 동일했고, 이 몫을 빼면 추가 캐시에 스탯이
        // 없던 대조군 7명과 오차 범위가 겹친다(1.56~2.60% 대 1.59~2.60%).
        // 엔젤릭버스터는 변신 전/후가 서로 다른 부위를 채워 겹치지 않으므로 그대로 둔다.
        if (!ZERO.equals(characterClass)) {
            putCashItems(itemEquip, node.path("additional_cash_item_equipment_base"), "추가 캐시 장비 - ", referenceDate);
        }
        return itemEquip;
    }

    private void putCashItems(Map<String, ItemSnapShot> target, JsonNode items,
                              String keyPrefix, LocalDate referenceDate) {
        for (JsonNode item : items) {
            ItemRecord itemRecord = cashItemParser.getItemSnapShot(item, referenceDate);
            target.put(keyPrefix + itemRecord.slot(), itemRecord.itemSnapShot());
        }
    }

    /**
     * 예비 특수 반지 슬롯. 착용 중인 특수 반지가 하나도 없으면 이 슬롯의 스탯은 붙지 않는다.
     *
     * <p>실측(2026-08-26, 골든 480건): 예비 반지 보유 153명 중 착용 특수 반지가 0개인 사람은
     * 둘뿐이고, 그 둘만 예비 반지 수치(올스탯 4 · 공격력/마력 4)만큼 과대 계산됐다.
     * 빼면 둘 다 API와 정수까지 일치한다. 착용 특수 반지가 1개인 151명은 예비까지 정상 반영된다.
     */
    private static final String ZERO = "제로";
    private static final String RESERVE_SPECIAL_RING_SLOT = "예비 특수 반지";
    private static final String RING_SLOT_PREFIX = "반지";

    private Map<String, ItemSnapShot> setItemEquip(JsonNode items, JsonNode title, JsonNode dragon, JsonNode mechanic, String characterClass, LocalDate referenceDate) {
        Map<String, ItemSnapShot> itemEquip = new HashMap<>();
        ItemRecord titleRecord = itemParser.getTitleItemSnapShot(title, referenceDate);
        itemEquip.put("장비 - " + titleRecord.slot(), titleRecord.itemSnapShot());

        for (JsonNode item : dragon) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "드래곤 장비");
            String slot = itemRecord.slot();
            itemEquip.put("드래곤 장비 - " + slot, itemRecord.itemSnapShot());
        }

        for (JsonNode item : mechanic) {
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "메카닉 장비");
            String slot = itemRecord.slot();
            itemEquip.put("메카닉 장비 - " + slot, itemRecord.itemSnapShot());
        }

        boolean specialRingWorn = wearsSpecialRing(items);
        boolean zeroAstraEquipped = wearsAstraSubWeapon(items);
        for (JsonNode item : items) {
            // 예비 특수 반지는 착용 중인 특수 반지가 있을 때만 효과가 붙는다.
            if (!specialRingWorn && RESERVE_SPECIAL_RING_SLOT.equals(item.path("item_equipment_slot").asText(""))) {
                continue;
            }
            ItemRecord itemRecord = itemParser.getItemSnapShot(item, characterClass, "장비", zeroAstraEquipped);
            String slot = itemRecord.slot();
            // 제로는 보조무기 슬롯을 둘 쓴다(대검 + 아스트라 아워글라스). 슬롯만으로 키를 잡으면
            // 하나가 덮어써져 통째로 사라지므로 이미 찬 슬롯이면 아이템 이름을 붙여 구분한다.
            String key = "장비 - " + slot;
            if (itemEquip.containsKey(key)) {
                key = key + " - " + Jsons.text(item, "item_name");
            }
            itemEquip.put(key, itemRecord.itemSnapShot());
        }

        return itemEquip;
    }

    /**
     * 반지 슬롯에 특수 반지(시드링)를 끼고 있는가. {@code special_ring_level}이 0보다 크면 특수 반지다.
     *
     * <p>예비 슬롯 자체도 {@code special_ring_level}을 갖지만 슬롯 이름이 {@code 반지N}이 아니라
     * 여기 걸리지 않는다.
     */
    /** 보조무기 슬롯에 아스트라 아워글라스를 끼고 있는가. 제로만 해당한다. */
    private boolean wearsAstraSubWeapon(JsonNode items) {
        for (JsonNode item : items) {
            if ("보조무기".equals(item.path("item_equipment_slot").asText(""))
                    && item.path("item_name").asText("").contains("아스트라")) {
                return true;
            }
        }
        return false;
    }

    private boolean wearsSpecialRing(JsonNode items) {
        for (JsonNode item : items) {
            String slot = item.path("item_equipment_slot").asText("");
            if (slot.startsWith(RING_SLOT_PREFIX) && item.path("special_ring_level").asInt(0) > 0) {
                return true;
            }
        }
        return false;
    }

    private StatSheet setSetEffect(JsonNode node, JsonNode presetItems, String characterClass) {
        return statSheetParser.parse(setEffectParser.getSetEffectByPreset(node, presetItems, characterClass), "setEffect");
    }

    private StatSheet setConsumableItem(String characterClass) {
        StatSheet sheet = new StatSheet("consumableItem");
        sheet.setATTACK_POWER(gameData.consumableAttackOf(characterClass));
        return sheet;
    }

    private StatSheet setAbility(JsonNode node, int presetNo, StatSheet abilityPoint) {
        return statSheetParser.parseNoPercentStat(
                abilityParser.resolveApConversions(
                        abilityParser.getCurrentAbilityByPreset(node, presetNo), abilityPoint),
                "ability");
    }

    private StatSheet setHyperStat(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(hyperStatParser.getStatIncreaseEffects(node, presetNo), "hyperStat");
    }


    /**
     * 컨버전 스타포스(제논·데몬어벤져). 장착 장비의 스타포스 합 10성당 올스탯 7이 붙는다.
     *
     * <p>합은 100성에서 자르고, 훈장·칭호의 스타포스는 세지 않는다. 길드 스킬로 올라간
     * 스타포스도 대상이 아니지만 API가 장비 단위로만 주므로 구분할 수 없다.
     * 해당 직업이 아니면 빈 시트를 준다.
     */
    private StatSheet setConversionStarforce(JsonNode presetItems, String characterClass) {
        if (!gameData.hasConversionStarforce(characterClass)) {
            return new StatSheet("conversionStarforce");
        }
        int starSum = 0;
        for (JsonNode item : presetItems) {
            if (gameData.isConversionExcludedSlot(Jsons.text(item, "item_equipment_slot"))) {
                continue;
            }
            starSum += item.path("starforce").asInt(0);
        }
        int allStat = gameData.conversionStarforceAllStat(starSum);
        return statSheetParser.parse(
                allStat == 0 ? List.of() : List.of("올스탯 " + allStat), "conversionStarforce");
    }

    /**
     * 챌린저스가 아닌 월드인데 공격대 데이터가 비어 있으면 유니온 정보가 없는 것이다.
     * 챌린저스는 유니온 시스템 자체가 없으므로 비어 있는 것이 정상이라 제외한다.
     */
    private boolean isUnionRaiderDataMissing(JsonNode raider, String worldName) {
        return !challengersBuffs.appliesTo(worldName) && !raiderParser.hasRaiderData(raider);
    }

    /** 만료로 스탯이 빠진 항목 수. 스탯이 없던 항목은 파서가 표시하지 않으므로 세지 않는다. */
    private int countExpired(Map<String, ItemSnapShot> items) {
        if (items == null) {
            return 0;
        }
        return (int) items.values().stream().filter(item -> item.getExpired() != null).count();
    }

    private StatSheet setUnionChampion(JsonNode node, String characterName) {
        return statSheetParser.parse(
                championParser.getChampionStats(node, characterName), "unionChampion");
    }

    private StatSheet setUnionOccupied(JsonNode node, int presetNo) {
        return statSheetParser.parse(raiderParser.getUnionOccupiedStatByPreset(node, presetNo), "unionOccupied");
    }

    private StatSheet setUnionRaider(JsonNode node, int presetNo) {
        return statSheetParser.parseNoPercentStat(raiderParser.getUnionRaiderStatByPreset(node, presetNo), "unionRaider");
    }

    /** 직렬화 형식이 바뀌면 접두사 버전을 올려 옛 값과 섞이지 않게 한다. */
    private String dataSheetCacheKey(String ocid, LocalDate date) {
        return "maple:datasheet:v11:" + normalizeOcid(ocid) + ":" + date;
    }

    private String normalizeOcid(String ocid) {
        return ocid == null ? "" : ocid.trim();
    }

}
