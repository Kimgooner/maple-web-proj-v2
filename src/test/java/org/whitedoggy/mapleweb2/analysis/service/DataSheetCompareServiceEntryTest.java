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

    @Test
    void missingSourceGivesNoChanges() {
        assertThat(service.entryChanges("symbol", new DataSheet(), new DataSheet())).isEmpty();
    }
}
