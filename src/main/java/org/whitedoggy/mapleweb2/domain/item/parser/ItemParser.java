package org.whitedoggy.mapleweb2.domain.item.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.common.support.ExpiryDates;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.domain.item.support.BowNormalization;
import org.whitedoggy.mapleweb2.domain.item.support.WeaponData;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ItemParser {
    private final BowNormalization bowNormalization;
    private final StatSheetParser statSheetParser;
    private final GameData gameData;
    private final WeaponData weaponData;

    public ItemRecord getItemSnapShot(JsonNode item, String characterClass, String type) {
        return getItemSnapShot(item, characterClass, type, false);
    }

    /**
     * @param zeroAstraEquipped 제로가 아스트라 아워글라스를 함께 착용 중인가.
     *                          착용 중이면 대검(보조무기)이 주던 데미지·보스 데미지·방어율 무시가
     *                          아스트라의 능력치로 대체되어 적용되지 않는다.
     *                          출처: 넥슨 공식 공지 1.2.411 (아스트라 보조무기)
     */
    public ItemRecord getItemSnapShot(JsonNode item, String characterClass, String type, boolean zeroAstraEquipped) {
        String itemSlot = Jsons.text(item, "item_equipment_slot");
        if(type.equals("장비")) {
            if (itemSlot.equals("무기")) return getWeaponItemSnapShot(item, characterClass);
            else if (itemSlot.equals("보조무기")) return getSubWeaponItemSnapShot(item, characterClass, zeroAstraEquipped);
            else return getNormalItemSnapShot(item);
        }
        else return getNormalItemSnapShot(item);
    }

    /**
     * 아스트라 아워글라스에서 전투력에 넣을 옵션.
     *
     * <p>기본 옵션의 보스 공격 시 데미지(45%)와 몬스터 방어율 무시(20%)는 실제 데미지에는
     * 들어가지만 <b>표기 전투력에는 반영되지 않는다</b>. 그래서 총합에서 기본 옵션 몫만 뺀다.
     * 스타포스·잠재·에디셔널에서 오는 보스 데미지는 그대로 둔다.
     *
     * <p>출처: 인벤 전사 게시판 "제로 아스트라 보조 기본옵션 '보공45%' 전투력 반영 X",
     * 나무위키 아스트라 보조무기. 표본 40명 측정에서도 이 값을 빼면 아스트라 착용 그룹의
     * 오차 범위가 미착용 그룹과 정확히 겹친다(1.59~7.24% 대 1.90~7.16%).
     */
    private JsonNode astraCombatPowerOption(JsonNode item, JsonNode totalOption) {
        int baseBoss = item.path("item_base_option").path("boss_damage").asInt(0);
        if (baseBoss == 0) {
            return totalOption;
        }
        ObjectNode adjusted = (ObjectNode) totalOption.deepCopy();
        adjusted.put("boss_damage", String.valueOf(totalOption.path("boss_damage").asInt(0) - baseBoss));
        return adjusted;
    }

    /** 아스트라 아워글라스가 대체하는 효과. 이 이름으로 시작하는 효과는 대검에서 오지 않는다. */
    private static final List<String> ASTRA_REPLACED_EFFECTS =
            List.of("데미지", "보스 몬스터 공격 시 데미지", "보스 몬스터 데미지", "몬스터 방어율 무시");

    private boolean isReplacedByAstra(String effect) {
        for (String name : ASTRA_REPLACED_EFFECTS) {
            if (effect.startsWith(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 칭호. 옵션 기간이 지나면 칭호는 남아도 스탯만 사라진다.
     * 스탯이 없는 칭호(연출용)는 만료돼도 전투력과 무관하므로 표시하지 않는다.
     */
    public ItemRecord getTitleItemSnapShot(JsonNode item, LocalDate referenceDate){
        String itemName = Jsons.text(item, "title_name");
        String itemIcon = Jsons.text(item, "title_icon");
        String description = Jsons.text(item, "title_description");
        String itemSlot = "칭호";

        ItemSnapShot snapShot = new ItemSnapShot(itemName, itemIcon);
        StatSheet statSheet = new StatSheet(itemName);

        List<String> effects = new ArrayList<>();
        EffectTextSplitter.addSplit(effects, description);
        StatSheet parsed = statSheetParser.parse(effects);

        boolean expired = ExpiryDates.isAnyExpired(referenceDate,
                Jsons.text(item, "date_expire"), Jsons.text(item, "date_option_expire"));
        if (expired) {
            if (!parsed.isZero()) {
                snapShot.setExpired("옵션 기간 만료");
            }
        } else {
            statSheet.merge(parsed);
        }
        snapShot.setStatSheet(statSheet);
        return new ItemRecord(itemSlot, snapShot);
    }

    private ItemRecord getSubWeaponItemSnapShot(JsonNode item, String characterClass, boolean zeroAstraEquipped){
        String itemName = Jsons.text(item, "item_name");
        String itemIcon = Jsons.text(item, "item_icon");
        String itemSlot = Jsons.text(item, "item_equipment_slot");
        Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));

        JsonNode totalOptionNode = item.path("item_total_option");

        String p_grade = Jsons.text(item, "potential_option_grade");
        String ap_grade = Jsons.text(item, "addtional_potential_option_grade");

        ItemSnapShot snapShot = new ItemSnapShot(itemName, itemIcon);
        StatSheet statSheet = new StatSheet(itemName);

        snapShot.setStarForce(starForce);
        snapShot.setP_grade(p_grade);
        snapShot.setAp_grade(ap_grade);

        List<String> effects = new ArrayList<>();

        //제로의 경우
        //아스트라 보조무기 O -> 기존 방식 그대로
        //아스트라 보조무기 X -> 보조무기의 잠재 옵션만 반영.
        boolean isZero = false;
        boolean isAstra = false;
        if(characterClass.equals("제로")){
            isZero = true;
        }
        if(itemName.contains("아스트라")){
            isAstra = true;
        }

        //제로O
        if(isZero){
            //아스트라 O
            if(isAstra){
                addStructuredOptionEffects(effects, astraCombatPowerOption(item, totalOptionNode));
            }
            for(String option : getPotentialOptions(item)){
                EffectTextSplitter.addSplit(effects, option);
            }

            for(String option : getAdditionalPotentialOptions(item)){
                EffectTextSplitter.addSplit(effects, option);
            }
            // 아스트라를 끼면 대검(기본 보조무기)의 효과는 아스트라 것으로 대체되므로 통째로 뺀다.
            if(!isAstra && zeroAstraEquipped){
                effects.clear();
            }
        }
        //제로X
        else {
            addStructuredOptionEffects(effects, totalOptionNode);

            for (String option : getPotentialOptions(item)) {
                EffectTextSplitter.addSplit(effects, option);
            }

            for (String option : getAdditionalPotentialOptions(item)) {
                EffectTextSplitter.addSplit(effects, option);
            }
        }
        statSheet.merge(statSheetParser.parse(effects));
        snapShot.setStatSheet(statSheet);

        return new ItemRecord(itemSlot, snapShot);
    }

    private ItemRecord getWeaponItemSnapShot(JsonNode item, String characterClass){
        String itemName = Jsons.text(item, "item_name");
        String itemIcon = Jsons.text(item, "item_icon");
        String itemSlot = Jsons.text(item, "item_equipment_slot");
        String itemPart = Jsons.text(item, "item_equipment_part");
        Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));

        JsonNode totalOptionNode = item.path("item_total_option");

        String p_grade = Jsons.text(item, "potential_option_grade");
        String ap_grade = Jsons.text(item, "additional_potential_option_grade");

        ItemSnapShot snapShot = new ItemSnapShot(itemName, itemIcon);
        StatSheet statSheet = new StatSheet(itemName);

        snapShot.setStarForce(starForce);
        snapShot.setP_grade(p_grade);
        snapShot.setAp_grade(ap_grade);

        List<String> effects = new ArrayList<>();

        addStructuredOptionEffectsForWeapon(effects, totalOptionNode);

        // 무기 정규화. 제로의 라즐리(태도)도 표에 있어 같은 방식으로 환산한다.
        // 데스티니 22성 = 스타포스 626 + 작 72 + 1추 활환산 251 = 949.
        int addOption;
        if (gameData.isMagicWeaponPart(itemPart)) {
            addOption = Integer.parseInt(Jsons.text(item.path("item_add_option"), "magic_power"));
        } else {
            addOption = Integer.parseInt(Jsons.text(item.path("item_add_option"), "attack_power"));
        }
        var normalized = bowNormalization.normalize(
                itemPart, itemName, starForce, addOption, scrollUpgrade(item));
        effects.addAll(normalized.effects());
        snapShot.setWeaponNormalizationFailed(!normalized.stageResolved());

        //무기 소울 옵션
        EffectTextSplitter.addSplit(effects, Jsons.text(item, "soul_option"));

        for(String option : getPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
        }

        for(String option : getAdditionalPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
        }
        statSheet.merge(statSheetParser.parse(effects));
        snapShot.setStatSheet(statSheet);

        return new ItemRecord(itemSlot, snapShot);
    }

    private ItemRecord getNormalItemSnapShot(JsonNode item){
        String itemName = Jsons.text(item, "item_name");
        String itemIcon = Jsons.text(item, "item_icon");
        String itemSlot = Jsons.text(item, "item_equipment_slot");
        Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));

        JsonNode totalOptionNode = item.path("item_total_option");
        JsonNode exceptionalOptionNode = item.path("item_exceptional_option");
        String itemDescription = Jsons.text(item, "item_description");

        String p_grade = Jsons.text(item, "potential_option_grade");
        String ap_grade = Jsons.text(item, "addtional_potential_option_grade");

        ItemSnapShot snapShot = new ItemSnapShot(itemName, itemIcon);
        StatSheet statSheet = new StatSheet(itemName);

        snapShot.setStarForce(starForce);
        snapShot.setP_grade(p_grade);
        snapShot.setAp_grade(ap_grade);

        List<String> effects = new ArrayList<>();
        addStructuredOptionEffects(effects,totalOptionNode);
        addStructuredOptionEffects(effects, exceptionalOptionNode);

        for(String option : getPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
        }

        for(String option : getAdditionalPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
        }

        //다크 크리티컬 링 등 반영.
        EffectTextSplitter.addSplit(effects, itemDescription);
        statSheet.merge(statSheetParser.parse(effects));
        snapShot.setStatSheet(statSheet);

        return new ItemRecord(itemSlot, snapShot);
    }

    private void addStructuredOptionEffectsV2(List<String> l1, List<String> l2, JsonNode option) {
        appendDual(l1, l2, "STR", option.path("str").asInt(0));
        appendDual(l1, l2, "DEX", option.path("dex").asInt(0));
        appendDual(l1, l2, "INT", option.path("int").asInt(0));
        appendDual(l1, l2, "LUK", option.path("luk").asInt(0));
        appendDual(l1, l2, "HP", option.path("max_hp").asInt(0));
        appendDualPercent(l1, l2, "올스탯", option.path("all_stat").asInt(0));
        appendDual(l1, l2, "공격력", option.path("attack_power").asInt(0));
        appendDual(l1, l2, "마력", option.path("magic_power").asInt(0));
        appendDualPercent(l1, l2, "데미지", option.path("damage").asInt(0));
        appendDualPercent(l1, l2, "보스 몬스터 공격 시 데미지", option.path("boss_damage").asInt(0));
        appendDualPercent(l1, l2, "HP", option.path("max_hp_rate").asInt(0));
        append(l2, "MP", option.path("max_mp").asInt());
        append(l2, "방어력", option.path("armor").asInt());
        append(l2, "이동속도", option.path("speed").asInt());
        append(l2, "점프력", option.path("jump").asInt());
        appendPercent(l2, "몬스터 방어율 무시", option.path("ignore_monster_armor").asInt());
        appendPercent(l2, "MP", option.path("max_mp_rate").asInt());
    }

    private void addStructuredOptionEffectsForWeaponV2(List<String> l1, List<String> l2, JsonNode option) {
        appendDual(l1, l2, "STR", option.path("str").asInt(0));
        appendDual(l1, l2, "DEX", option.path("dex").asInt(0));
        appendDual(l1, l2, "INT", option.path("int").asInt(0));
        appendDual(l1, l2, "LUK", option.path("luk").asInt(0));
        appendDual(l1, l2, "HP", option.path("max_hp").asInt(0));
        appendDualPercent(l1, l2, "올스탯", option.path("all_stat").asInt(0));
        append(l2, "공격력", option.path("attack_power").asInt(0));
        append(l2, "마력", option.path("magic_power").asInt(0));
        appendDualPercent(l1, l2, "데미지", option.path("damage").asInt(0));
        appendDualPercent(l1, l2, "보스 몬스터 공격 시 데미지", option.path("boss_damage").asInt(0));
        appendDualPercent(l1, l2, "HP", option.path("max_hp_rate").asInt(0));
        append(l2, "MP", option.path("max_mp").asInt());
        append(l2, "방어력", option.path("armor").asInt());
        append(l2, "이동속도", option.path("speed").asInt());
        append(l2, "점프력", option.path("jump").asInt());
        appendPercent(l2, "몬스터 방어율 무시", option.path("ignore_monster_armor").asInt());
        appendPercent(l2, "MP", option.path("max_mp_rate").asInt());
    }




    private Integer getStarForce15(Integer baseAttack, Integer scrollAttack){
        return (int) Math.floor((baseAttack + scrollAttack) / 50.0) + 1;
    }

    private Integer getNormalizedAttackForZero(JsonNode item){
        String name = Jsons.text(item, "item_name");
        String family = null;
        if(name.contains("제네시스")){
            family = "제네시스";
        }
        if(name.contains("데스티니")){
            family = "데스티니";
        }
        if(name.contains("9형")){
            family = "아케인셰이드";
        }
        if(name.contains("8형")){
            family = "앱솔랩스";
        }
        Integer registered = weaponData.zeroBaseAttackOf(name);
        if (registered == null) {
            throw new IllegalStateException("game-data.yml의 maple.game.weapon.zero-base-attack에 없는 제로 무기입니다: " + name);
        }
        double bowAttack = registered;

        double itemBaseAttack = Integer.parseInt(Jsons.text(item.path("item_base_option"), "attack_power"));
        double itemAddAttack = Integer.parseInt(Jsons.text(item.path("item_add_option"), "attack_power"));
        double itemStarAttack = Integer.parseInt(Jsons.text(item.path("item_starforce_option"), "attack_power"));

        Integer addStage = weaponData.findStage(family, "태도", (int) itemAddAttack);
        double bowAddAttack = weaponData.bowAddOption(family, addStage);

        System.out.println(itemBaseAttack);
        System.out.println(itemAddAttack);
        System.out.println(itemStarAttack);
        System.out.println(bowAttack);
        System.out.println(bowAddAttack);

        double residue = 0.0; // 이후 보정 값을 찾는 과정이 필요함.

        return (int) Math.floor(
                (((bowAttack / itemBaseAttack) - 1.0)
                        * (itemBaseAttack + itemStarAttack)
        ) + residue);
    }

    public StatSheet getItemStatEffectsFromList(JsonNode items, String characterClass){
        StatSheet sumSheet = new StatSheet("장착 아이템");

        if(characterClass.equals("제로")) {
            List<String> weaponPotentials = new ArrayList<>();
            boolean isAstra = false;

            for (JsonNode item : items) {
                List<String> effects = new ArrayList<>();
                String part = Jsons.text(item, "item_equipment_part");
                String slot = Jsons.text(item, "item_equipment_slot");
                String name = Jsons.text(item, "item_name");
                if(slot.equals("무기")){
                    addStructuredOptionEffects(effects, item.path("item_total_option")); // 무기 전체 옵션
                    EffectTextSplitter.addSplit(effects, Jsons.text(item, "soul_option")); // 소울 옵션
                    int normalized = getNormalizedAttackForZero(item);
                    effects.add("공격력 " + (normalized)); // 공격력 정규화

                    // 잠재, 에디 저장
                    for (String option : getPotentialOptions(item)) {
                        EffectTextSplitter.addSplit(weaponPotentials, option);
                    }
                    for (String option : getAdditionalPotentialOptions(item)) {
                        EffectTextSplitter.addSplit(weaponPotentials, option);
                    }
                }
                else if(slot.equals("보조무기")){
                    if(name.contains("아스트라")){
                        addStructuredOptionEffects(effects, item.path("item_total_option")); // 무기 전체 옵션
                        // 잠재, 에디 저장
                        for (String option : getPotentialOptions(item)) {
                            EffectTextSplitter.addSplit(effects, option);
                        }
                        for (String option : getAdditionalPotentialOptions(item)) {
                            EffectTextSplitter.addSplit(effects, option);
                        }
                        isAstra = true;
                    }
                }
                else{
                    addStructuredOptionEffects(effects, item.path("item_total_option"));
                    addStructuredOptionEffects(effects, item.path("item_exceptional_option"));
                    EffectTextSplitter.addSplit(effects, Jsons.text(item, "item_description"));
                    for (String option : getPotentialOptions(item)) {
                        EffectTextSplitter.addSplit(effects, option);
                    }
                    for (String option : getAdditionalPotentialOptions(item)) {
                        EffectTextSplitter.addSplit(effects, option);
                    }
                }
                sumSheet.merge(statSheetParser.parse(effects, slot + ", " + name));
            }
            if(isAstra){
                sumSheet.merge(statSheetParser.parse(weaponPotentials, "제로 무기 잠재"));
            }
            else{
                sumSheet.merge(statSheetParser.parse(weaponPotentials, "제로 무기 잠재 #1"));
                sumSheet.merge(statSheetParser.parse(weaponPotentials, "제로 무기 잠재 #2"));
            }
        }

        return sumSheet;
    }

    public List<String> getItemStatEffects(JsonNode item, String characterClass) {
        List<String> effects = new ArrayList<>();
        String slot = Jsons.text(item, "item_equipment_slot");
        String part = Jsons.text(item, "item_equipment_part");
        boolean isZeroSubWeapon = false;
        boolean isZeroMainWeapon = false;

        if(characterClass.equals("제로")){
            if(slot.equals("무기")){
                addStructuredOptionEffects(effects, item.path("item_total_option"));
                addStructuredOptionEffects(effects, item.path("item_exceptional_option"));
                int normalized = getNormalizedAttackForZero(item);
                effects.add("공격력 " + (normalized));
                EffectTextSplitter.addSplit(effects, Jsons.text(item, "soul_option"));
            }
            else if(slot.equals("보조무기")){
                String name = Jsons.text(item, "item_name");
                if(name.contains("아스트라")){
                    addStructuredOptionEffectsForWeapon(effects, item.path("item_total_option"));
                    addStructuredOptionEffects(effects, item.path("item_exceptional_option"));
                }
                else{
                    isZeroSubWeapon = true;
                }
            }
            else {
                addStructuredOptionEffects(effects, item.path("item_total_option"));
                addStructuredOptionEffects(effects, item.path("item_exceptional_option"));
            }
        }
        else{
            if(slot.equals("무기")){
                addStructuredOptionEffectsForWeapon(effects, item.path("item_total_option"));
                addStructuredOptionEffectsForWeapon(effects, item.path("item_exceptional_option"));
                String addOption = null;
                if (gameData.isMagicWeaponPart(part)) {
                    addOption = Jsons.text(item.path("item_add_option"), "magic_power");
                } else {
                    addOption = Jsons.text(item.path("item_add_option"), "attack_power");
                }
                String name = Jsons.text(item, "item_name");
                Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));
                effects.addAll(bowNormalization.buildNormalizedBow(
                        part, name, starForce, Integer.parseInt(addOption), scrollUpgrade(item)));
                EffectTextSplitter.addSplit(effects, Jsons.text(item, "soul_option"));
            }
            else {
                addStructuredOptionEffects(effects, item.path("item_total_option"));
                addStructuredOptionEffects(effects, item.path("item_exceptional_option"));
            }
        }

        if(!isZeroSubWeapon) {
            for (String option : getPotentialOptions(item)) {
                EffectTextSplitter.addSplit(effects, option);
            }
            for (String option : getAdditionalPotentialOptions(item)) {
                EffectTextSplitter.addSplit(effects, option);
            }
        }

        EffectTextSplitter.addSplit(effects, Jsons.text(item, "item_description"));

        return effects;
    }

    public List<String> getTitleStatEffects(JsonNode item, LocalDate referenceDate) {
        List<String> effects = new ArrayList<>();
        if (ExpiryDates.isAnyExpired(referenceDate,
                Jsons.text(item, "date_expire"), Jsons.text(item, "date_option_expire"))) {
            return effects;
        }
        String title = Jsons.text(item, "title_description");
        EffectTextSplitter.addSplit(effects, title);
        return effects;
    }

    public List<String> getPotentialOptions(JsonNode item) {
        return List.of(
                Jsons.text(item, "potential_option_1"),
                Jsons.text(item, "potential_option_2"),
                Jsons.text(item, "potential_option_3")
        );
    }

    public List<String> getAdditionalPotentialOptions(JsonNode item) {
        return List.of(
                Jsons.text(item, "additional_potential_option_1"),
                Jsons.text(item, "additional_potential_option_2"),
                Jsons.text(item, "additional_potential_option_3")
        );
    }

    private void addStructuredOptionEffects(List<String> effects, JsonNode option) {
        append(effects, "STR", option.path("str").asInt(0));
        append(effects, "DEX", option.path("dex").asInt(0));
        append(effects, "INT", option.path("int").asInt(0));
        append(effects, "LUK", option.path("luk").asInt(0));
        append(effects, "HP", option.path("max_hp").asInt(0));
        appendPercent(effects, "올스탯", option.path("all_stat").asInt(0));
        append(effects, "공격력", option.path("attack_power").asInt(0));
        append(effects, "마력", option.path("magic_power").asInt(0));
        appendPercent(effects, "데미지", option.path("damage").asInt(0));
        appendPercent(effects, "보스 몬스터 공격 시 데미지", option.path("boss_damage").asInt(0));
        appendPercent(effects, "HP", option.path("max_hp_rate").asInt(0));
    }

    private void addStructuredOptionEffectsForWeapon(List<String> effects, JsonNode option) {
        append(effects, "STR", option.path("str").asInt(0));
        append(effects, "DEX", option.path("dex").asInt(0));
        append(effects, "INT", option.path("int").asInt(0));
        append(effects, "LUK", option.path("luk").asInt(0));
        append(effects, "HP", option.path("max_hp").asInt(0));
        appendPercent(effects, "올스탯", option.path("all_stat").asInt(0));
        appendPercent(effects, "데미지", option.path("damage").asInt(0));
        appendPercent(effects, "보스 몬스터 공격 시 데미지", option.path("boss_damage").asInt(0));
        appendPercent(effects, "HP", option.path("max_hp_rate").asInt(0));
    }

    private void addStructuredOptionEffectsForZeroSubWeapon(List<String> effects, JsonNode option) {
        appendPercent(effects, "데미지", option.path("damage").asInt(0));
        appendPercent(effects, "보스 몬스터 공격 시 데미지", option.path("boss_damage").asInt(0));
    }

    private void append(List<String> effects, String statName, int value) {
        if (value > 0) {
            effects.add(statName + " " + value);
        }
    }

    private void appendPercent(List<String> effects, String statName, int value) {
        if (value > 0) {
            effects.add(statName + " " + value + "%");
        }
    }

    private void appendDual(List<String> l1, List<String> l2, String statName, int value){
        append(l1, statName, value);
        append(l2, statName, value);
    }

    private void appendDualPercent(List<String> l1, List<String> l2, String statName, int value){
        appendPercent(l1, statName, value);
        appendPercent(l2, statName, value);
    }

    /** 주문서 작 횟수. 무기 정규화에서 작 1회당 12가 갈린다. 없으면 null. */
    private Integer scrollUpgrade(JsonNode item) {
        String raw = Jsons.text(item, "scroll_upgrade");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
