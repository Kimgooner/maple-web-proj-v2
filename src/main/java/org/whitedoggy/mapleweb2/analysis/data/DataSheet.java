package org.whitedoggy.mapleweb2.analysis.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.util.Map;

@Getter
@Setter
public class DataSheet {
    //스탯 시트
    StatSheet abilityPoint;
    StatSheet symbol;
    StatSheet skill;
    StatSheet hexaStat;
    StatSheet ability;
    StatSheet hyperStat;
    StatSheet otherStat;

    Map<String, ItemSnapShot> petEquip;
    Map<String, ItemSnapShot> cashEquip;
    Map<String, ItemSnapShot> itemEquip;
    StatSheet setEffect;
    StatSheet consumableItem;

    StatSheet unionArtifact;
    StatSheet unionChampion;
    StatSheet unionOccupied;
    StatSheet unionRaider;

    /** 컨버전 스타포스(제논·데몬어벤져). 장착 장비의 스타포스 합에서 오는 올스탯. */
    StatSheet conversionStarforce;

    /** 파이렛 블레스가 힘·민첩을 바꾸지 않는 부위. 심볼과 펫 장비도 대상이 아니지만 여기 담기지 않는다. */
    private static final java.util.Set<String> PIRATE_BLESS_EXCLUDED_SLOTS = java.util.Set.of("무기", "보조무기");

    //종합 시트
    StatSheet sumSheet = new StatSheet("종합");
    Long combatPower;

    boolean lucidTransformSuspected;

    /**
     * 모험가 해적의 파이렛 블레스를 켠 것으로 보아 장비의 힘·민첩을 바꿔 계산했는가.
     *
     * <p>API가 이 스킬의 사용 여부를 주지 않아 켠 쪽과 끈 쪽을 모두 계산하고
     * 전투력이 높은 쪽을 택한다. 손해면 아무도 켜지 않기 때문이다.
     */
    boolean pirateBlessApplied;

    /**
     * 유효기간이 지나 계산에서 제외한 유니온 아티팩트 크리스탈 수.
     * 0보다 크면 아직 게임에 반영되지 않았을 뿐 곧 전투력이 떨어질 상태다.
     */
    int expiredArtifactCrystals;

    /** 스탯이 붙어 있었는데 기간이 지나 빠진 캐시 장비 수. */
    int expiredCashItems;

    /** 스탯이 붙은 칭호의 옵션 기간이 지나 빠진 경우. */
    boolean expiredTitleOption;

    /** 펫 또는 펫 장비 기간이 지나 스탯이 빠진 펫 장비 수. */
    int expiredPetEquipments;

    /**
     * 챌린저스가 아닌 월드인데 유니온 공격대 정보가 없는 상태.
     * 공격대원·점령 효과가 빠진 채로 계산되므로 전투력이 낮게 나온다.
     * (2026년 7월 유니온 개편 이후 미접속이면 이렇게 온다.)
     */
    boolean unionRaiderDataMissing;

    /**
     * 무기 추가옵션이 몇 추인지 표에서 찾지 못한 상태.
     * 활 기준 환산값(수백의 공격력)이 통째로 빠진 채 계산되므로 전투력이 크게 낮게 나온다.
     * 조용히 0으로 넘어가면 드러나지 않으므로 플래그로 남긴다.
     */
    boolean weaponNormalizationFailed;

    /**
     * 무기를 아예 끼지 않은 상태. 게임에서는 이러면 전투력이 0이고,
     * API도 대개 0을 준다(표본 335명 중 266명). 활 환산할 대상이 없다.
     *
     * <p>API 는 프리셋 1의 무기를 프리셋 2·3에도 그대로 내려주므로,
     * 무기가 비어 있다는 건 프리셋 1에 무기가 없다는 뜻이다.
     */
    boolean weaponMissing;

    /**
     * 이름이 어느 세트 키워드에도 맞지 않는 무기. 활 기준 공격력을 모른다.
     * 표본 8,785명 중 461명이고, 레이븐혼(여제 장비군)처럼 활 자체가 없는
     * 무기군도 있어 원리적으로 환산할 수 없는 것이 섞여 있다.
     */
    boolean unknownWeapon;

