package org.whitedoggy.mapleweb2.domain.item.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

@Setter
@Getter
public class ItemSnapShot {
    ItemSheet itemSheet;
    StatSheet statSheet;

    public ItemSnapShot(ItemSheet itemSheet, StatSheet statSheet){
        this.itemSheet = itemSheet;
        this.statSheet = statSheet;
    }
}
