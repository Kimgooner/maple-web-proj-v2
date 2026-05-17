package org.whitedoggy.mapleweb2.domain.combat.parser;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class HyperStatParser {
    public Integer getCurrentPresetNo(JsonNode hyperStat) {
        return Math.max(1, Jsons.parseInt(Jsons.text(hyperStat, "use_preset_no")));
    }

    public List<Integer> availablePresets(JsonNode hyperStat) {
        List<Integer> presets = new ArrayList<>();
        for (int presetNo = 1; presetNo <= 3; presetNo++) {
            JsonNode preset = hyperStat.path("hyper_stat_preset_" + presetNo);
            if (preset.isArray() && !preset.isEmpty()) {
                presets.add(presetNo);
            }
        }
        if (presets.isEmpty()) {
            presets.add(1);
        }
        return presets;
    }

    public List<String> getStatIncreaseEffects(JsonNode hyperStat, int presetNo) {
        List<String> effects = new ArrayList<>();
        for (JsonNode node : hyperStat.path("hyper_stat_preset_" + presetNo)) {
            EffectTextSplitter.addSplit(effects, Jsons.text(node, "stat_increase"));
        }
        return effects;
    }

    public int remainPoint(JsonNode hyperStat, int presetNo) {
        return hyperStat.path("hyper_stat_preset_" + presetNo + "_remain_point").asInt(Integer.MAX_VALUE);
    }

    public int scorePreset(JsonNode hyperStat, int presetNo) {
        int score = 0;
        boolean investedIgnoreDefense = false;

        for (JsonNode node : hyperStat.path("hyper_stat_preset_" + presetNo)) {
            String type = Jsons.text(node, "stat_type");
            int level = node.path("stat_level").asInt(0);

            if ("방어율 무시".equals(type) && level > 0) {
                investedIgnoreDefense = true;
                score += 80;
            }
            if ("보스 몬스터 공격 시 데미지 증가".equals(type)) {
                score += level * 8;
            }
            if ("크리티컬 데미지".equals(type)) {
                score += level * 7;
            }
            if ("데미지".equals(type)) {
                score += level * 5;
            }
            if ("공격력/마력".equals(type)) {
                score += level * 4;
            }
            if ("STR".equals(type) || "DEX".equals(type) || "INT".equals(type) || "LUK".equals(type)) {
                score += level * 3;
            }
            if ("획득 경험치".equals(type) || "일반 몬스터 공격 시 데미지 증가".equals(type) || "아케인포스".equals(type)) {
                score -= level * 10;
            }
        }

        if (!investedIgnoreDefense) {
            score -= 100;
        }
        score -= Math.min(remainPoint(hyperStat, presetNo), 300);
        return score;
    }
}
