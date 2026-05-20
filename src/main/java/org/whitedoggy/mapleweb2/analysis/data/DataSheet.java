package org.whitedoggy.mapleweb2.analysis.data;

import lombok.Getter;
import lombok.Setter;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

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

    Map<String, StatSheet> petEquip;
    Map<String, StatSheet> cashEquip;
    Map<String, StatSheet> itemEquip;
    StatSheet setEffect;

    StatSheet unionArtifact;
    StatSheet unionChampion;
    StatSheet unionOccupied;
    StatSheet unionRaider;

    //종합 시트
    StatSheet sumSheet;
}
