package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.CombatCalculationService;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 소스를 하나씩 <b>통째로 빼고</b> 재계산해 API 값과 비교한다.
 *
 * <p>"어느 소스를 우리가 넣지 말았어야 했나"를 찾는 도구다. 유니온만 빼보는
 * {@link UnionExclusionProbeTest}의 일반화. 빼는 폭이 커서 정확히 맞는 일은 드물지만,
 * 오차 부호가 뒤집히는 지점을 보면 후보를 좁힐 수 있다.
 *
 * <p>{@code -Dablation.names=가,나}
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "ablation.names", matches = ".+")
class SourceAblationTest {

    @Autowired private DataSheetService dataSheetService;
    @Autowired private CombatCalculationService combatCalculationService;
    @Autowired private BasicParser basicParser;
    @Autowired private StatParser statParser;
    @Autowired private ObjectMapper mapper;

    @Test
    void ablate() throws Exception {
        List<String> names = List.of(System.getProperty("ablation.names").split(","));

        Map<String, Consumer<DataSheet>> ablations = new LinkedHashMap<>();
        ablations.put("skill", s -> s.setSkill(empty()));
        ablations.put("hexaStat", s -> s.setHexaStat(empty()));
        ablations.put("ability", s -> s.setAbility(empty()));
        ablations.put("abilityPoint", s -> s.setAbilityPoint(empty()));
        ablations.put("hyperStat", s -> s.setHyperStat(empty()));
        ablations.put("setEffect", s -> s.setSetEffect(empty()));
        ablations.put("consumable", s -> s.setConsumableItem(empty()));
        ablations.put("otherStat", s -> s.setOtherStat(empty()));
        ablations.put("symbol", s -> s.setSymbol(empty()));
        ablations.put("unionArtifact", s -> s.setUnionArtifact(empty()));
        ablations.put("unionChampion", s -> s.setUnionChampion(empty()));
        ablations.put("unionOccupied", s -> s.setUnionOccupied(empty()));
        ablations.put("unionRaider", s -> s.setUnionRaider(empty()));
        ablations.put("펫장비", s -> s.setPetEquip(Map.of()));
        ablations.put("캐시장비", s -> s.setCashEquip(Map.of()));

        FixtureLoader.forEach(mapper, fixture -> {
            if (!names.contains(fixture.characterName())) {
                return;
            }
            var snapshot = fixture.snapshot();
            String job = basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC));
            int level = basicParser.characterLevel(snapshot.document(NexonEndpoint.BASIC));
            String raw = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
            long api = raw == null ? 0L : (long) Math.floor(Jsons.parseDouble(raw));
            long base = dataSheetService.getCurrentDataSheet(snapshot).getCombatPower();

            System.out.printf("%n── %s %s  API %,d  기준 %,d (%+.4f%%)%n",
                    fixture.job(), fixture.characterName(), api, base, (base - api) * 100.0 / api);
            ablations.forEach((name, ablate) -> {
                DataSheet sheet = dataSheetService.getCurrentDataSheet(snapshot);
                ablate.accept(sheet);
                sheet.setSumSheet(new StatSheet("종합"));
                sheet.buildSum();
                long value = combatCalculationService.estimateCombatPower(sheet, job, level);
                System.out.printf("   %-14s %,15d  %+9.4f%%  (기준대비 %+,d)%n",
                        name, value, (value - api) * 100.0 / api, value - base);
            });
        });
    }

    private StatSheet empty() {
        return new StatSheet("ablated");
    }
}
