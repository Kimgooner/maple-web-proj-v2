package org.whitedoggy.mapleweb2.domain.item.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.domain.item.support.BowNormalization;
import org.whitedoggy.mapleweb2.domain.item.support.WeaponAddOptionTable;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ItemParser {
    private final BowNormalization bowNormalization;
    private final StatSheetParser statSheetParser;

    private ItemRecord getTitleItemSnapShot(JsonNode item){
        String itemName = Jsons.text(item, "title_name");
        String itemIcon = Jsons.text(item, "title_icon");
        String expired = Jsons.text(item, "date_option_expire");
        String description = Jsons.text(item, "title_description");
        String itemSlot = "칭호";

        ItemSheet itemSheet = new ItemSheet(itemName);
        StatSheet statSheet = new StatSheet(itemName);

        List<String> effects = new ArrayList<>();
        if(!expired.equals("expired")){
            EffectTextSplitter.addSplit(effects, description);
        }
        else{
            itemSheet.setExpired("옵션 기간 만료");
        }

        itemSheet.setItemIcon(itemIcon);
        itemSheet.setItemDescription(description);
        statSheet.merge(statSheetParser.parse(effects));

        return new ItemRecord(itemSlot, new ItemSnapShot(itemSheet, statSheet));
    }

    private ItemRecord getWeaponItemSnapShot(JsonNode item, String characterClass){
        String itemName = Jsons.text(item, "item_name");
        String itemIcon = Jsons.text(item, "item_icon");
        String itemSlot = Jsons.text(item, "item_equipment_slot");
        Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));

        JsonNode totalOptionNode = item.path("item_total_option");

        String p_grade = Jsons.text(item, "potential_option_grade");
        String ap_grade = Jsons.text(item, "addtional_potential_option_grade");

        ItemSheet itemSheet = new ItemSheet(itemName);
        StatSheet statSheet = new StatSheet(itemName);

        List<String> effects = new ArrayList<>();
        List<String> totalOptions = new ArrayList<>();
        List<String> potentialOptions = new ArrayList<>();
        List<String> additionalPotentialOptions = new ArrayList<>();

        addStructuredOptionEffectsForWeaponV2(effects, totalOptions, totalOptionNode);

        for(String option : getPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
            potentialOptions.add(option);
        }

        for(String option : getAdditionalPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
            additionalPotentialOptions.add(option);
        }

        itemSheet.setItemIcon(itemIcon);
        itemSheet.setStarForce(starForce);
        itemSheet.setItemTotalOption(totalOptions);

        itemSheet.setPotentialGrade(p_grade);
        itemSheet.setItemPotentialOption(potentialOptions);

        itemSheet.setAdditionalPotentialGrade(ap_grade);
        itemSheet.setItemAdditionalPotentialOption(additionalPotentialOptions);

        statSheet.merge(statSheetParser.parse(effects));
        return new ItemRecord(itemSlot, new ItemSnapShot(itemSheet, statSheet));
    }

    private ItemRecord getNormalItemSnapShot(JsonNode item){
        String itemName = Jsons.text(item, "item_name");
        String itemIcon = Jsons.text(item, "item_icon");
        String itemSlot = Jsons.text(item, "item_equipment_slot");
        Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));

        JsonNode totalOptionNode = item.path("item_total_option");
        JsonNode exceptionalOptionNode = item.path("item_exceptional_option");

        String p_grade = Jsons.text(item, "potential_option_grade");
        String ap_grade = Jsons.text(item, "addtional_potential_option_grade");

        ItemSheet itemSheet = new ItemSheet(itemName);
        StatSheet statSheet = new StatSheet(itemName);

        List<String> effects = new ArrayList<>();
        List<String> totalOptions = new ArrayList<>();
        addStructuredOptionEffectsV2(effects, totalOptions, totalOptionNode);

        List<String> exceptionalOptions = new ArrayList<>();
        addStructuredOptionEffectsV2(effects, exceptionalOptions, exceptionalOptionNode);

        List<String> potentialOptions = new ArrayList<>();
        for(String option : getPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
            potentialOptions.add(option);
        }

        List<String> additionalPotentialOptions = new ArrayList<>();
        for(String option : getAdditionalPotentialOptions(item)){
            EffectTextSplitter.addSplit(effects, option);
            additionalPotentialOptions.add(option);
        }

        itemSheet.setItemIcon(itemIcon);
        itemSheet.setStarForce(starForce);
        itemSheet.setItemTotalOption(totalOptions);
        itemSheet.setItemExceptionalOption(exceptionalOptions);

        itemSheet.setPotentialGrade(p_grade);
        itemSheet.setItemPotentialOption(potentialOptions);

        itemSheet.setAdditionalPotentialGrade(ap_grade);
        itemSheet.setItemAdditionalPotentialOption(additionalPotentialOptions);

        statSheet.merge(statSheetParser.parse(effects));
        return new ItemRecord(itemSlot, new ItemSnapShot(itemSheet, statSheet));
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


    private List<String> magicTypes = List.of(
            "완드",
            "샤이닝 로드",
            "ESP 리미터",
            "매직 건틀렛",
            "스태프"
    );

    private Map<String, Integer> BOW_BASE_ATTACK = Map.of(
            "라피스 8형", 192,
            "라피스 9형", 276,
            "제네시스 라피스", 318,
            "데스티니 라피스", 349,
            "라즐리 8형", 192,
            "라즐리 9형", 276,
            "제네시스 라즐리", 318,
            "데스티니 라즐리", 349
    );

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
        double bowAttack = BOW_BASE_ATTACK.get(name);

        double itemBaseAttack = Integer.parseInt(Jsons.text(item.path("item_base_option"), "attack_power"));
        double itemAddAttack = Integer.parseInt(Jsons.text(item.path("item_add_option"), "attack_power"));
        double itemStarAttack = Integer.parseInt(Jsons.text(item.path("item_starforce_option"), "attack_power"));

        Integer addStage = WeaponAddOptionTable.findStage(family, "태도", (int) itemAddAttack);
        double bowAddAttack = WeaponAddOptionTable.bowAddOption(family, addStage);

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
                if (magicTypes.contains(part)) {
                    addOption = Jsons.text(item.path("item_add_option"), "magic_power");
                } else {
                    addOption = Jsons.text(item.path("item_add_option"), "attack_power");
                }
                String name = Jsons.text(item, "item_name");
                Integer starForce = Integer.parseInt(Jsons.text(item, "starforce"));
                effects.addAll(bowNormalization.buildNormalizedBow(part, name, starForce, Integer.parseInt(addOption)));
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

    public List<String> getTitleStatEffects(JsonNode item) {
        List<String> effects = new ArrayList<>();
        if(Jsons.text(item, "date_option_expire").equals("expired")) return effects;
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
}
