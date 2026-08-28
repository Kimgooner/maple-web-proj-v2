package org.whitedoggy.mapleweb2.domain.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * 단계가 없는 단일 펫 고유 버프 스킬 이름. 실제 목록은 {@code pet-buff-skills.yml}에 있다.
 *
 * <p>펫 세트 버프는 멀티펫 종류 수에 따라 3단계로 갈리며 이름에 {@code Lv.1~3}이 붙기 때문에
 * {@link SkillParser}의 패턴이 자동으로 잡는다. 반면 단일 펫이 주는 고유 버프는 단계가 없어
 * 이름에 아무 표식이 없고, 그래서 이름을 직접 등록해야 한다.
 */
@ConfigurationProperties(prefix = "maple.pet")
public record PetBuffSkills(List<String> buffSkillNames) {

    public PetBuffSkills {
        buffSkillNames = buffSkillNames == null ? List.of() : List.copyOf(buffSkillNames);
    }

    public Set<String> names() {
        return Set.copyOf(buffSkillNames);
    }
}
