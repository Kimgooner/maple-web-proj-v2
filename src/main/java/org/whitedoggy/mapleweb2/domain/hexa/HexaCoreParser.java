package org.whitedoggy.mapleweb2.domain.hexa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * HEXA 코어에 지금까지 들어간 솔 에르다 조각을 센다.
 *
 * <p>헥사 코어 강화는 전투력에 잡히지 않는다. 그래서 전투력만 보면 6차에 얼마를 쏟았는지
 * 드러나지 않아, 대신 <b>누적 조각 사용량</b>을 추이의 두 번째 축으로 쓴다.
 *
 * <p>API의 {@code hexa_core_type}은 네 가지인데 비용표는 일곱이라 갈라야 한다.
 * <ul>
 *   <li>마스터리·강화 코어는 1:1이다.</li>
 *   <li>공용 코어는 이름이 {@code 솔 야누스}·{@code 솔 헤카테}면 그 표를 쓰고,
 *       나머지(5차 공용 스킬 강화)는 따로 있다.</li>
 *   <li>스킬 코어는 <b>배열에 오는 순서</b>가 오리진 → 어센트 → 3번이다.
 *       레벨순이 아니다(실측: 한 캐릭터가 7/12/3). 3번 코어를 아직 안 연 캐릭터는
 *       앞의 둘만 오고, 3번을 연 같은 직업 캐릭터에서 그 코어가 [2]번 자리에 있었다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HexaCoreParser {

    /** 전투에 관여하지 않아 세지 않는 코어. */
    private static final String SOL_JANUS = "솔 야누스";

    /** 자기 비용표를 쓰는 공용 코어. 나머지 공용 코어는 5차 공용 강화다. */
    private static final Set<String> FIXED_COMMON_CORES = Set.of(SOL_JANUS, "솔 헤카테");

    private static final String SKILL_CORE = "스킬 코어";
    private static final String MASTERY_CORE = "마스터리 코어";
    private static final String BOOST_CORE = "강화 코어";
    private static final String COMMON_CORE = "공용 코어";

    private final HexaCoreCost coreCost;

    /**
     * 문서를 못 받았거나 6차 전직 전이면 {@code null}. 0과 구분해야 한다 —
     * 0은 "6차인데 아직 아무것도 안 올렸다"이고, null은 "모른다"다.
     */
    public Long solErdaFragments(JsonNode hexaMatrix) {
        // hexa_core_event_level 은 이벤트로 받은 레벨이라 조각이 들지 않았다.
        return sum(hexaMatrix, (table, core) -> core.path("hexa_core_level").asInt(0));
    }

    /**
     * 지금 가진 코어를 모두 만렙까지 올리는 데 드는 조각. 진행률의 분모다.
     *
     * <p>가지고 있는 코어만 센다. 3번 스킬 코어를 아직 안 연 캐릭터는 그만큼 분모가 작고,
     * 여는 날 분모가 늘어난다. 직업마다 코어 수가 달라 상수 하나로는 둘 수 없다.
     */
    public Long solErdaFragmentsRequired(JsonNode hexaMatrix) {
        return sum(hexaMatrix, (table, core) -> table.size());
    }

    private Long sum(JsonNode hexaMatrix, java.util.function.ToIntBiFunction<List<Integer>, JsonNode> levelOf) {
        if (Jsons.empty(hexaMatrix)) {
            return null;
        }
        JsonNode cores = hexaMatrix.path("character_hexa_core_equipment");
        if (!cores.isArray()) {
            return null;
        }

        long total = 0;
        int skillCoreIndex = 0;
        for (JsonNode core : cores) {
            String type = Jsons.text(core, "hexa_core_type");
            // 순서가 곧 어느 스킬 코어인지라, 빼는 코어가 있어도 자리는 세어 둔다.
            int index = SKILL_CORE.equals(type) ? skillCoreIndex++ : 0;

            if (SOL_JANUS.equals(Jsons.text(core, "hexa_core_name"))) {
                continue;
            }
            List<Integer> table = tableFor(type, Jsons.text(core, "hexa_core_name"), index);
            if (table == null) {
                continue;
            }
            total += HexaCoreCost.cumulative(table, levelOf.applyAsInt(table, core));
        }
        return total;
    }

    /** 모르는 종류면 {@code null}. 새 코어 종류가 생겨도 세지 않고 넘어간다. */
    private List<Integer> tableFor(String type, String name, int skillCoreIndex) {
        return switch (type) {
            case MASTERY_CORE -> coreCost.mastery();
            case BOOST_CORE -> coreCost.boost();
            case COMMON_CORE -> FIXED_COMMON_CORES.contains(name)
                    ? coreCost.solJanusHecate()
                    : coreCost.commonBoost();
            case SKILL_CORE -> switch (skillCoreIndex) {
                case 0 -> coreCost.origin();
                case 1 -> coreCost.ascent();
                default -> coreCost.third();
            };
            default -> null;
        };
    }
}
