package org.whitedoggy.mapleweb2.analysis.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class DataSheet {
    //기본 정보
    String ocid;
    LocalDate date;

    //캐릭터 정보
    String characterName;
    String characterClass;
    Integer characterLevel;
    String characterGuild;
    String characterWorld;

    //캐릭터 이미지
    String characterImage;

    //스탯 시트
    StatSheet abilityPoint;
    StatSheet symbol;
    StatSheet skill;
    StatSheet hexaStat;
    StatSheet ability;
    StatSheet hyperStat;

    Map<String, ItemSnapShot> petEquip;
    Map<String, ItemSnapShot> cashEquip;
    Map<String, ItemSnapShot> itemEquip;
    StatSheet setEffect;

    StatSheet unionArtifact;
    StatSheet unionChampion;
    StatSheet unionOccupied;
    StatSheet unionRaider;

    //종합 시트
    StatSheet sumSheet;
    long currentCombatPower;
    boolean lucidTransformSuspected;

    public void copy(DataSheet other) {
        this.ocid = other.ocid;
        this.date = other.date;

        this.characterName = other.characterName;
        this.characterClass = other.characterClass;
        this.characterLevel = other.characterLevel;
        this.characterGuild = other.characterGuild;
        this.characterWorld = other.characterWorld;
        this.characterImage = other.characterImage;

        this.abilityPoint = other.abilityPoint;
        this.symbol = other.symbol;
        this.skill = other.skill;
        this.hexaStat = other.hexaStat;
        this.ability = other.ability;
        this.hyperStat = other.hyperStat;

        this.petEquip = other.petEquip;
        this.cashEquip = other.cashEquip;
        this.itemEquip = other.itemEquip;
        this.sumSheet = other.sumSheet;
        this.setEffect = other.setEffect;

        this.unionArtifact = other.unionArtifact;
        this.unionChampion = other.unionChampion;
        this.unionOccupied = other.unionOccupied;
        this.unionRaider = other.unionRaider;

        this.currentCombatPower = other.currentCombatPower;
        this.lucidTransformSuspected = other.lucidTransformSuspected;
    }

    public void buildSum(){
        sumSheet.merge(this.abilityPoint);
        sumSheet.merge(this.symbol);
        sumSheet.merge(this.skill);
        sumSheet.merge(this.hexaStat);
        sumSheet.merge(this.ability);
        sumSheet.merge(this.hyperStat);
        sumSheet.merge(this.setEffect);
        sumSheet.merge(this.unionArtifact);
        sumSheet.merge(this.unionChampion);
        sumSheet.merge(this.unionOccupied);
        sumSheet.merge(this.unionRaider);

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
            sumSheet.merge(sheet);
        }
    }
}
