package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 프리셋 점수가 동점일 때 답이 얼마나 흔들리는지 네 종류 모두 잰다.
 *
 * <p>장비·어빌리티·하이퍼스탯은 동점을 "현재 착용 프리셋"으로 끊는다. 그 말은 조회한 날
 * 무엇을 끼고 있었느냐가 답을 바꾼다는 뜻이라, 같은 날짜를 다시 봐도 값이 달라진다.
 * 유니온만 낮은 번호로 끊어 그 흔들림이 없다.
 *
 * <pre>./gradlew test --tests '*PresetTieProbeTest' -Dpreset.tie=true</pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "preset.tie", matches = "true")
class PresetTieProbeTest {

    @Autowired private DataSheetService dataSheetService;
    @Autowired private ItemEquipmentParser itemEquipmentParser;
    @Autowired private AbilityParser abilityParser;
    @Autowired private HyperStatParser hyperStatParser;
    @Autowired private RaiderParser raiderParser;
    @Autowired private BasicParser basicParser;
    @Autowired private ObjectMapper mapper;

    private record Kind(String label, boolean breaksByCurrentPreset) {
    }

    @Test
    void howOftenDoesATieChangeTheAnswer() throws Exception {
        List<Kind> kinds = List.of(
                new Kind("장비", true), new Kind("어빌리티", true),
                new Kind("하이퍼스탯", true), new Kind("유니온", false));
        int[] total = {0};
        int[][] tied = new int[4][1];
        int[][] swung = new int[4][1];
        double[] worst = new double[4];
        String[] worstName = new String[4];

        FixtureLoader.forEach(mapper, fixture -> {
            total[0]++;
            CharacterSnapshot snapshot = fixture.snapshot();
            String job = basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC));
            PresetSelection chosen = dataSheetService.getCombatPresetSelection(snapshot);
            List<List<Integer>> tops = List.of(
                    top(itemEquipmentParser.availablePresets(snapshot.document(NexonEndpoint.ITEM_EQUIPMENT)),
                            p -> itemEquipmentParser.scorePreset(itemEquipmentParser.getItemEquipmentByPreset(
                                    snapshot.document(NexonEndpoint.ITEM_EQUIPMENT), p))),
                    top(abilityParser.availablePresets(snapshot.document(NexonEndpoint.ABILITY)),
                            p -> abilityParser.scorePreset(snapshot.document(NexonEndpoint.ABILITY), p, job)),
                    top(hyperStatParser.availablePresets(snapshot.document(NexonEndpoint.HYPER_STAT)),
                            p -> hyperStatParser.scorePreset(snapshot.document(NexonEndpoint.HYPER_STAT), p)),
                    top(raiderParser.availablePresets(snapshot.document(NexonEndpoint.UNION_RAIDER)),
                            p -> raiderParser.scorePreset(snapshot.document(NexonEndpoint.UNION_RAIDER), p)));

            for (int k = 0; k < 4; k++) {
                List<Integer> candidates = tops.get(k);
                if (candidates.size() < 2) {
                    continue;
                }
                tied[k][0]++;
                long min = Long.MAX_VALUE;
                long max = Long.MIN_VALUE;
                for (int preset : candidates) {
                    long power = power(snapshot, withPreset(chosen, k, preset));
                    min = Math.min(min, power);
                    max = Math.max(max, power);
                }
                if (max > 0 && max != min) {
                    swung[k][0]++;
                    double gap = (max - min) * 100.0 / max;
                    if (gap > worst[k]) {
                        worst[k] = gap;
                        worstName[k] = fixture.characterName();
                    }
                }
            }
        });

        System.out.println("PRESET-TIE 표본 " + total[0] + "명");
        for (int k = 0; k < 4; k++) {
            Kind kind = kinds.get(k);
            System.out.printf("PRESET-TIE   %-6s 동점 %3d명  값이 갈림 %3d명  최대 %5.1f%% (%s)  동점처리=%s%n",
                    kind.label(), tied[k][0], swung[k][0], worst[k],
                    worstName[k] == null ? "-" : worstName[k],
                    kind.breaksByCurrentPreset() ? "현재착용" : "낮은번호");
        }
    }

    private List<Integer> top(List<Integer> presets, ToIntFunction<Integer> score) {
        int best = presets.stream().mapToInt(score).max().orElse(Integer.MIN_VALUE);
        List<Integer> result = new ArrayList<>();
        for (int preset : presets) {
            if (score.applyAsInt(preset) == best) {
                result.add(preset);
            }
        }
        return result;
    }

    private PresetSelection withPreset(PresetSelection base, int kind, int preset) {
        return switch (kind) {
            case 0 -> new PresetSelection(preset, base.abilityPreset(), base.hyperStatPreset(), base.unionRaiderPreset());
            case 1 -> new PresetSelection(base.itemPreset(), preset, base.hyperStatPreset(), base.unionRaiderPreset());
            case 2 -> new PresetSelection(base.itemPreset(), base.abilityPreset(), preset, base.unionRaiderPreset());
            default -> new PresetSelection(base.itemPreset(), base.abilityPreset(), base.hyperStatPreset(), preset);
        };
    }

    private long power(CharacterSnapshot snapshot, PresetSelection selection) {
        DataSheet sheet = dataSheetService.getDataSheet(snapshot, selection);
        return sheet.getCombatPower() == null ? 0 : sheet.getCombatPower();
    }
}
