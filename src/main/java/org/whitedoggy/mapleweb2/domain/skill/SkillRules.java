package org.whitedoggy.mapleweb2.domain.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 전투력에 반영할 0차 스킬 목록. 실제 값은 {@code event-buffs.yml}에 있다.
 *
 * <p>이벤트마다 버프 이름이 바뀌므로 코드가 아니라 설정에서 관리한다.
 * 수치는 여기 두지 않는다 — 같은 버프라도 캐릭터마다 값이 달라서
 * API의 {@code skill_effect} 텍스트를 파싱해야 한다.
 */
@ConfigurationProperties(prefix = "maple.skill")
public record SkillRules(
        List<String> directSkillNames,
        Map<String, List<String>> fixedEffectSkills,
        List<String> blessingSkillNames,
        List<String> lucidTransformSkillNames
) {
    public SkillRules {
        fixedEffectSkills = fixedEffectSkills == null ? Map.of() : Map.copyOf(fixedEffectSkills);
        directSkillNames = required(directSkillNames, "direct-skill-names");
        blessingSkillNames = required(blessingSkillNames, "blessing-skill-names");
        lucidTransformSkillNames = required(lucidTransformSkillNames, "lucid-transform-skill-names");
    }

    public Set<String> directSkills() {
        return Set.copyOf(directSkillNames);
    }

    /** 효과 문구가 비어서 우리가 수치를 들고 있어야 하는 스킬. 없으면 빈 목록. */
    public List<String> fixedEffectsOf(String skillName) {
        return fixedEffectSkills.getOrDefault(skillName, List.of());
    }

    public Set<String> blessingSkills() {
        return Set.copyOf(blessingSkillNames);
    }

    public Set<String> lucidTransformSkills() {
        return Set.copyOf(lucidTransformSkillNames);
    }

    private static List<String> required(List<String> values, String property) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(
                    "maple.skill." + property + " 설정이 비어 있습니다. event-buffs.yml을 확인하세요.");
        }
        return List.copyOf(values);
    }
}
