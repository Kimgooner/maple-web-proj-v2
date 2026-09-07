package org.whitedoggy.mapleweb2.analysis.service;

import org.junit.jupiter.api.Test;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSheetCompareServiceEntryTest {

    private final DataSheetCompareService service = new DataSheetCompareService();

    @Test
    void reportsAddedRemovedAndChangedEntries() {
        DataSheet before = new DataSheet();
        before.setSourceEntries(Map.of("skill", Map.of("A", "Lv.1", "B", "Lv.3")));
        DataSheet after = new DataSheet();
        after.setSourceEntries(Map.of("skill", Map.of("A", "Lv.2", "C", "Lv.1")));

        List<DataSheetCompareService.EntryChange> changes = service.entryChanges("skill", before, after);

        assertThat(changes).containsExactlyInAnyOrder(
                new DataSheetCompareService.EntryChange("A", "Lv.1", "Lv.2"),
                new DataSheetCompareService.EntryChange("C", null, "Lv.1"),
                new DataSheetCompareService.EntryChange("B", "Lv.3", null));
    }

    @Test
    void missingSourceGivesNoChanges() {
        assertThat(service.entryChanges("symbol", new DataSheet(), new DataSheet())).isEmpty();
    }
}
