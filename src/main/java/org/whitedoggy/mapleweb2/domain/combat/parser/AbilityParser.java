package org.whitedoggy.mapleweb2.domain.combat.parser;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class AbilityParser {
    public Integer getCurrentPresetAbility(JsonNode ability) {
        return Jsons.optionalInt(ability, "preset_no").orElse(1);
    }

    public List<String> getCurrentAbilityByPreset(JsonNode ability, Integer presetNo) {
        List<String> options = new ArrayList<>();
        JsonNode preset = ability.path("ability_preset_" + normalizePresetNo(presetNo)).path("ability_info");
        for (JsonNode info : preset) {
            EffectTextSplitter.addSplit(options, Jsons.text(info, "ability_value"));
        }
        return options;
    }

    public List<Integer> availablePresets(JsonNode ability) {
        List<Integer> presets = new ArrayList<>();
        for (int presetNo = 1; presetNo <= 3; presetNo++) {
            if (!getCurrentAbilityByPreset(ability, presetNo).isEmpty()) {
                presets.add(presetNo);
            }
        }
        if (presets.isEmpty()) {
            presets.add(getCurrentPresetAbility(ability));
        }
        return presets;
    }

    public int scorePreset(JsonNode ability, int presetNo, String characterClass) {
        int score = 0;

        for (String option : getCurrentAbilityByPreset(ability, presetNo)) {
            if (option.contains("보스 몬스터 공격 시 데미지")) {
                score += 100 + Jsons.parseInt(option);
            } else if (option.contains("공격력")) {
                score += 60 + Jsons.parseInt(option);
            } else if (option.contains("마력")) {
                score += 60 + Jsons.parseInt(option);
            } else if (option.contains("크리티컬 확률")) {
                score += characterClass.contains("궁수") ? Jsons.parseInt(option) : -30;
            } else if (option.contains("메소") || option.contains("드롭") || option.contains("경험치")) {
                score -= 50;
            } else {
                score += 5;
            }
        }

        return score;
    }

    private int normalizePresetNo(Integer presetNo) {
        if (presetNo == null || presetNo < 1 || presetNo > 3) {
            return 1;
        }
        return presetNo;
    }
}
