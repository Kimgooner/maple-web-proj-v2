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
import java.util.List;
import java.util.Map;

/**
 * 장비를 <b>부위 단위</b>로 덤프한다. 캐릭터 합계로는 안 잡히는 어긋남을 찾기 위한 것이다.
 *
 * <p>소스 합계(`SourceMatrixDumpTest`)는 어느 아이템이 문제인지 못 알려준다. 여기서는
 * 부위마다 우리 파서가 만든 StatSheet를 그대로 찍어, 원본 JSON의 옵션 문구와 1:1로
 * 대조할 수 있게 한다.
 *
 * <p>{@code -Ditem.names=가,나}로 좁힐 수 있다. 없으면 전 픽스처.
 * 결과: {@code build/reports/golden/item-effects.tsv}
 */
@SpringBootTest
class ItemEffectDumpTest {

    private static final Path REPORT = Path.of("build/reports/golden/item-effects.tsv");
    private static final List<String> NAMES =
            System.getProperty("item.names") == null
                    ? List.of()
                    : List.of(System.getProperty("item.names").split(","));

    @Autowired private DataSheetService dataSheetService;
    @Autowired private ObjectMapper mapper;

    @Test
    void dumpItemEffects() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append(String.join("\t", "job", "name", "slot",
                "STR", "DEX", "INT", "LUK", "ALL",
                "STRp", "DEXp", "INTp", "LUKp", "ALLp",
                "STRn", "DEXn", "INTn", "LUKn", "ALLn",
                "ATT", "MAG", "ATTp", "MAGp",
                "DMG", "BOSS", "CRIT", "FIN")).append('\n');

        FixtureLoader.forEach(mapper, fixture -> {
            if (!NAMES.isEmpty() && !NAMES.contains(fixture.characterName())) {
                return;
            }
            DataSheet sheet = dataSheetService.getCurrentDataSheet(fixture.snapshot());
            append(out, fixture, "장비", sheet.getItemEquip());
            append(out, fixture, "캐시", sheet.getCashEquip());
            append(out, fixture, "펫", sheet.getPetEquip());
        });

        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, out.toString());
        System.out.println("[diagnostic] " + REPORT.toAbsolutePath());
    }

    private void append(StringBuilder out, FixtureLoader.Fixture fixture,
                        String kind, Map<String, ItemSnapShot> items) {
        if (items == null) {
            return;
        }
        items.forEach((slot, item) -> {
            StatSheet s = item.getStatSheet();
            if (s == null) {
                return;
            }
            out.append(String.join("\t", fixture.job(), fixture.characterName(), kind + " - " + slot,
                    i(s.getSTR()), i(s.getDEX()), i(s.getINT()), i(s.getLUK()), i(s.getALL_STAT()),
                    i(s.getSTR_PERCENT()), i(s.getDEX_PERCENT()), i(s.getINT_PERCENT()),
                    i(s.getLUK_PERCENT()), i(s.getALL_STAT_PERCENT()),
                    i(s.getSTR_NO_PERCENT()), i(s.getDEX_NO_PERCENT()), i(s.getINT_NO_PERCENT()),
                    i(s.getLUK_NO_PERCENT()), i(s.getALL_STAT_NO_PERCENT()),
                    i(s.getATTACK_POWER()), i(s.getMAGIC_POWER()),
                    i(s.getATTACK_POWER_PERCENT()), i(s.getMAGIC_POWER_PERCENT()),
                    d(s.getDAMAGE()), d(s.getBOSS_DAMAGE()), d(s.getCRITICAL_DAMAGE()), d(s.getFINAL_DAMAGE())))
                    .append('\n');
        });
    }

    private String i(int value) {
        return String.valueOf(value);
    }

    private String d(double value) {
        return String.format("%.2f", value);
    }
}
