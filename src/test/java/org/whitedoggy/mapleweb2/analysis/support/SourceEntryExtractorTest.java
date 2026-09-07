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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
                {"union_artifact_effect":[{"name":"올스탯 150 증가","level":10}]}"""));
        docs.put(NexonEndpoint.HEXA_MATRIX_STAT, json("""
                {"character_hexa_stat_core":[{"main_stat_name":"주력 스탯 증가","sub_stat_name_1":"공격력 증가",
                  "sub_stat_name_2":"크리티컬 데미지 증가","main_stat_level":5,"sub_stat_level_1":8,"sub_stat_level_2":7}]}"""));
        docs.put(NexonEndpoint.SKILL_0, json("""
                {"character_skill":[{"skill_name":"달팽이 세마리","skill_level":2,"skill_effect":"MP 5 소비하여 데미지 25"},
                                    {"skill_name":"Lv.2 궁디팡팡 멍뭉이","skill_level":1,"skill_effect":"공격력 20, 마력 20증가","skill_icon":"https://x/skill.png"}]}"""));

        Map<String, Map<String, SourceEntry>> entries = extractor.extract(
                docs, MAPPER.createArrayNode(), new PresetSelection(1, 3, 2, 1), "나이트로드", "스카니아");

        assertThat(entries.get("symbol")).containsEntry("아케인심볼 : 소멸의 여로", new SourceEntry("Lv.20", "https://x/sym.png"));
        assertThat(entries.get("hyperStat")).containsOnly(Map.entry("보스 몬스터 공격 시 데미지 증가", SourceEntry.of("Lv.12")));
        assertThat(entries.get("ability")).containsEntry("어빌리티 1", SourceEntry.of("보스 몬스터 공격 시 데미지 20% 증가"));
        assertThat(entries.get("unionRaider")).containsEntry("LUK 증가", SourceEntry.of("100"));
        assertThat(entries.get("unionOccupied")).containsEntry("보스 몬스터 공격 시 데미지 증가", SourceEntry.of("5.00%"));
        assertThat(entries.get("unionArtifact")).containsEntry("올스탯 증가", SourceEntry.of("150 (Lv.10)"));
        assertThat(entries.get("hexaStat")).containsEntry("코어1 주 주력 스탯 증가", SourceEntry.of("Lv.5"));
        // 전투력에 안 들어가는 달팽이 세마리는 빠지고, 펫 버프 모양의 스킬만 남는다.
        // 값은 설명문이 아니라 파서가 스탯으로 바꾼 결과다.
        assertThat(entries.get("skill")).containsOnly(
                Map.entry("Lv.2 궁디팡팡 멍뭉이", new SourceEntry("공격력 +20 · 마력 +20", "https://x/skill.png")));
    }

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }
}
