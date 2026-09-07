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

    private static final Pattern NUMBER_PATTERN = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)");
    // 펫 세트 스킬은 "아이스 스노우 Lv.1"처럼 뒤에 붙기도 하고
    // "Lv.1 궁디팡팡 멍뭉이"처럼 앞에 붙기도 한다. 끝에만 맞추면 후자를 놓친다.
    //
    // 점은 없을 수도 있다 — "눈여우는 뽀송뽀송 Lv1"은 넥슨이 점 없이 준다. 점을
    // 필수로 두면 이 세트만 통째로 빠진다. 표본 4,541명에서 점 없는 Lv 로 새로
    // 걸리는 스킬은 눈여우 3단계뿐이라 다른 스킬을 잘못 끌어오지 않는다.
    private static final Pattern PET_SET_SKILL_PATTERN = Pattern.compile("Lv\\.?[123](?:\\s|$)");

    // 펫 버프는 이름이 아니라 효과 문구의 모양으로 잡는다. 세트 버프든 단일 펫 고유
    // 버프든 skill_effect 가 "공격력 N, 마력 N증가" 한 줄뿐이고, 다른 절이 붙지 않는다.
    //
    // 이름으로 잡으려 하면 두 번 진다. 옛 펫은 Lv.N 이 안 붙어 이름을 하나씩 등록해야
    // 하고(데블 펫의 버프·쁘띠 랑의 가호·메이플M 팬텀이 그렇게 빠져 있었다), 새 펫은
    // 이벤트마다 이름이 바뀐다. 모양으로 잡으면 등록 자체가 필요 없다.
    //
    // 표본 9,997명 검증: 이 모양인 스킬 264종 중 256종이 Lv.N 이름이라 종전 패턴과
    // 정확히 겹치고, 나머지 8종은 축복 2종(위에서 먼저 처리)·루나 파워업(direct 등록)·
    // 단일 펫 5종이다. 공격력과 마력 값이 다른 경우는 0종이라 역참조로 묶어 둔다.
    private static final Pattern PET_BUFF_EFFECT_PATTERN =
            Pattern.compile("\\s*공격력\\s*(\\d+)\\s*,\\s*마력\\s*\\1\\s*증가\\s*");

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

    /**
     * 스킬 하나가 계산에 넣는 효과 문구 목록. {@link #getCombatRelevantSkillEffects} 가 스킬마다 하는
     * 일과 같지만, 축복은 여러 스킬 중 최댓값만 반영하는 규칙을 여기서는 적용하지 않고 그 스킬의
     * 값을 그대로 준다. 계산에 들어가지 않는 스킬은 빈 목록.
     */
    public List<String> parsedEffectsOf(String name, String effect, String worldName) {
        if (challengersBuffs.appliesTo(worldName)) {
            List<String> tierEffects = challengersBuffs.effectsOf(name);
            if (!tierEffects.isEmpty()) {
                return tierEffects;
            }
        }
        List<String> fixed = rules.fixedEffectsOf(name);
        if (!fixed.isEmpty()) {
            return fixed;
        }
        if (isBlessingSkill(name)) {
            int value = (int) Math.floor(extractLastNumber(effect));
            return value > 0 ? List.of("공격력 " + value, "마력 " + value) : List.of();
        }
        if (!isCombatRelevantSkill(name, effect)) {
            return List.of();
        }
        return EffectTextSplitter.split(effect);
    }

    /**
     * 이 스킬이 전투력 계산에 들어가는가. {@link #getCombatRelevantSkillEffects} 와 같은 기준이다.
     * 항목 이름을 뽑는 쪽({@code SourceEntryExtractor})이 같은 스킬 집합을 보게 하려고 공개한다.
     */
    public boolean isCombatRelevant(String name, String effect, String worldName) {
        if (challengersBuffs.appliesTo(worldName) && !challengersBuffs.effectsOf(name).isEmpty()) {
            return true;
        }
        if (!rules.fixedEffectsOf(name).isEmpty()) {
            return true;
        }
        return isBlessingSkill(name) || isCombatRelevantSkill(name, effect);
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
        if (PET_BUFF_EFFECT_PATTERN.matcher(effect).matches()) {
            return true;
        }
        // 모양 규칙이 지금 표본에서는 아래 패턴을 완전히 포함하지만, 문구가 조금 다른
        // 펫 세트 스킬이 있을 수 있어 종전 이름 패턴도 남겨 둔다.
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
