package org.whitedoggy.mapleweb2.domain.combat.parser;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class ItemParser {
    public List<String> getItemStatEffects(JsonNode item) {
        List<String> effects = new ArrayList<>();
        addStructuredOptionEffects(effects, item.path("item_total_option"));
        addStructuredOptionEffects(effects, item.path("item_exceptional_option"));

        for (String option : getPotentialOptions(item)) {
            EffectTextSplitter.addSplit(effects, option);
        }
        for (String option : getAdditionalPotentialOptions(item)) {
            EffectTextSplitter.addSplit(effects, option);
        }

        EffectTextSplitter.addSplit(effects, Jsons.text(item, "soul_option"));
        return effects;
    }

    public List<String> getTitleStatEffects(JsonNode item) {
        List<String> effects = new ArrayList<>();
        if(Jsons.text(item, "date_expire").equals("expired")) return effects;
        String title = Jsons.text(item, "title_description");
        effects.addAll(List.of(title.split("\n")));
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
        append(effects, "올스탯", option.path("all_stat").asInt(0));
        append(effects, "공격력", option.path("attack_power").asInt(0));
        append(effects, "마력", option.path("magic_power").asInt(0));
        appendPercent(effects, "데미지", option.path("damage").asInt(0));
        appendPercent(effects, "보스 몬스터 공격 시 데미지", option.path("boss_damage").asInt(0));
        appendPercent(effects, "HP", option.path("max_hp_rate").asInt(0));
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
