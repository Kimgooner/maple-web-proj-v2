package org.whitedoggy.mapleweb2.domain.item.parser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-09-17 업데이트로 무기 소울에 상시 공격력(soul_pad)과 소울 잠재가 생겼다.
 * 09-17 표본 472명: 이 둘을 넣기 전 정수 일치 13명 → 넣은 뒤 427명(+유니온 결손 20명).
 */
@SpringBootTest
class ItemParserSoulTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private ItemParser itemParser;

    private static JsonNode weapon(String soulFields) {
        return MAPPER.readTree("""
                {"item_equipment_part":"활","item_equipment_slot":"무기","item_name":"아케인셰이드 보우",
                 "item_icon":"","starforce":"22","potential_option_grade":"레전드리",
                 "additional_potential_option_grade":"유니크",
                 "item_total_option":{"str":"0","dex":"0","int":"0","luk":"0","max_hp":"0","max_mp":"0",
                   "attack_power":"500","magic_power":"0","armor":"0","speed":"0","jump":"0","boss_damage":"30",
                   "ignore_monster_armor":"20","all_stat":"0","damage":"0","equipment_level_decrease":0,
                   "max_hp_rate":"0","max_mp_rate":"0"},
                 "item_base_option":{"attack_power":"276","base_equipment_level":200},
                 "item_add_option":{"attack_power":"170","magic_power":"0"},
                 "item_etc_option":{"attack_power":"72","magic_power":"0"},
                 "item_starforce_option":{"attack_power":"200"},
                 "item_exceptional_option":{},
                 "potential_option_1":"공격력 +12%","potential_option_2":null,"potential_option_3":null,
                 "additional_potential_option_1":null,"additional_potential_option_2":null,"additional_potential_option_3":null,
                 "item_description":null
                 """ + soulFields + "}");
    }

    @Test
    void 소울_상시_공격력과_소울_잠재를_읽는다() {
        ItemSnapShot snapshot = itemParser.getItemSnapShot(weapon("""
                ,"soul_name":"위대한 데미안의 소울 적용","soul_option":"공격력 +3%","soul_pad":"20","soul_mad":"0",
                 "soul_active":"1","soul_potential_grade":"레전드리","soul_potential_amplified_grade":3,
                 "soul_potential_option_1":"공격력 +6%","soul_potential_option_2":"보스 몬스터 데미지 +6%",
                 "soul_potential_option_3":"공격력 +4.5%\""""), "보우마스터", "장비").itemSnapShot();

        // 소울 옵션 3% + 잠재 12% + 소울 잠재 6% + 4.5%
        assertThat(snapshot.getStatSheet().getATTACK_POWER_PERCENT()).isEqualTo(25.5);
        assertThat(snapshot.getStatSheet().getBOSS_DAMAGE()).isEqualTo(36.0);
        assertThat(snapshot.getSoulPotentialGrade()).isEqualTo("레전드리");
        assertThat(snapshot.getSoulPotentialLines())
                .containsExactly("공격력 +6%", "보스 몬스터 데미지 +6%", "공격력 +4.5%");
        // 화면이 잠재/에디/익셉셔널/소울 잠재를 갈라 보여주므로 잠재 줄에는 섞이지 않는다.
        assertThat(snapshot.getPotentialLines()).containsExactly("공격력 +12%");
    }

    /** 업데이트 전 문서(필드 없음)와 소울 없는 무기는 전과 같다. */
    @Test
    void 소울_필드가_없으면_아무것도_더하지_않는다() {
        ItemSnapShot old = itemParser.getItemSnapShot(weapon(""), "보우마스터", "장비").itemSnapShot();
        ItemSnapShot none = itemParser.getItemSnapShot(
                weapon(",\"soul_pad\":\"0\",\"soul_mad\":\"0\""), "보우마스터", "장비").itemSnapShot();

        assertThat(old.getStatSheet().getATTACK_POWER_PERCENT()).isEqualTo(12.0);
        assertThat(none.getStatSheet().getATTACK_POWER()).isEqualTo(old.getStatSheet().getATTACK_POWER());
        assertThat(old.getSoulPotentialLines()).isEmpty();
        assertThat(old.getSoulPotentialGrade()).isNull();
    }

    @Test
    void 소울_상시_공격력은_고정_공격력에_더해진다() {
        ItemSnapShot without = itemParser.getItemSnapShot(weapon(""), "보우마스터", "장비").itemSnapShot();
        ItemSnapShot with = itemParser.getItemSnapShot(
                weapon(",\"soul_option\":\"공격력 +3%\",\"soul_pad\":\"20\",\"soul_mad\":\"0\""),
                "보우마스터", "장비").itemSnapShot();

        assertThat(with.getStatSheet().getATTACK_POWER() - without.getStatSheet().getATTACK_POWER()).isEqualTo(20);
    }
}
