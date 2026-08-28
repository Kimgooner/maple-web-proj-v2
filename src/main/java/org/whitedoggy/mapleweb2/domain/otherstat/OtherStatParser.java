package org.whitedoggy.mapleweb2.domain.otherstat;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 기타 능력치 영향 요소(`character/other-stat`) 파서.
 *
 * <p>실측으로 확인된 항목:
 * <ul>
 *   <li><b>[챌린저스] 의문의 결계</b> — 챌린저스 월드 전용. 주/부스탯·공격력(마력)과
 *       주스탯 % 를 준다. 챌린저스는 유니온이 없는 대신 이쪽으로 들어온다.</li>
 *   <li><b>[마스터라벨 플러스] 전투 플러스</b> — 월드와 무관. 공/마 60, 올스탯 140, HP/MP 7000.</li>
 *   <li><b>[마스터라벨 플러스] 성장 플러스</b> — 경험치뿐이라 전투력과 무관(파서가 걸러낸다).</li>
 *   <li><b>[제네시스 패스] 대적자의 힘</b> — 공/마 20·올스탯 20·보스뎀 10%를 주지만
 *       <b>전투력에는 반영되지 않는다.</b> 이걸 세면 보유자만 그만큼 과대로 나온다.</li>
 * </ul>
 *
 * <p>{@code stat_name}이 {@code "INT (+%)"} 형태면 퍼센트 옵션이다.
 */
@Component
public class OtherStatParser {

    private static final String PERCENT_SUFFIX = "(+%)";

    /** 전투력에 반영되지 않는 그룹. {@code other_stat_type}이 이것으로 시작하면 통째로 건너뛴다. */
    private static final String COMBAT_POWER_EXCLUDED_GROUP = "[제네시스 패스]";

    public List<String> getOtherStatEffects(JsonNode otherStat) {
        List<String> effects = new ArrayList<>();
        for (JsonNode group : Jsons.array(otherStat, "other_stat")) {
            if (Jsons.text(group, "other_stat_type").startsWith(COMBAT_POWER_EXCLUDED_GROUP)) {
                continue;
            }
            for (JsonNode info : Jsons.array(group, "stat_info")) {
                String effect = toEffect(info);
                if (!effect.isBlank()) {
                    effects.add(effect);
                }
            }
        }
        return effects;
    }

    private String toEffect(JsonNode info) {
        String name = Jsons.text(info, "stat_name").trim();
        String value = Jsons.text(info, "stat_value").trim();
        if (name.isEmpty() || value.isEmpty()) {
            return "";
        }
        if (name.endsWith(PERCENT_SUFFIX)) {
            String statName = name.substring(0, name.length() - PERCENT_SUFFIX.length()).trim();
            return statName + " " + value + "%";
        }
        return name + " " + value;
    }
}
