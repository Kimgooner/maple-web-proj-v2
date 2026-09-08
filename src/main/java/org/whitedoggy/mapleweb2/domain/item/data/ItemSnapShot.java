package org.whitedoggy.mapleweb2.domain.item.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.util.List;

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

    /**
     * 화면에 그대로 보여줄 효과 문구. 파싱하기 전의 줄들이다.
     *
     * <p>계산에는 쓰지 않는다. 장비를 교체했을 때 "이전 것은 이랬고 이후 것은 이렇다"를
     * 증감이 아니라 원문으로 나란히 보여주려고 남긴다. 증감으로 바꿔 적으면 잠재 한 줄이
     * 여러 스탯으로 흩어져, 무엇이 붙어 있었는지가 되레 안 보인다.
     */
    List<String> potentialLines;
    List<String> additionalPotentialLines;
    List<String> exceptionalLines;

    /** 게임 아이템 창처럼 보여줄 스탯 줄. 장비가 아니면 비어 있다. */
    List<ItemStatLine> statLines;

    /**
     * 설명문에서 뽑아낸 효과. 칭호처럼 옵션 필드 없이 설명문이 곧 스탯인 것들이다.
     * 계산이 실제로 읽는 줄과 같은 것을 화면에도 그대로 보여준다.
     */
    List<String> descriptionLines;

    /** 주문서 강화 횟수. 아이템 창의 "주문서 강화 3회". */
    Integer scrollUpgrade;

    /** 요구 레벨. {@code item_base_option.base_equipment_level}. */
    Integer requiredLevel;

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