    /**
     * 최근 7일간 접속하지 않은 캐릭터. API 의 stat 문서가 마지막 접속 시점에 멈춰
     * 있을 수 있어 우리 계산과 어긋난다 — 재조회해도 수렴하지 않는다.
     */
    boolean inactiveCharacter;

    public void copy(DataSheet other) {
        this.abilityPoint = other.abilityPoint;
        this.symbol = other.symbol;
        this.skill = other.skill;
        this.hexaStat = other.hexaStat;
        this.ability = other.ability;
        this.hyperStat = other.hyperStat;
        this.otherStat = other.otherStat;

        this.petEquip = other.petEquip;
        this.cashEquip = other.cashEquip;
        this.itemEquip = other.itemEquip;
        this.sumSheet = other.sumSheet;
        this.setEffect = other.setEffect;
        this.consumableItem = other.consumableItem;

        this.unionArtifact = other.unionArtifact;
        this.unionChampion = other.unionChampion;
        this.unionOccupied = other.unionOccupied;
        this.unionRaider = other.unionRaider;
        this.conversionStarforce = other.conversionStarforce;

        this.lucidTransformSuspected = other.lucidTransformSuspected;
        this.expiredArtifactCrystals = other.expiredArtifactCrystals;
        this.expiredCashItems = other.expiredCashItems;
        this.expiredTitleOption = other.expiredTitleOption;
        this.expiredPetEquipments = other.expiredPetEquipments;
        this.unionRaiderDataMissing = other.unionRaiderDataMissing;
        this.weaponNormalizationFailed = other.weaponNormalizationFailed;
        this.weaponMissing = other.weaponMissing;
        this.unknownWeapon = other.unknownWeapon;
        this.inactiveCharacter = other.inactiveCharacter;
    }

    public void buildSum(){
        buildSum(false);
    }

    /** {@code itemEquip}의 키는 {@code "장비 - 무기"}처럼 접두사가 붙어 온다. 뒤쪽 부위명만 뽑는다. */
    private static String slotOf(String itemEquipKey) {
        int separator = itemEquipKey.lastIndexOf(" - ");
        return separator < 0 ? itemEquipKey : itemEquipKey.substring(separator + 3);
    }

    /**
     * 종합 시트를 새로 만든다.
     *
     * @param pirateBless 참이면 무기·보조무기를 뺀 장착 장비의 STR과 DEX를 바꿔 합친다.
     *                    모험가 해적의 파이렛 블레스다. 심볼·펫장비·AP·하이퍼스탯·유니온은
     *                    스왑 대상이 아니라 그대로 둔다.
     */
    public void buildSum(boolean pirateBless){
        sumSheet = new StatSheet("종합");
        sumSheet.merge(this.abilityPoint);
        sumSheet.merge(this.symbol);
        sumSheet.merge(this.skill);
        sumSheet.merge(this.hexaStat);
        sumSheet.merge(this.ability);
        sumSheet.merge(this.hyperStat);
        sumSheet.merge(this.otherStat);
        sumSheet.merge(this.setEffect);
        sumSheet.merge(this.consumableItem);
        sumSheet.merge(this.unionArtifact);
        sumSheet.merge(this.unionChampion);
        sumSheet.merge(this.unionOccupied);
        sumSheet.merge(this.unionRaider);
        if (this.conversionStarforce != null) {
            sumSheet.merge(this.conversionStarforce);
        }

        for (Map.Entry<String, ItemSnapShot> m : petEquip.entrySet()){
            StatSheet sheet = m.getValue().getStatSheet();
            sumSheet.merge(sheet);
        }
        for (Map.Entry<String, ItemSnapShot> m : cashEquip.entrySet()){
            StatSheet sheet = m.getValue().getStatSheet();
            sumSheet.merge(sheet);
        }
        for (Map.Entry<String, ItemSnapShot> m : itemEquip.entrySet()){
            StatSheet sheet = m.getValue().getStatSheet();
            if (pirateBless && !PIRATE_BLESS_EXCLUDED_SLOTS.contains(slotOf(m.getKey()))) {
                sheet = sheet.swappedStrDex();
            }
            sumSheet.merge(sheet);
        }
    }
}
