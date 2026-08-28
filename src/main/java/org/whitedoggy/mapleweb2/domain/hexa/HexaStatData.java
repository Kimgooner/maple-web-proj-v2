package org.whitedoggy.mapleweb2.domain.hexa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 헥사 스탯의 레벨별 수치. 실제 값은 {@code game-data.yml}의 {@code maple.game.hexa-stat}에 있다.
 *
 * <p>메인 코어와 서브 코어가 같은 이름이라도 수치가 다르다.
 */
@ConfigurationProperties(prefix = "maple.game.hexa-stat")
public record HexaStatData(
        Map<String, List<Double>> main,
        Map<String, List<Double>> sub
) {
    public HexaStatData {
        main = main == null ? Map.of() : Map.copyOf(main);
        sub = sub == null ? Map.of() : Map.copyOf(sub);
    }

    public boolean hasMain(String statName) {
        return main.containsKey(statName);
    }

    public boolean hasSub(String statName) {
        return sub.containsKey(statName);
    }

    /** 레벨(1부터)에 해당하는 수치. */
    public double mainValue(String statName, int level) {
        return valueOf(main, statName, level);
    }

    public double subValue(String statName, int level) {
        return valueOf(sub, statName, level);
    }

    private double valueOf(Map<String, List<Double>> table, String statName, int level) {
        List<Double> values = table.get(statName);
        if (values == null || level < 1 || level > values.size()) {
            throw new IllegalStateException(
                    "game-data.yml의 maple.game.hexa-stat에 없는 조합입니다: " + statName + " lv" + level);
        }
        return values.get(level - 1);
    }
}
