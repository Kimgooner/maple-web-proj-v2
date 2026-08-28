package org.whitedoggy.mapleweb2.domain.union.artifact;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 유니온 아티팩트 효과 계수. 실제 값은 {@code game-data.yml}의 {@code maple.game.artifact}에 있다.
 *
 * <p>효과 레벨은 같은 옵션을 가진 크리스탈들의 레벨 합이고 {@code maxEffectLevel}에서 멈춘다.
 * 상한을 무시하면 크리스탈을 많이 낀 캐릭터에서 과대계산된다.
 */
@ConfigurationProperties(prefix = "maple.game.artifact")
public record ArtifactData(
        int maxEffectLevel,
        Map<String, Option> perLevel
) {
    /** @param text 효과 문구, @param value 레벨 1당 수치, @param percent 퍼센트 옵션인가 */
    public record Option(String text, double value, boolean percent) {
    }

    public ArtifactData {
        perLevel = perLevel == null ? Map.of() : Map.copyOf(perLevel);
    }

    public Option optionOf(String crystalOptionName) {
        return perLevel.get(crystalOptionName);
    }

    public int capLevel(int level) {
        return Math.min(level, maxEffectLevel);
    }
}
