package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 장비 프리셋 점수가 동점일 때 "현재 착용 프리셋"으로 끊는 규칙이 답을 얼마나 흔드는지 잰다.
 *
 * <pre>./gradlew test --tests '*PresetTieProbeTest' -Dpreset.tie=true</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "preset.tie", matches = "true")
class PresetTieProbeTest {

    @Autowired private DataSheetService dataSheetService;
    @Autowired private ItemEquipmentParser itemEquipmentParser;
    @Autowired private ObjectMapper mapper;

    @Test
    void howOftenDoesTheTieBreakChangeTheAnswer() throws Exception {
        List<String> swings = new ArrayList<>();
        int[] total = {0};
        int[] tied = {0};

        FixtureLoader.forEach(mapper, fixture -> {
            total[0]++;
            JsonNode itemEquip = fixture.snapshot().document(NexonEndpoint.ITEM_EQUIPMENT);
            List<Integer> presets = itemEquipmentParser.availablePresets(itemEquip);
            int best = presets.stream()
                    .mapToInt(p -> itemEquipmentParser.scorePreset(itemEquipmentParser.getItemEquipmentByPreset(itemEquip, p)))
                    .max().orElse(Integer.MIN_VALUE);
            List<Integer> top = presets.stream()
                    .filter(p -> itemEquipmentParser.scorePreset(itemEquipmentParser.getItemEquipmentByPreset(itemEquip, p)) == best)
                    .toList();
            if (top.size() < 2) {
                return;
            }
            tied[0]++;

            PresetSelection chosen = dataSheetService.getCombatPresetSelection(fixture.snapshot());
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (int preset : top) {
                DataSheet sheet = dataSheetService.getDataSheet(fixture.snapshot(), new PresetSelection(
                        preset, chosen.abilityPreset(), chosen.hyperStatPreset(), chosen.unionRaiderPreset()));
                long power = sheet.getCombatPower() == null ? 0 : sheet.getCombatPower();
                min = Math.min(min, power);
                max = Math.max(max, power);
            }
            if (max > 0 && max != min) {
                swings.add(String.format("%-28s 동점 %d개  최저 %,d  최고 %,d  차이 %.2f%%",
                        fixture.characterName(), top.size(), min, max, (max - min) * 100.0 / max));
            }
        });

        swings.sort((a, b) -> b.substring(b.lastIndexOf("차이")).compareTo(a.substring(a.lastIndexOf("차이"))));
        System.out.println("PRESET-TIE 표본 " + total[0] + "명, 동점 " + tied[0] + "명, 그중 전투력이 갈리는 경우 " + swings.size() + "명");
        swings.stream().limit(25).forEach(line -> System.out.println("PRESET-TIE   " + line));
    }
}
