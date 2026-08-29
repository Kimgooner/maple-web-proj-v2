package org.whitedoggy.mapleweb2.domain.ability;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AbilityParser {

    /** "AP를 직접 투자한 LUK의 7% 만큼 DEX 증가" — 어빌리티에만 있는 표기다. */
    private static final Pattern AP_CONVERSION = Pattern.compile(
            "^AP를 직접 투자한 (STR|DEX|INT|LUK)의 (\\d+)% 만큼 (STR|DEX|INT|LUK) 증가$");

    /**
     * AP 투자량에 비례하는 어빌리티 줄을 실제 수치로 바꾼다.
     *
     * <p>이 표기는 문구만으로는 값을 알 수 없어 AP 배분량이 필요하다. 나머지 줄은 그대로 둔다.
     * 실측(제논 프뢰벨): AP LUK 851 의 7% = 59.57 → 59 가 DEX 로 붙는다. 내림이다.
     */
    public List<String> resolveApConversions(List<String> options, StatSheet abilityPoint) {
        List<String> resolved = new ArrayList<>();
        for (String option : options) {
            Matcher matcher = AP_CONVERSION.matcher(option.trim());
            if (!matcher.matches()) {
                resolved.add(option);
                continue;
            }
            int amount = investedAp(abilityPoint, matcher.group(1))
                    * Integer.parseInt(matcher.group(2)) / 100;
            if (amount > 0) {
                resolved.add(matcher.group(3) + " " + amount);
            }
        }
        return resolved;
    }

    private int investedAp(StatSheet abilityPoint, String statName) {
        if (abilityPoint == null) {
            return 0;
        }
        return switch (statName) {
            case "STR" -> abilityPoint.getSTR();
            case "DEX" -> abilityPoint.getDEX();
            case "INT" -> abilityPoint.getINT();
            case "LUK" -> abilityPoint.getLUK();
            default -> 0;
        };
    }

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
