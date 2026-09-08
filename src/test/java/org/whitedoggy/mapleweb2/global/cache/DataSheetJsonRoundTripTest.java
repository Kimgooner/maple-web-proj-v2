package org.whitedoggy.mapleweb2.global.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;
import org.whitedoggy.mapleweb2.analysis.service.DataSheetService;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.golden.FixtureLoader;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataSheet}이 JSON 왕복을 견디는지 본다. Redis 캐시가 시트를 JSON 으로 실어
 * 나르므로, 필드 하나가 조용히 빠지면 캐시에서 읽은 시점만 전투력이 틀어진다.
 *
 * <p>왕복 뒤 종합 시트를 다시 쌓아 비교하는 것이 핵심이다. 종합 시트는 모든 하위 시트를
 * 합친 것이라, 어느 시트의 어느 스탯이 빠져도 여기서 드러난다.
 */
@SpringBootTest
class DataSheetJsonRoundTripTest {

    private static final Path FIXTURE_DIR = Path.of("src/test/resources/fixtures");

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DataSheetService dataSheetService;

    /** 픽스처 없이도 도는 최소 왕복. 직렬화 형식이 깨지면 CI 에서 바로 걸린다. */
    @Test
    void handBuiltSheetSurvivesRoundTrip() {
        DataSheet original = handBuiltSheet();

        String json = mapper.writeValueAsString(original);
        DataSheet restored = mapper.readValue(json, DataSheet.class);

        assertEquals(json, mapper.writeValueAsString(restored), "왕복 뒤 JSON 이 달라졌다");
        assertEquals(1234567L, restored.getCombatPower());
        assertEquals(100, restored.getSymbol().getSTR());
        assertEquals(12.5, restored.getSymbol().getBOSS_DAMAGE());
        assertEquals("이름", restored.getItemEquip().get("장비 - 무기").getItemName());
        assertEquals("30", restored.getSourceEntries().get("skill").get("스킬").value());
        assertTrue(restored.isPirateBlessApplied());
    }

    /**
     * 골든 픽스처로 만든 진짜 시트의 왕복. 픽스처는 로컬 전용이라 없으면 건너뛴다.
     */
    @Test
    @EnabledIf("fixturesAvailable")
    void fixtureSheetsSurviveRoundTrip() throws Exception {
        List<String> failures = new ArrayList<>();

        FixtureLoader.forEach(mapper, fixture -> {
            DataSheet original = dataSheetService.getCombatDataSheet(fixture.snapshot());
            DataSheet restored = mapper.readValue(mapper.writeValueAsString(original), DataSheet.class);

            if (!java.util.Objects.equals(original.getCombatPower(), restored.getCombatPower())) {
                failures.add(fixture.file() + ": 전투력 " + original.getCombatPower() + " → " + restored.getCombatPower());
            }
            if (original.getItemEquip().size() != restored.getItemEquip().size()) {
                failures.add(fixture.file() + ": 장비 수 " + original.getItemEquip().size()
                        + " → " + restored.getItemEquip().size());
            }
            if (original.getSourceEntries().size() != restored.getSourceEntries().size()) {
                failures.add(fixture.file() + ": sourceEntries 수 " + original.getSourceEntries().size()
                        + " → " + restored.getSourceEntries().size());
            }
            if (!flags(original).equals(flags(restored))) {
                failures.add(fixture.file() + ": 플래그 " + flags(original) + " → " + flags(restored));
            }

            // 종합 시트를 양쪽 모두 새로 쌓아 비교한다. AnalysisService.prepareDataSheet 가 하는 일이다.
            original.buildSum();
            restored.buildSum();
            String originalSum = mapper.writeValueAsString(original.getSumSheet());
            String restoredSum = mapper.writeValueAsString(restored.getSumSheet());
            if (!originalSum.equals(restoredSum)) {
                failures.add(fixture.file() + ": 종합 시트\n  원본 " + originalSum + "\n  왕복 " + restoredSum);
            }
        });

        assertTrue(failures.isEmpty(), "JSON 왕복에서 값이 달라진 픽스처:\n" + String.join("\n", failures));
    }

    private static String flags(DataSheet sheet) {
        return sheet.isLucidTransformSuspected() + "/" + sheet.isPirateBlessApplied()
                + "/" + sheet.getExpiredArtifactCrystals() + "/" + sheet.getExpiredCashItems()
                + "/" + sheet.isExpiredTitleOption() + "/" + sheet.getExpiredPetEquipments()
                + "/" + sheet.isUnionRaiderDataMissing() + "/" + sheet.isWeaponNormalizationFailed()
                + "/" + sheet.isWeaponMissing() + "/" + sheet.isUnknownWeapon()
                + "/" + sheet.isInactiveCharacter() + "/" + sheet.isIncompleteSnapshot();
    }

    static boolean fixturesAvailable() {
        if (!Files.isDirectory(FIXTURE_DIR)) {
            return false;
        }
        try (var files = Files.list(FIXTURE_DIR)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".json.gz"));
        } catch (Exception e) {
            return false;
        }
    }

    private static DataSheet handBuiltSheet() {
        DataSheet sheet = new DataSheet();

        StatSheet symbol = new StatSheet("symbol");
        symbol.setSTR(100);
        symbol.setDEX(90);
        symbol.setALL_STAT_NO_PERCENT(30);
        symbol.setSTR_PER_LEVEL9(11);
        symbol.setATTACK_POWER(77);
        symbol.setMAGIC_POWER(78);
        symbol.setALL_STAT_PERCENT(7);
        symbol.setATTACK_POWER_PERCENT(9);
        symbol.setDAMAGE(3.5);
        symbol.setBOSS_DAMAGE(12.5);
        symbol.setCRITICAL_DAMAGE(4.25);
        symbol.setFINAL_DAMAGE(1.75);
        sheet.setSymbol(symbol);

        ItemSnapShot weapon = new ItemSnapShot("이름", "아이콘");
        weapon.setStarForce(22);
        weapon.setP_grade("레전드리");
        weapon.setAp_grade("유니크");
        weapon.setExpired("2026-01-01T00:00+09:00");
        weapon.setWeaponNormalizationFailed(true);
        weapon.setStatSheet(symbol);

        sheet.setItemEquip(Map.of("장비 - 무기", weapon));
        sheet.setPetEquip(Map.of());
        sheet.setCashEquip(Map.of());
        sheet.setSourceEntries(Map.of("skill", Map.of("스킬", new SourceEntry("30", "아이콘"))));
        sheet.setCombatPower(1234567L);
        sheet.setPirateBlessApplied(true);
        sheet.setWeaponNormalizationFailed(true);
        sheet.setExpiredArtifactCrystals(3);
        sheet.setIncompleteSnapshot(true);

        assertNotNull(sheet.getSumSheet());
        return sheet;
    }
}
