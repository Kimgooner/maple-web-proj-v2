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

import java.util.List;

/**
 * "유니온 공격대원·점령 효과가 실제로는 적용되지 않은 상태"라는 가설을 검증한다.
 *
 * <p>해당 두 소스만 비우고 다시 계산해 API 전투력과 맞는지 본다.
 * {@code -Dunion.probe=이름,이름}
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "union.probe", matches = ".+")
class UnionExclusionProbeTest {

    @Autowired private DataSheetService dataSheetService;
    @Autowired private CombatCalculationService combatCalculationService;
    @Autowired private BasicParser basicParser;
    @Autowired private StatParser statParser;
    @Autowired private ObjectMapper mapper;

    @Test
    void probe() throws Exception {
        List<String> names = List.of(System.getProperty("union.probe").split(","));
        System.out.printf("%n%-12s %-8s %14s %14s %8s %14s %8s%n",
                "캐릭터", "직업", "API", "현재계산", "오차%", "유니온제외", "오차%");
        FixtureLoader.forEach(mapper, fixture -> {
            if (!names.contains(fixture.characterName())) {
                return;
            }
            var snapshot = fixture.snapshot();
            String job = basicParser.characterClass(snapshot.document(NexonEndpoint.BASIC));
            int level = basicParser.characterLevel(snapshot.document(NexonEndpoint.BASIC));
            String apiText = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
            long api = apiText == null ? 0L : (long) Math.floor(Jsons.parseDouble(apiText));

            long with = dataSheetService.getCurrentDataSheet(snapshot).getCombatPower();

            DataSheet without = dataSheetService.getCurrentDataSheet(snapshot);
            without.setUnionOccupied(new StatSheet("unionOccupied"));
            without.setUnionRaider(new StatSheet("unionRaider"));
            without.setSumSheet(new StatSheet("종합"));
            without.buildSum();
            long bare = combatCalculationService.estimateCombatPower(without, job, level);

            System.out.printf("%-12s %-8s %,14d %,14d %+7.2f %,14d %+7.2f%n",
                    fixture.characterName(), fixture.job(), api, with,
                    (with - api) * 100.0 / api, bare, (bare - api) * 100.0 / api);
        });
    }
}
