package org.whitedoggy.mapleweb2.domain.item.data;

import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.List;

public record ItemRecord(
        String slot,
        ItemSnapShot itemSnapShot
) {
    public ItemRecord(String slot, ItemSnapShot itemSnapShot){
        this.slot = slot;
        this.itemSnapShot = itemSnapShot;
    }
}
