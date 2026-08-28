package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 소스별 기여도를 덤프한다. 같은 직업인데 오차가 다른 캐릭터를 나란히 놓고
 * 어느 소스가 비었는지 눈으로 찾기 위한 도구.
 *
 * <p>결과: {@code build/reports/golden/source-contributions.txt}
 */
@SpringBootTest
class SourceContributionDumpTest {

    private static final Path REPORT_PATH = Path.of("build/reports/golden/source-contributions.txt");

    /**
     * 비교 대상. {@code -Dsource.job=키네시스}로 한 직업 전체를,
     * {@code -Dsource.names=가,나}로 특정 캐릭터만 찍는다.
     */
    private static final String JOB = System.getProperty("source.job", "");
    private static final java.util.List<String> NAMES =
            System.getProperty("source.names") == null
                    ? java.util.List.of()
                    : java.util.List.of(System.getProperty("source.names").split(","));

    @Autowired
    private DataSheetService dataSheetService;

    @Autowired
    private org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser statParser;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void dumpSourceContributions() throws Exception {
        StringBuilder out = new StringBuilder();
        FixtureLoader.forEach(mapper, fixture -> {
            boolean wanted = (!JOB.isBlank() && JOB.equals(fixture.job()))
                    || NAMES.contains(fixture.characterName());
            if (!wanted) {
                return;
            }
            DataSheet sheet = dataSheetService.getCurrentDataSheet(fixture.snapshot());
            String apiText = statParser.currentCombatPower(
                    fixture.snapshot().document(NexonEndpoint.STAT));
            long api = apiText == null ? 0L : (long) Math.floor(Jsons.parseDouble(apiText));
            double error = api == 0 ? 0.0 : (sheet.getCombatPower() - api) * 100.0 / api;
            out.append(String.format("── %s %s  계산=%,d API=%,d 오차=%+.2f%%%n",
                    fixture.job(), fixture.characterName(), sheet.getCombatPower(), api, error));

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
            sources.forEach((name, s) -> out.append(line(name, s)));

            out.append(line("[합] 펫 장비", sum(sheet.getPetEquip())));
            out.append(line("[합] 캐시 장비", sum(sheet.getCashEquip())));
            out.append(line("[합] 장비", sum(sheet.getItemEquip())));
            sheet.getItemEquip().forEach((slot, item) -> {
                if (item.getStatSheet() != null && !item.getStatSheet().isZero()) {
                    out.append(line("   " + slot, item.getStatSheet()));
                }
            });
            out.append(line("== 종합", sheet.getSumSheet()));
            out.append('\n');
        });
        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, out.toString());
        System.out.println(out);
    }

    private StatSheet sum(Map<String, ItemSnapShot> items) {
        StatSheet total = new StatSheet("sum");
        if (items != null) {
            items.values().forEach(item -> {
                if (item.getStatSheet() != null) {
                    total.merge(item.getStatSheet());
                }
            });
        }
        return total;
    }

    private String line(String name, StatSheet s) {
        if (s == null) {
            return String.format("   %-16s (null)%n", name);
        }
        return String.format("   %-16s STR%6d DEX%6d INT%6d LUK%6d ALL%5d | %%: S%3d D%3d I%3d L%3d A%3d "
                        + "| NP: S%5d D%5d I%5d L%5d A%5d | ATT%5d(%3d%%) MAG%5d(%3d%%) "
                        + "| DMG%6.1f BOSS%7.1f CRIT%6.1f FINAL%6.1f%n",
                name, s.getSTR(), s.getDEX(), s.getINT(), s.getLUK(), s.getALL_STAT(),
                s.getSTR_PERCENT(), s.getDEX_PERCENT(), s.getINT_PERCENT(), s.getLUK_PERCENT(), s.getALL_STAT_PERCENT(),
                s.getSTR_NO_PERCENT(), s.getDEX_NO_PERCENT(), s.getINT_NO_PERCENT(), s.getLUK_NO_PERCENT(),
                s.getALL_STAT_NO_PERCENT(),
                s.getATTACK_POWER(), s.getATTACK_POWER_PERCENT(), s.getMAGIC_POWER(), s.getMAGIC_POWER_PERCENT(),
                s.getDAMAGE(), s.getBOSS_DAMAGE(), s.getCRITICAL_DAMAGE(), s.getFINAL_DAMAGE());
    }
}
