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

    /** 무기 추가옵션 단계를 표에서 못 찾아 활 환산값 없이 계산됐다. */
    boolean weaponNormalizationFailed;

    StatSheet statSheet;

    public ItemSnapShot(String itemName, String itemIcon){
        this.itemName = itemName;
        this.itemIcon = itemIcon;
        this.expired = null;
        this.weaponNormalizationFailed = false;

        this.starForce = 0;
        this.p_grade = null;
        this.ap_grade = null;

        this.statSheet = null;
    }
}
