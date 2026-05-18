package org.whitedoggy.mapleweb2.domain.skill;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SkillParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern PET_SET_SKILL_PATTERN = Pattern.compile("Lv\\.[123]$");
    private static final Set<String> DIRECT_SKILL_NAMES = Set.of(
            "파괴의 얄다바오트",
            "초월 : 결전의 의지",
            "메이플 스위츠"
    );

    public List<String> getCombatRelevantSkillEffects(JsonNode skill0) {
        List<String> effects = new ArrayList<>();
        double bestBlessing = 0;

        for (JsonNode skill : skill0.path("character_skill")) {
            String name = skill.path("skill_name").asText("");
            String effect = skill.path("skill_effect").asText("");

            if (isBlessingSkill(name)) {
                bestBlessing = Math.max(bestBlessing, extractLastNumber(effect));
                continue;
            }
            if (!isCombatRelevantSkill(name, effect)) {
                continue;
            }

            EffectTextSplitter.addSplit(effects, effect);
        }

        if (bestBlessing > 0) {
            int value = (int) Math.floor(bestBlessing);
            effects.add("공격력 " + value);
            effects.add("마력 " + value);
        }

        return effects;
    }

    private boolean isBlessingSkill(String name) {
        return "정령의 축복".equals(name) || "여제의 축복".equals(name);
    }

    private boolean isCombatRelevantSkill(String name, String effect) {
        if (DIRECT_SKILL_NAMES.contains(name)) {
            return true;
        }
        return PET_SET_SKILL_PATTERN.matcher(name).find() && containsCombatStat(effect);
    }

    private boolean containsCombatStat(String effect) {
        return effect.contains("공격력")
                || effect.contains("마력")
                || effect.contains("올스탯")
                || effect.contains("보스 몬스터 공격 시 데미지")
                || effect.contains("크리티컬 데미지")
                || effect.contains("최종 데미지")
                || effect.contains("데미지");
    }

    private double extractLastNumber(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        double value = 0;
        while (matcher.find()) {
            value = Double.parseDouble(matcher.group(1));
        }
        return value;
    }
}
