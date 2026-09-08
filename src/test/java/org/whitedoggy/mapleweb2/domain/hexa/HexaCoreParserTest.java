package org.whitedoggy.mapleweb2.domain.hexa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 조각 계산은 화면에 그대로 나가는 숫자다. 표가 흔들리면 모든 캐릭터의 그래프가
 * 같이 움직이므로 누적값을 못박아 둔다.
 */
@SpringBootTest
class HexaCoreParserTest {

    @Autowired
    private HexaCoreParser parser;

    @Autowired
    private HexaCoreCost cost;

    @Autowired
    private ObjectMapper mapper;

    /**
     * 표의 0→30 누적값. MapleStory Wiki 에서 옮기고 나무위키와 대조한 값이다
     * (2026-09-08). 넥슨이 표를 고쳐 yml 을 손대면 여기도 같이 고친다.
     */
    @Test
    void costTablesMatchTheSourceTotals() {
        assertEquals(30, cost.origin().size());
        assertEquals(4400, sum(cost.origin()), "오리진");
        assertEquals(4500, sum(cost.ascent()), "어센트");
        assertEquals(3442, sum(cost.third()), "스킬 3번");
        assertEquals(2252, sum(cost.mastery()), "마스터리");
        assertEquals(3383, sum(cost.boost()), "강화");
        assertEquals(6268, sum(cost.solJanusHecate()), "솔 야누스·헤카테");
        assertEquals(4035, sum(cost.commonBoost()), "5차 공용 강화");
    }

    /** 솔 야누스는 전투에 관여하지 않아 세지 않는다. 자리는 세므로 뒤 코어가 밀리지 않는다. */
    @Test
    void solJanusIsNotCounted() {
        long withJanus = parse("""
                [{"hexa_core_name":"솔 야누스","hexa_core_type":"공용 코어","hexa_core_level":30},
                 {"hexa_core_name":"솔 헤카테","hexa_core_type":"공용 코어","hexa_core_level":30}]""");
        assertEquals(6268, withJanus, "솔 헤카테 몫만 남아야 한다");
    }

    /** 스킬 코어는 순서가 오리진 → 어센트 → 3번이다. */
    @Test
    void skillCoresAreReadInSlotOrder() {
        long all30 = parse("""
                [{"hexa_core_name":"1번","hexa_core_type":"스킬 코어","hexa_core_level":30},
                 {"hexa_core_name":"2번","hexa_core_type":"스킬 코어","hexa_core_level":30},
                 {"hexa_core_name":"3번","hexa_core_type":"스킬 코어","hexa_core_level":30}]""");
        assertEquals(4400 + 4500 + 3442, all30);
    }

    /** 3번을 아직 안 연 캐릭터는 앞의 둘만 온다. */
    @Test
    void twoSkillCoresMeanOriginAndAscent() {
        assertEquals(4400 + 4500, parse("""
                [{"hexa_core_name":"1번","hexa_core_type":"스킬 코어","hexa_core_level":30},
                 {"hexa_core_name":"2번","hexa_core_type":"스킬 코어","hexa_core_level":30}]"""));
    }

    /** 솔 야누스·헤카테가 아닌 공용 코어는 5차 공용 강화표를 쓴다. */
    @Test
    void otherCommonCoresUseTheFifthJobBoostTable() {
        assertEquals(4035, parse("""
                [{"hexa_core_name":"시그너스 팔랑크스 VI","hexa_core_type":"공용 코어","hexa_core_level":30}]"""));
    }

    /** 이벤트로 받은 레벨은 조각이 들지 않았다. */
    @Test
    void eventLevelsCostNothing() {
        assertEquals(0, parse("""
                [{"hexa_core_name":"솔 헤카테","hexa_core_type":"공용 코어",
                  "hexa_core_level":0,"hexa_core_event_level":30}]"""));
    }

    /** 중간 레벨은 표를 앞에서부터 그만큼 더한 값이다. */
    @Test
    void partialLevelsSumTheTablePrefix() {
        // 마스터리 5레벨 = 50 + 15 + 18 + 20 + 23
        assertEquals(126, parse("""
                [{"hexa_core_name":"아무","hexa_core_type":"마스터리 코어","hexa_core_level":5}]"""));
    }

    /** 모르는 종류가 생겨도 터지지 않고 그것만 빼고 센다. */
    @Test
    void unknownCoreTypesAreSkipped() {
        assertEquals(2252, parse("""
                [{"hexa_core_name":"아무","hexa_core_type":"마스터리 코어","hexa_core_level":30},
                 {"hexa_core_name":"새 코어","hexa_core_type":"미래 코어","hexa_core_level":30}]"""));
    }

    /** 문서를 못 받은 것과 "6차인데 아직 안 올렸다"는 다르다. */
    @Test
    void missingDocumentIsNullNotZero() {
        assertNull(parser.solErdaFragments(NullNode.getInstance()));
        assertNull(parser.solErdaFragments(null));
        assertEquals(0, parse("[]"));
    }

    private long parse(String coresJson) {
        Map<String, Object> document = Map.of("character_hexa_core_equipment",
                mapper.readValue(coresJson, java.util.List.class));
        return parser.solErdaFragments(mapper.valueToTree(document));
    }

    private static int sum(java.util.List<Integer> table) {
        return table.stream().mapToInt(Integer::intValue).sum();
    }
}
