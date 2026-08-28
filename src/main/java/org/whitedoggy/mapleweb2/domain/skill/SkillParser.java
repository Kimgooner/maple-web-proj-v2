package org.whitedoggy.mapleweb2.domain.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SkillParser {
    private final SkillRules rules;
    private final ChallengersBuffs challengersBuffs;
    private final PetBuffSkills petBuffSkills;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)");
    // 펫 세트 스킬은 "아이스 스노우 Lv.1"처럼 뒤에 붙기도 하고
    // "Lv.1 궁디팡팡 멍뭉이"처럼 앞에 붙기도 한다. 끝에만 맞추면 후자를 놓친다.
    private static final Pattern PET_SET_SKILL_PATTERN = Pattern.compile("Lv\\.[123](?:\\s|$)");

    /**
     * @param worldName 캐릭터의 월드. 챌린저스 계열이면 월드 전용 버프를 함께 반영한다.
     */
    public SkillParseResult getCombatRelevantSkillEffects(JsonNode skill0, String worldName) {
        boolean challengersWorld = challengersBuffs.appliesTo(worldName);
        List<String> effects = new ArrayList<>();
        double bestBlessing = 0;
        boolean lucidTransformSuspected = false;

        for (JsonNode skill : skill0.path("character_skill")) {
            String name = skill.path("skill_name").asText("");
            String effect = skill.path("skill_effect").asText("");
            int level = skill.path("skill_level").asInt(-1);

            if (isLucidTransformSuspiciousSkill(name, level)) {
                lucidTransformSuspected = true;
            }

            if (challengersWorld) {
                List<String> tierEffects = challengersBuffs.effectsOf(name);
                if (!tierEffects.isEmpty()) {
                    effects.addAll(tierEffects);
                    continue;
                }
            }
            // 효과 문구가 빈 스킬(버닝 BEYOND 등)은 표에서 수치를 가져온다.
            List<String> fixed = rules.fixedEffectsOf(name);
            if (!fixed.isEmpty()) {
                effects.addAll(fixed);
                continue;
            }
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

        return new SkillParseResult(effects, lucidTransformSuspected);
    }

    private boolean isLucidTransformSuspiciousSkill(String name, int level) {
        if (level != 0) {
            return false;
        }
        return rules.lucidTransformSkills().contains(name);
    }

    private boolean isBlessingSkill(String name) {
        return rules.blessingSkills().contains(name);
    }

    private boolean isCombatRelevantSkill(String name, String effect) {
        if (rules.directSkills().contains(name)) {
            return true;
        }
        // 단일 펫 고유 버프는 단계가 없어 이름에 Lv.N 이 없다. 이름으로 등록해 둔 것만 반영한다.
        if (petBuffSkills.names().contains(name)) {
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
