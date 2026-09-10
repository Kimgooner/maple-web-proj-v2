package org.whitedoggy.mapleweb2.analysis.service;

import org.junit.jupiter.api.Test;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSheetCompareServiceEntryTest {

    private final DataSheetCompareService service = new DataSheetCompareService();

    @Test
    void reportsAddedRemovedAndChangedEntries() {
        DataSheet before = new DataSheet();
        before.setSourceEntries(Map.of("skill", Map.of("A", SourceEntry.of("Lv.1"), "B", new SourceEntry("Lv.3", "b.png"))));
        DataSheet after = new DataSheet();
        after.setSourceEntries(Map.of("skill", Map.of("A", new SourceEntry("Lv.2", "a.png"), "C", SourceEntry.of("Lv.1"))));

        List<DataSheetCompareService.EntryChange> changes = service.entryChanges("skill", before, after);

        assertThat(changes).containsExactlyInAnyOrder(
                new DataSheetCompareService.EntryChange("A", "Lv.1", "Lv.2", "a.png"),
                new DataSheetCompareService.EntryChange("C", null, "Lv.1", null),
                new DataSheetCompareService.EntryChange("B", "Lv.3", null, "b.png"));
    }

    /**
     * 세트는 구성 수가 늘어도 새로 붙는 옵션이 없는 구간이 있다(보스 장신구가 두 개마다
     * 한 단계씩 오른다). 그때 "3세트 → 4세트" 만 적으면 전투력이 안 움직인 줄이 하나 는다.
     */
    @Test
    void 붙는_옵션이_그대로인_세트는_구성_수만_바뀌어도_적지_않는다() {
        String options = "3세트 보스 몬스터 공격 시 데미지 10% 증가";
        DataSheet before = sheetWith("setEffect", "보스 장신구 세트", new SourceEntry("3세트", null, options));
        DataSheet after = sheetWith("setEffect", "보스 장신구 세트", new SourceEntry("4세트", null, options));

        assertThat(service.entryChanges("setEffect", before, after)).isEmpty();
    }

    @Test
    void 옵션이_달라진_세트는_그대로_적는다() {
        DataSheet before = sheetWith("setEffect", "칠흑의 보스 세트", new SourceEntry("3세트", null, "3세트 올스탯 10"));
        DataSheet after = sheetWith("setEffect", "칠흑의 보스 세트", new SourceEntry("4세트", null, "3세트 올스탯 10\n4세트 공격력 20"));

        assertThat(service.entryChanges("setEffect", before, after))
                .extracting(DataSheetCompareService.EntryChange::name)
                .containsExactly("칠흑의 보스 세트");
    }

    /**
     * 우리 표로 직접 세는 세트(앱솔랩스처럼 넥슨 문서에는 직업 접미사가 붙어 이름이 안 맞는
     * 것들)는 옵션 문구가 늘 null 이다. null 끼리 같다고 지우면 진짜 단계 상승이 통째로 사라진다.
     */
    @Test
    void 붙는_옵션을_모르는_세트는_지우지_않는다() {
        DataSheet before = sheetWith("setEffect", "앱솔랩스 세트", new SourceEntry("4세트", null));
        DataSheet after = sheetWith("setEffect", "앱솔랩스 세트", new SourceEntry("5세트", null));

        assertThat(service.entryChanges("setEffect", before, after))
                .extracting(DataSheetCompareService.EntryChange::name)
                .containsExactly("앱솔랩스 세트");
    }

    private DataSheet sheetWith(String source, String name, SourceEntry entry) {
        DataSheet sheet = new DataSheet();
        sheet.setSourceEntries(Map.of(source, Map.of(name, entry)));
        return sheet;
    }

    @Test
    void missingSourceGivesNoChanges() {
        assertThat(service.entryChanges("symbol", new DataSheet(), new DataSheet())).isEmpty();
    }
}
