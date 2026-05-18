package org.whitedoggy.mapleweb2.domain.union.raider;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class RaiderParser {
    public List<String> getUnionRaiderStat(JsonNode raider) {
        return readTextArray(raider.path("union_raider_stat"));
    }

    public List<String> getUnionOccupiedStat(JsonNode raider) {
        return readTextArray(raider.path("union_occupied_stat"));
    }

    public List<String> getUnionRaiderStatByPreset(JsonNode raider, int presetNo) {
        return readTextArray(raider.path("union_raider_preset_" + presetNo).path("union_raider_stat"));
    }

    public List<String> getUnionOccupiedStatByPreset(JsonNode raider, int presetNo) {
        return readTextArray(raider.path("union_raider_preset_" + presetNo).path("union_occupied_stat"));
    }

    public List<String> getCombatStatEffectsByPreset(JsonNode raider, int presetNo) {
        List<String> effects = new ArrayList<>();
        effects.addAll(getUnionRaiderStatByPreset(raider, presetNo));
        effects.addAll(getUnionOccupiedStatByPreset(raider, presetNo));
        return effects;
    }

    public List<Integer> availablePresets(JsonNode raider) {
        List<Integer> presets = new ArrayList<>();
        for (int presetNo = 1; presetNo <= 5; presetNo++) {
            JsonNode blocks = raider.path("union_raider_preset_" + presetNo).path("union_block");
            if (blocks.isArray() && !blocks.isEmpty()) {
                presets.add(presetNo);
            }
        }
        if (presets.isEmpty()) {
            presets.add(1);
        }
        return presets;
    }

    public int scorePreset(JsonNode raider, int presetNo) {
        JsonNode preset = raider.path("union_raider_preset_" + presetNo);
        int score = preset.path("union_block").size() * 10;

        for (String stat : getUnionOccupiedStatByPreset(raider, presetNo)) {
            if (stat.contains("크리티컬 확률")) {
                score += 30;
            }
            if (stat.contains("크리티컬 데미지")) {
                score += 35;
            }
            if (stat.contains("보스 몬스터 공격 시 데미지")) {
                score += 40;
            }
            if (stat.contains("방어율 무시")) {
                score += 30;
            }
            if (stat.contains("공격력")) {
                score += 20;
            }
        }
        return score;
    }

    private List<String> readTextArray(JsonNode array) {
        List<String> list = new ArrayList<>();
        for (JsonNode node : array) {
            EffectTextSplitter.addSplit(list, node.asText());
        }
        return list;
    }
}
