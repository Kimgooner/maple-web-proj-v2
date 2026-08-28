package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 세트 개수를 우리가 직접 센 값과 API가 준 {@code total_set_count}를 대조한다.
 *
 * <p>세트 효과는 기여가 크고(공격력 100~400), 우리는 프리셋 장비로 개수를 다시 센다
 * (별칭 매칭·럭키 아이템 보정·제로 루트 어비스 휴리스틱). 한 개 차이로 효과 단계가
 * 통째로 달라지므로 검증이 필요하다.
 *
 * <p>API의 {@code total_set_count}는 <b>현재 착용</b> 기준이라 프리셋이 다르면 당연히 다르다.
 * 그래서 골든과 같은 current 프리셋으로 계산해 비교한다.
 *
 * <p>결과: {@code build/reports/golden/set-count-audit.tsv}
 */
@SpringBootTest
class SetCountAuditTest {

    private static final Path REPORT = Path.of("build/reports/golden/set-count-audit.tsv");

    @Autowired private SetEffectParser setEffectParser;
    @Autowired private ItemEquipmentParser itemEquipmentParser;
    @Autowired private BasicParser basicParser;
    @Autowired private ObjectMapper mapper;

    @Test
    void auditSetCounts() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append(String.join("\t", "job", "name", "set", "ours", "api", "diff")).append('\n');

        FixtureLoader.forEach(mapper, fixture -> {
            var snapshot = fixture.snapshot();
            JsonNode itemEquip = snapshot.document(NexonEndpoint.ITEM_EQUIPMENT);
            JsonNode setEffect = snapshot.document(NexonEndpoint.SET_EFFECT);
            String job = basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC));

            int preset = itemEquip.path("preset_no").asInt(1);
            JsonNode presetItems = itemEquipmentParser.getItemEquipmentByPreset(itemEquip, preset);

            Map<String, Integer> ours = setEffectParser.getAppliedSetCounts(setEffect, presetItems, job);
            Map<String, Integer> api = new LinkedHashMap<>();
            for (JsonNode s : setEffect.path("set_effect")) {
                api.put(Jsons.text(s, "set_name"), s.path("total_set_count").asInt(0));
            }

            ours.forEach((name, count) -> {
                // API 는 "루타비스 세트(해적)"처럼 직업 접미사를 붙인다. 우리 표는 "루타비스 세트"다.
                // 이름을 그대로 맞추면 대부분이 비교에서 빠지므로 접두 일치로 찾는다.
                Integer apiCount = api.get(name);
                if (apiCount == null) {
                    String head = name.replace(" 세트", "");
                    apiCount = api.entrySet().stream()
                            .filter(x -> x.getKey().startsWith(head))
                            .map(Map.Entry::getValue)
                            .findFirst().orElse(null);
                }
                if (apiCount != null && apiCount != count) {
                    out.append(String.join("\t", fixture.job(), fixture.characterName(), name,
                            String.valueOf(count), String.valueOf(apiCount),
                            String.valueOf(count - apiCount))).append('\n');
                }
            });
        });

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, out.toString());
        System.out.println("[diagnostic] " + REPORT.toAbsolutePath());
    }
}
