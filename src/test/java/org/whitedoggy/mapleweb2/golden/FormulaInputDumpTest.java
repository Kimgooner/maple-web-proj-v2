package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.CharacterSnapshot;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
import org.whitedoggy.mapleweb2.domain.calculator.parser.StatParser;
import org.whitedoggy.mapleweb2.domain.common.stat.GameData;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 전투력 공식에 들어가는 입력값을 픽스처 전체에 대해 덤프한다.
 *
 * <p>전투력 값 하나만 보면 어느 항목이 부족한지 알 수 없다. 공식의 다섯 항
 * (주스탯 / 부스탯 / 공격력·마력 / 데미지·보스 / 크리티컬 데미지 / 최종 데미지)을 각각 남겨
 * 오차의 성격을 역산할 수 있게 한다. 단정하지 않고 자료만 만든다.
 *
 * <p>결과: {@code build/reports/golden/formula-inputs.tsv}
 */
@SpringBootTest
class FormulaInputDumpTest {

    private static final Path REPORT_PATH = Path.of("build/reports/golden/formula-inputs.tsv");

    @Autowired
    private DataSheetService dataSheetService;

    @Autowired
    private BasicParser basicParser;

    @Autowired
    private StatParser statParser;

    @Autowired
    private GameData gameData;

    @Autowired
    private org.whitedoggy.mapleweb2.domain.item.support.WeaponData weaponData;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void dumpFormulaInputs() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append(String.join("\t",
                "file", "job", "name", "world", "level",
                "mainStat", "mainFlat", "mainPct", "mainNoPct", "subStat", "subFlat", "subPct", "subNoPct", "subCount", "allStat", "allStatPct", "cSTR", "cDEX", "cINT", "cLUK", "hp", "hpPct", "hpNoPct", "power", "powerRaw", "powerPct", "damage", "bossDamage", "critDamage", "finalDamage",
                "wSet", "wPart", "wStar", "wAdd", "wStage", "wNorm", "wActual",
                "calc", "api")).append('\n');

