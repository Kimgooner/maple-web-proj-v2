package org.whitedoggy.mapleweb2.domain.hexa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HexaParser {

    private final HexaStatData hexaStatData;


    private String applyMainStat(String name, Integer level, List<String> mainStats) {
        boolean optional_percent = false;
        String description = null;
        switch(name) {
            case "보스 데미지 증가" -> {
                description = "보스 몬스터 공격 시 데미지";
                optional_percent = true;
            }
            case "크리티컬 데미지 증가" -> {
                description = "크리티컬 데미지";
                optional_percent = true;
            }
            case "데미지 증가" -> {
                description = "데미지";
                optional_percent = true;
            }
            case "공격력 증가" -> {
                description = "공격력";
            }
            case "마력 증가" -> {
                description = "마력";
            }
            case "주력 스탯 증가" -> {
                if(mainStats.size() == 1){
                    String stat = mainStats.getFirst();
                    description = stat;
                    //TODO : 데벤
                }
                else{
                    //TODO : 제논
                }
            }
        }
        // 테이블에 없는 스탯(방어율 무시 등)이나 아직 미지원 직업(제논)은 조용히 건너뛴다.
        // 빈 문자열은 StatSheetParser가 무시한다.
        if(description == null || !hexaStatData.hasMain(name)) return "";
        if(level == 0) return description + " 0";
        if(optional_percent) return description + " " + hexaStatData.mainValue(name, level) + "%";
        else return description + " " + hexaStatData.mainValue(name, level);
    }

    private String applySubStat(String name, Integer level, List<String> mainStats) {
        boolean optional_percent = false;
        String description = null;
        switch(name) {
            case "보스 데미지 증가" -> {
                description = "보스 몬스터 공격 시 데미지";
                optional_percent = true;
            }
            case "크리티컬 데미지 증가" -> {
                description = "크리티컬 데미지";
                optional_percent = true;
            }
            case "데미지 증가" -> {
                description = "데미지";
                optional_percent = true;
            }
            case "공격력 증가" -> {
                description = "공격력";
            }
            case "마력 증가" -> {
                description = "마력";
            }
            case "주력 스탯 증가" -> {
                if(mainStats.size() == 1){
                    // 데몬어벤져는 주력 스탯이 HP다. 표기만 HP로 바꾸고
                    // 수치는 "주력 스탯 증가" 테이블을 그대로 쓴다(name을 덮어쓰면 조회가 깨진다).
                    description = mainStats.getFirst();
                }
                else{
                    //TODO : 제논
                }
            }
        }
        if(description == null || !hexaStatData.hasSub(name)) return "";
        if(level == 0) return description + " 0";
        if(optional_percent) return description + " " + hexaStatData.subValue(name, level) + "%";
        else return description + " " + hexaStatData.subValue(name, level);
    }

    public List<String> getCurrentHexa(JsonNode hexa, List<String> mainStats) {
        List<String> effects = new ArrayList<>();
        List<JsonNode> stats = List.of(
                hexa.path("character_hexa_stat_core"),
                hexa.path("character_hexa_stat_core_2"),
                hexa.path("character_hexa_stat_core_3"),
                hexa.path("character_hexa_stat_core_4"),
                hexa.path("character_hexa_stat_core_5"),
                hexa.path("character_hexa_stat_core_6")
        );

        for (JsonNode core : stats) {
            if(core.isEmpty()) continue;
            JsonNode stat = core.get(0);
            String main = Jsons.text(stat, "main_stat_name");
            String sub1 = Jsons.text(stat, "sub_stat_name_1");
            String sub2 = Jsons.text(stat, "sub_stat_name_2");

            Integer main_lv = Jsons.optionalInt(stat, "main_stat_level").orElse(0);
            Integer sub_1_lv = Jsons.optionalInt(stat, "sub_stat_level_1").orElse(0);
            Integer sub_2_lv = Jsons.optionalInt(stat, "sub_stat_level_2").orElse(0);

            effects.add(applyMainStat(main, main_lv, mainStats));
            effects.add(applySubStat(sub1, sub_1_lv, mainStats));
            effects.add(applySubStat(sub2, sub_2_lv, mainStats));
        }
        return effects;
    }
}
