package org.whitedoggy.mapleweb2.domain.item.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

@Setter
@Getter
public class ItemSnapShot {
    String itemName;
    String itemIcon;

    Integer starForce;
    String p_grade;
    String ap_grade;

    String expired;
    StatSheet statSheet;

    public ItemSnapShot(String itemName, String itemIcon){
        this.itemName = itemName;
        this.itemIcon = itemIcon;
        this.expired = null;

        this.starForce = 0;
        this.p_grade = null;
        this.ap_grade = null;

        this.statSheet = null;
    }
}
