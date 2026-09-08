package org.whitedoggy.mapleweb2.analysis.service;

import org.junit.jupiter.api.Test;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반지·펜던트는 부위 번호가 고정이 아니다. 같은 반지를 반지2에서 반지3으로 옮겨 끼면
 * 부위별로 비교했을 때 "교체" 두 건이 서로 반대 방향으로 잡힌다 — 실제로는 아무것도
 * 바뀌지 않았는데 화면에는 +44/-44 가 나란히 뜬다.
 */
class DataSheetCompareServiceRingTest {

    private final DataSheetCompareService service = new DataSheetCompareService();

    @Test
    void swappingTwoRingsBetweenSlotsIsNotAChange() {
        DataSheet before = sheet(Map.of(
                "장비 - 반지2", ring("테네브리스 링", 44),
                "장비 - 반지3", ring("여명의 가디언 링", 30)));
        DataSheet after = sheet(Map.of(
                "장비 - 반지2", ring("여명의 가디언 링", 30),
                "장비 - 반지3", ring("테네브리스 링", 44)));

        assertThat(service.diff(before, after).itemChanges())
                .as("자리만 바꿔 낀 반지는 변화가 아니다")
                .isEmpty();
    }

    /** 자리를 옮기면서 스탯도 달라졌다면 그건 한 건으로 잡혀야 한다. */
    @Test
    void aRingThatMovedAndChangedIsOneChange() {
        DataSheet before = sheet(Map.of("장비 - 반지2", ring("테네브리스 링", 44)));
        DataSheet after = sheet(Map.of("장비 - 반지3", ring("테네브리스 링", 55)));

        assertThat(service.diff(before, after).itemChanges())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.previousSlot()).isEqualTo("장비 - 반지2");
                    assertThat(change.currentSlot()).isEqualTo("장비 - 반지3");
                    assertThat(change.previousItemName()).isEqualTo("테네브리스 링");
                    assertThat(change.currentItemName()).isEqualTo("테네브리스 링");
                });
    }

    /** 진짜 교체는 그대로 잡혀야 한다. */
    @Test
    void replacingARingIsStillAChange() {
        DataSheet before = sheet(Map.of("장비 - 반지1", ring("테네브리스 링", 44)));
        DataSheet after = sheet(Map.of("장비 - 반지1", ring("여명의 가디언 링", 30)));

        assertThat(service.diff(before, after).itemChanges()).singleElement()
                .satisfies(change -> assertThat(change.changeType()).isEqualTo("REPLACED"));
    }

    /** 반지가 아닌 부위는 번호가 고정이라 예전대로 부위끼리 비교한다. */
    @Test
    void fixedSlotsStillCompareBySlot() {
        DataSheet before = sheet(Map.of("장비 - 모자", ring("아케인셰이드 햇", 10)));
        DataSheet after = sheet(Map.of("장비 - 모자", ring("에테르넬 햇", 20)));

        assertThat(service.diff(before, after).itemChanges()).singleElement()
                .satisfies(change -> assertThat(change.slot()).isEqualTo("장비 - 모자"));
    }

    private static DataSheet sheet(Map<String, ItemSnapShot> items) {
        DataSheet sheet = new DataSheet();
        sheet.setItemEquip(new LinkedHashMap<>(items));
        sheet.setPetEquip(Map.of());
        sheet.setCashEquip(Map.of());
        sheet.setCombatPower(0L);
        return sheet;
    }

    private static ItemSnapShot ring(String name, int str) {
        ItemSnapShot item = new ItemSnapShot(name, name + ".png");
        StatSheet stats = new StatSheet(name);
        stats.setSTR(str);
        item.setStatSheet(stats);
        return item;
    }
}
