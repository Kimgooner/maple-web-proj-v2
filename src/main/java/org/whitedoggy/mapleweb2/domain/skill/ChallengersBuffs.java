package org.whitedoggy.mapleweb2.domain.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 챌린저스 월드 전용 버프. 실제 값은 {@code challengers-buffs.yml}에 있다.
 *
 * <p>챌린저스 월드는 유니온이 없는 대신 월드 전용 패시브가 붙는다. 티어가 스킬 이름에 드러나지만
 * ({@code 챌린저스 비기너} … 사파이어 이상은 {@code 챌린저스}) API의 {@code skill_effect}는
 * 티어와 무관하게 항상 비어 있어 수치를 알 수 없다. 그래서 공식 표를 설정으로 들고 있는다.
 *
 * <p>같은 이름의 스킬이 일반 월드에 존재할 가능성을 배제할 수 없으므로,
 * 캐릭터의 {@code world_name}이 챌린저스 계열일 때만 적용한다.
 */
@ConfigurationProperties(prefix = "maple.challengers")
public record ChallengersBuffs(
        String worldPrefix,
        Map<String, List<String>> tierEffects
) {
    public ChallengersBuffs {
        tierEffects = tierEffects == null ? Map.of() : Map.copyOf(tierEffects);
    }

    /** 챌린저스 계열 월드인가. 챌린저스 / 챌린저스2 / 챌린저스3 … */
    public boolean appliesTo(String worldName) {
        return worldPrefix != null && !worldPrefix.isBlank()
                && worldName != null && worldName.startsWith(worldPrefix);
    }

    /** 스킬 이름(=티어)에 해당하는 효과. 없으면 빈 목록. */
    public List<String> effectsOf(String skillName) {
        return tierEffects.getOrDefault(skillName, List.of());
    }
}
