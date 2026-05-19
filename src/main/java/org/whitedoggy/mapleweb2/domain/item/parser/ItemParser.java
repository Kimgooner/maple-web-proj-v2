package org.whitedoggy.mapleweb2.domain.item.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.item.support.BowNormalization;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ItemParser {
    private final BowNormalization bowNormalization;

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
            "데스티니 라피스", 349
    );

    private Integer getNormalizedAttackForZero(JsonNode item){
        String name = Jsons.text(item, "item_name");
        double bowAttack = BOW_BASE_ATTACK.get(name);
        double itemBaseAttack = Integer.parseInt(Jsons.text(item.path("item_base_option"), "attack_power"));
        double itemStarAttack = Integer.parseInt(Jsons.text(item.path("item_starforce_option"), "attack_power"));
        return (int) Math.floor(((bowAttack / itemBaseAttack) - 1.0) * (itemBaseAttack + itemStarAttack));
    }

    public List<String> getItemStatEffects(JsonNode item, String characterClass) {
        List<String> effects = new ArrayList<>();
        String slot = Jsons.text(item, "item_equipment_slot");
        String part = Jsons.text(item, "item_equipment_part");
        boolean isZeroSubWeapon = false;

        if(characterClass.equals("제로")){
            if(slot.equals("보조무기")){
                isZeroSubWeapon = true;
            }
        }

        if(!isZeroSubWeapon) {
            if (!characterClass.equals("제로")) {
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
                }
                else {
                    addStructuredOptionEffects(effects, item.path("item_total_option"));
                    addStructuredOptionEffects(effects, item.path("item_exceptional_option"));
                }
            }
            else{
                if(slot.equals("무기")){
                    if(!part.equals("태도")){

                    }
                }
            }
        }
        else{

        }

        for (String option : getPotentialOptions(item)) {
            EffectTextSplitter.addSplit(effects, option);
        }
        for (String option : getAdditionalPotentialOptions(item)) {
            EffectTextSplitter.addSplit(effects, option);
        }

        if(!isZeroSubWeapon) EffectTextSplitter.addSplit(effects, Jsons.text(item, "soul_option"));
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
}
