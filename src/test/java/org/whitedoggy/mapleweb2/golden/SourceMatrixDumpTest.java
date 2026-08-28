package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 480건 전체의 소스별 기여를 한 장의 TSV로 뽑는다.
 *
 * <p>직업 하나씩 찍는 {@link SourceContributionDumpTest}와 달리, "어떤 직업이 어떤 소스에서
 * 값을 못 받고 있는가"를 가로로 비교하기 위한 것이다.
 *
 * <p>결과: {@code build/reports/golden/source-matrix.tsv}
 */
@SpringBootTest
class SourceMatrixDumpTest {

    private static final Path REPORT = Path.of("build/reports/golden/source-matrix.tsv");

    @Autowired private DataSheetService dataSheetService;
    @Autowired private ObjectMapper mapper;

    @Test
    void dumpMatrix() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append(String.join("\t", "job", "name", "source",
                "STR", "DEX", "INT", "LUK", "ALL", "sPct", "aPct",
                "sNoPct", "aNoPct", "ATT", "ATTp", "MAG", "MAGp",
                "DMG", "BOSS", "CRIT", "FIN")).append('\n');

        FixtureLoader.forEach(mapper, fixture -> {
            DataSheet sheet = dataSheetService.getCurrentDataSheet(fixture.snapshot());
            Map<String, StatSheet> sources = new LinkedHashMap<>();
            sources.put("abilityPoint", sheet.getAbilityPoint());
            sources.put("symbol", sheet.getSymbol());
            sources.put("skill", sheet.getSkill());
            sources.put("hexaStat", sheet.getHexaStat());
            sources.put("ability", sheet.getAbility());
            sources.put("hyperStat", sheet.getHyperStat());
            sources.put("setEffect", sheet.getSetEffect());
            sources.put("consumable", sheet.getConsumableItem());
            sources.put("otherStat", sheet.getOtherStat());
            sources.put("unionArtifact", sheet.getUnionArtifact());
            sources.put("unionChampion", sheet.getUnionChampion());
            sources.put("unionOccupied", sheet.getUnionOccupied());
            sources.put("unionRaider", sheet.getUnionRaider());
            sources.put("펫장비", sum(sheet.getPetEquip()));
            sources.put("캐시장비", sum(sheet.getCashEquip()));
            sources.put("장비", sum(sheet.getItemEquip()));
            sources.put("종합", sheet.getSumSheet());

            sources.forEach((name, s) -> out.append(row(fixture.job(), fixture.characterName(), name, s)));
        });

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, out.toString());
        System.out.println("[diagnostic] " + REPORT.toAbsolutePath());
    }

    private StatSheet sum(Map<String, ItemSnapShot> items) {
        StatSheet total = new StatSheet("sum");
        if (items != null) {
            items.values().forEach(i -> {
                if (i.getStatSheet() != null) {
                    total.merge(i.getStatSheet());
                }
            });
        }
        return total;
    }

    private String row(String job, String name, String source, StatSheet s) {
        if (s == null) {
            s = new StatSheet("");
        }
        return String.join("\t", job, name, source,
                String.valueOf(s.getSTR()), String.valueOf(s.getDEX()),
                String.valueOf(s.getINT()), String.valueOf(s.getLUK()),
                String.valueOf(s.getALL_STAT()),
                String.valueOf(s.getSTR_PERCENT() + s.getDEX_PERCENT()
                        + s.getINT_PERCENT() + s.getLUK_PERCENT()),
                String.valueOf(s.getALL_STAT_PERCENT()),
                String.valueOf(s.getSTR_NO_PERCENT() + s.getDEX_NO_PERCENT()
                        + s.getINT_NO_PERCENT() + s.getLUK_NO_PERCENT()),
                String.valueOf(s.getALL_STAT_NO_PERCENT()),
                String.valueOf(s.getATTACK_POWER()), String.valueOf(s.getATTACK_POWER_PERCENT()),
                String.valueOf(s.getMAGIC_POWER()), String.valueOf(s.getMAGIC_POWER_PERCENT()),
                String.valueOf(s.getDAMAGE()), String.valueOf(s.getBOSS_DAMAGE()),
                String.valueOf(s.getCRITICAL_DAMAGE()), String.valueOf(s.getFINAL_DAMAGE())) + "\n";
    }
}
