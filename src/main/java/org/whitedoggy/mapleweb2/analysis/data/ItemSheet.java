package org.whitedoggy.mapleweb2.analysis.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

@Getter
@Setter
public class ItemSheet {
    String itemName;
    StatSheet statSheet;

    public ItemSheet(String itemName, StatSheet statSheet) {
        this.itemName = itemName;
        this.statSheet = statSheet;
    }
}