        FixtureLoader.forEach(mapper, fixture -> {
            CharacterSnapshot snapshot = fixture.snapshot();
            JsonNode basic = snapshot.document(NexonEndpoint.BASIC);
            String characterClass = basicParser.characterClass(basic);
            int level = basicParser.characterLevel(basic);

            DataSheet dataSheet = dataSheetService.getCurrentDataSheet(snapshot);
            StatSheet sheet = dataSheet.getSumSheet();

            List<String> mains = gameData.mainStats(characterClass);
            List<String> subs = gameData.subStats(characterClass);
            double main = mains.isEmpty() ? 0.0 : statValue(mains.getFirst(), sheet, level);
            double sub = 0.0;
            for (String name : subs) {
                sub += statValue(name, sheet, level);
            }
            String mainName = mains.isEmpty() ? "" : mains.getFirst();
            double mainFlat = flatOf(mainName, sheet, level);
            double mainPct = pctOf(mainName, sheet);
            double mainNoPct = noPctOf(mainName, sheet);
            // 부스탯이 둘인 직업은 첫 번째 것만 성분으로 남긴다. 합계는 subStat에 있다.
            String subName = subs.isEmpty() ? "" : subs.getFirst();
            double subFlat = flatOf(subName, sheet, level);
            double subPct = pctOf(subName, sheet);
            double subNoPct = noPctOf(subName, sheet);

            double power = gameData.isMageClass(characterClass)
                    ? Math.floor(sheet.getMAGIC_POWER() * (100.0 + sheet.getMAGIC_POWER_PERCENT()) / 100.0)
                    : Math.floor(sheet.getATTACK_POWER() * (100.0 + sheet.getATTACK_POWER_PERCENT()) / 100.0);

            double powerRaw = gameData.isMageClass(characterClass)
                    ? sheet.getMAGIC_POWER() : sheet.getATTACK_POWER();
            double powerPct = gameData.isMageClass(characterClass)
                    ? sheet.getMAGIC_POWER_PERCENT() : sheet.getATTACK_POWER_PERCENT();

            // 무기 정규화 재현: 세트 표 + 주문서 작 + 활 기준 추가옵션
            String wSet = "-", wPart = "-";
            int wStar = 0, wAdd = 0, wNorm = 0, wActual = 0;
            Integer wStage = null;
            for (JsonNode item : snapshot.document(NexonEndpoint.ITEM_EQUIPMENT).path("item_equipment")) {
                if (!"무기".equals(item.path("item_equipment_slot").asText(""))) {
                    continue;
                }
                String name = item.path("item_name").asText("");
                wPart = item.path("item_equipment_part").asText("");
                wStar = item.path("starforce").asInt(0);
                boolean magic = gameData.isMagicWeaponPart(wPart);
                wAdd = item.path("item_add_option").path(magic ? "magic_power" : "attack_power").asInt(0);
                wActual = item.path("item_total_option").path(magic ? "magic_power" : "attack_power").asInt(0);
                var set = weaponData.resolveSet(name);
                wSet = set.name();
                wStage = weaponData.findStage(set.name(), wPart, wAdd);
                Integer scroll = item.path("scroll_upgrade").isMissingNode()
                        ? null : item.path("scroll_upgrade").asInt();
                wNorm = weaponData.starForceAttack(set.name(), wStar)
                        + weaponData.scrollAttack(set, scroll)
                        + weaponData.bowAddOption(set.name(), wStage);
            }

            String apiValue = statParser.currentCombatPower(snapshot.document(NexonEndpoint.STAT));
            long api = apiValue == null ? 0L : (long) Math.floor(Jsons.parseDouble(apiValue));

            out.append(String.join("\t",
                    fixture.file(), fixture.job(), fixture.characterName(),
                    basicParser.characterWorld(basic), String.valueOf(level),
                    fmt(main), fmt(mainFlat), fmt(mainPct), fmt(mainNoPct), fmt(sub),
                    fmt(subFlat), fmt(subPct), fmt(subNoPct), String.valueOf(subs.size()),
                    fmt(sheet.getALL_STAT()), fmt(sheet.getALL_STAT_PERCENT()),
                    fmt(statValue("STR", sheet, level)), fmt(statValue("DEX", sheet, level)),
                    fmt(statValue("INT", sheet, level)), fmt(statValue("LUK", sheet, level)),
                    // 데몬어벤져 역산용. HP는 calculateStat 경로가 없어 성분 그대로 남긴다.
                    fmt(sheet.getHP()), fmt(sheet.getHP_PERCENT()), fmt(sheet.getHP_NO_PERCENT()),
                    fmt(power), fmt(powerRaw), fmt(powerPct),
                    fmt(sheet.getDAMAGE()), fmt(sheet.getBOSS_DAMAGE()),
                    fmt(sheet.getCRITICAL_DAMAGE()), fmt(sheet.getFINAL_DAMAGE()),
                    wSet, wPart, String.valueOf(wStar), String.valueOf(wAdd),
                    String.valueOf(wStage), String.valueOf(wNorm), String.valueOf(wActual),
                    String.valueOf(dataSheet.getCombatPower()), String.valueOf(api))).append('\n');
        });

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, out.toString());
        System.out.println("[diagnostic] " + REPORT_PATH.toAbsolutePath());
    }

    /** calculateStat 의 세 성분. floor 전 기본값 / 곱해지는 % / 뒤에 더해지는 값. */
    private double flatOf(String statName, StatSheet s, int level) {
        return switch (statName) {
            case "STR" -> s.getSTR() + (level / 9) * s.getSTR_PER_LEVEL9() + s.getALL_STAT();
            case "DEX" -> s.getDEX() + (level / 9) * s.getDEX_PER_LEVEL9() + s.getALL_STAT();
            case "INT" -> s.getINT() + (level / 9) * s.getINT_PER_LEVEL9() + s.getALL_STAT();
            case "LUK" -> s.getLUK() + (level / 9) * s.getLUK_PER_LEVEL9() + s.getALL_STAT();
            default -> 0.0;
        };
    }

    private double pctOf(String statName, StatSheet s) {
        int base = switch (statName) {
            case "STR" -> s.getSTR_PERCENT();
            case "DEX" -> s.getDEX_PERCENT();
            case "INT" -> s.getINT_PERCENT();
            case "LUK" -> s.getLUK_PERCENT();
            default -> 0;
        };
        return base + s.getALL_STAT_PERCENT();
    }

    private double noPctOf(String statName, StatSheet s) {
        int base = switch (statName) {
            case "STR" -> s.getSTR_NO_PERCENT();
            case "DEX" -> s.getDEX_NO_PERCENT();
            case "INT" -> s.getINT_NO_PERCENT();
            case "LUK" -> s.getLUK_NO_PERCENT();
            default -> 0;
        };
        return base + s.getALL_STAT_NO_PERCENT();
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    /** CombatCalculationService.calculateStat 과 같은 식. */
    private double statValue(String statName, StatSheet sheet, int level) {
        int base;
        int perLevel9;
        int percent;
        int noPercent;
        switch (statName) {
            case "STR" -> {
                base = sheet.getSTR();
                perLevel9 = sheet.getSTR_PER_LEVEL9();
                percent = sheet.getSTR_PERCENT();
                noPercent = sheet.getSTR_NO_PERCENT();
            }
            case "DEX" -> {
                base = sheet.getDEX();
                perLevel9 = sheet.getDEX_PER_LEVEL9();
                percent = sheet.getDEX_PERCENT();
                noPercent = sheet.getDEX_NO_PERCENT();
            }
            case "INT" -> {
                base = sheet.getINT();
                perLevel9 = sheet.getINT_PER_LEVEL9();
                percent = sheet.getINT_PERCENT();
                noPercent = sheet.getINT_NO_PERCENT();
            }
            case "LUK" -> {
                base = sheet.getLUK();
                perLevel9 = sheet.getLUK_PER_LEVEL9();
                percent = sheet.getLUK_PERCENT();
                noPercent = sheet.getLUK_NO_PERCENT();
            }
            default -> {
                return 0.0;
            }
        }
        double flat = base + (level / 9) * perLevel9 + sheet.getALL_STAT();
        double scaled = flat * (100.0 + percent + sheet.getALL_STAT_PERCENT()) / 100.0;
        return Math.floor(scaled) + noPercent + sheet.getALL_STAT_NO_PERCENT();
    }
}
