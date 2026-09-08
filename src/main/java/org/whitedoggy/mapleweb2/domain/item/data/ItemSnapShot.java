package org.whitedoggy.mapleweb2.domain.item.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

/** 캐시에 JSON 으로 실려 나가므로 기본 생성자가 필요하다. */
@Setter
@Getter
@NoArgsConstructor
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

    /**
     * 잠재·에디셔널 잠재만 따로 담은 시트. 없으면 {@code null}.
     *
     * <p>계산에는 쓰지 않는다 — 계산은 {@link #statSheet} 하나로 한다. 화면이 변화를
     * "옵션 / 잠재 / 익셉셔널"로 나눠 보여주려고 둔다. 옵션 몫은 따로 담지 않고
     * {@code statSheet - potentialStatSheet - exceptionalStatSheet} 로 뺀다 —
     * 효과 문구를 줄 단위로 더하는 파싱이라 이 뺄셈이 정확하다.
     */
    StatSheet potentialStatSheet;

    /** 익셉셔널만 따로 담은 시트. 없으면 {@code null}. */
    StatSheet exceptionalStatSheet;

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
