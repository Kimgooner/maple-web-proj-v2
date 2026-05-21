package org.whitedoggy.mapleweb2.domain.item.data;

import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

public class ItemSnapShot {
    ItemSheet itemSheet;
    StatSheet statSheet;

    public ItemSnapShot(ItemSheet itemSheet, StatSheet statSheet){
        this.itemSheet = itemSheet;
        this.statSheet = statSheet;
    }
}
