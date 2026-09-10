package org.whitedoggy.mapleweb2.analysis.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

@SpringBootTest
class SourceEntryExtractorTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    SourceEntryExtractor extractor;

    @Test
    void namedEntriesFollowTheChosenPresets() {
        Map<NexonEndpoint, JsonNode> docs = new EnumMap<>(NexonEndpoint.class);
        docs.put(NexonEndpoint.SYMBOL_EQUIPMENT, json("""
                {"symbol":[{"symbol_name":"아케인심볼 : 소멸의 여로","symbol_level":20,"symbol_icon":"https://x/sym.png"}]}"""));
        docs.put(NexonEndpoint.HYPER_STAT, json("""
                {"hyper_stat_preset_1":[{"stat_type":"STR","stat_level":0}],
                 "hyper_stat_preset_2":[{"stat_type":"보스 몬스터 공격 시 데미지 증가","stat_level":12},
                                        {"stat_type":"STR","stat_level":0}]}"""));
        docs.put(NexonEndpoint.ABILITY, json("""
                {"ability_preset_3":{"ability_info":[{"ability_no":"1","ability_value":"보스 몬스터 공격 시 데미지 20% 증가"}]}}"""));
        docs.put(NexonEndpoint.UNION_RAIDER, json("""
                {"union_raider_stat":["LUK 100 증가"],"union_state_stat":["보스 몬스터 공격 시 데미지 5.00% 증가"],
                 "use_preset_no":1}"""));
        docs.put(NexonEndpoint.UNION_ARTIFACT, json("""
                {"union_artifact_effect":[{"name":"올스탯 150 증가","level":10}],
                 "union_artifact_crystal":[{"date_expire":"2026-09-01T00:00+09:00"},
                                           {"validity_flag":"1"},
                                           {"date_expire":"2027-01-01T00:00+09:00"}]}"""));
        docs.put(NexonEndpoint.HEXA_MATRIX_STAT, json("""
                {"character_hexa_stat_core":[{"main_stat_name":"주력 스탯 증가","sub_stat_name_1":"공격력 증가",
                  "sub_stat_name_2":"크리티컬 데미지 증가","main_stat_level":5,"sub_stat_level_1":8,"sub_stat_level_2":7}]}"""));
        docs.put(NexonEndpoint.SKILL_0, json("""
                {"character_skill":[{"skill_name":"달팽이 세마리","skill_level":2,"skill_effect":"MP 5 소비하여 데미지 25"},
                                    {"skill_name":"Lv.2 궁디팡팡 멍뭉이","skill_level":1,"skill_effect":"공격력 20, 마력 20증가","skill_icon":"https://x/skill.png"}]}"""));

        Map<String, Map<String, SourceEntry>> entries = extractor.extract(
                docs, MAPPER.createArrayNode(), new PresetSelection(1, 3, 2, 1), "나이트로드", "스카니아",
                LocalDate.of(2026, 9, 9));

        assertThat(entries.get("symbol")).containsEntry("아케인심볼 : 소멸의 여로", new SourceEntry("Lv.20", "https://x/sym.png"));
        assertThat(entries.get("hyperStat")).containsOnly(Map.entry("보스 몬스터 공격 시 데미지 증가", SourceEntry.of("Lv.12")));
        assertThat(entries.get("ability")).containsEntry("어빌리티 1", SourceEntry.of("보스 몬스터 공격 시 데미지 20% 증가"));
        assertThat(entries.get("unionRaider")).containsEntry("LUK 증가", SourceEntry.of("100"));
        assertThat(entries.get("unionOccupied")).containsEntry("보스 몬스터 공격 시 데미지 증가", SourceEntry.of("5.00%"));
        assertThat(entries.get("unionArtifact")).containsEntry("올스탯 증가", SourceEntry.of("150 (Lv.10)"));
        // 만료된 크리스탈은 효과 목록에 아무 흔적이 없다. 세어 두지 않으면 전투력이
        // 왜 줄었는지 화면에서 답할 자리가 없다.
        assertThat(entries.get("unionArtifact"))
                .containsEntry("만료된 크리스탈", new SourceEntry("2개", null, null, "만료"));
        // 코어 하나가 한 항목이다. 셋을 따로 두면 코어를 갈아 끼웠을 때 세 줄이 각각 바뀐 것처럼 나온다.
        assertThat(entries.get("hexaStat")).containsEntry("헥사 스탯I", SourceEntry.of("""
                주옵션 주력 스탯 증가 Lv.5
                부옵션 공격력 증가 Lv.8
                부옵션 크리티컬 데미지 증가 Lv.7"""));
        // 전투력에 안 들어가는 달팽이 세마리는 빠지고, 펫 버프 모양의 스킬만 남는다.
        // 값은 설명문이 아니라 파서가 스탯으로 바꾼 결과다.
        assertThat(entries.get("skill")).containsOnly(
                Map.entry("Lv.2 궁디팡팡 멍뭉이", new SourceEntry("공격력 +20 · 마력 +20", "https://x/skill.png")));
    }

    /**
     * 유니온 공격대는 같은 효과를 여러 줄로 준다. 늘어놓지 않고 합쳐야 읽을 수 있다.
     * (실측: 한 캐릭터의 union_raider_stat 에 "INT 100 증가"가 셋, "INT 80 증가"가 하나)
     */
    @Test
    void sameUnionEffectIsSummed() {
        assertThat(SourceEntryExtractor.lines(List.of(
                "INT 100 증가", "INT 100 증가", "INT 100 증가", "INT 80 증가")))
                .containsExactly(Map.entry("INT 증가", SourceEntry.of("380")));
    }

    @Test
    void percentEffectsKeepTheirSign() {
        assertThat(SourceEntryExtractor.lines(List.of("크리티컬 데미지 6% 증가", "크리티컬 데미지 3.5% 증가")))
                .containsExactly(Map.entry("크리티컬 데미지 증가", SourceEntry.of("9.5%")));
    }

    /** 한 줄에 숫자가 둘이면 무엇을 더할지 알 수 없다. 그대로 늘어놓는다. */
    @Test
    void multiNumberLinesAreListedAsIs() {
        assertThat(SourceEntryExtractor.lines(List.of(
                "공격 시 20%의 확률로 데미지 20% 증가", "공격 시 10%의 확률로 데미지 30% 증가")))
                .containsExactly(Map.entry("공격 시 의 확률로 데미지 증가", SourceEntry.of("20% 20%, 10% 30%")));
    }

    /**
     * %와 고정값은 숫자를 지우면 이름이 같아지지만 더하면 거짓말이 된다. 따로 더해 나눠 적는다.
     * (실측: "최대 HP 5% 증가" 하나에 "최대 HP 2000 증가"가 둘 오는 캐릭터가 있다)
     */
    @Test
    void percentAndFlatAreSummedSeparately() {
        assertThat(SourceEntryExtractor.lines(List.of("최대 HP 5% 증가", "최대 HP 2000 증가", "최대 HP 2000 증가")))
                .containsExactly(Map.entry("최대 HP 증가", SourceEntry.of("5%, 4000")));
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }
}
