package org.whitedoggy.mapleweb2.domain.hexa;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HexaParser {
    private Map<String, double[]> MAIN_STAT = Map.ofEntries(
            Map.entry("보스 데미지 증가" , new double[]{1, 2, 3, 4, 6, 8, 10, 13, 16, 20}),
            Map.entry("크리티컬 데미지 증가", new double[]{0.35, 0.7, 1.05, 1.4, 2.1, 2.8, 3.5, 4.55, 5.6, 7}),
            Map.entry("데미지 증가", new double[]{0.75, 1.5, 2.25, 3, 4.5, 6, 7.5, 9.75, 12, 15}),
            Map.entry("공격력 증가", new double[]{5, 10, 15, 20, 30, 40, 50, 65, 80, 100}),
            Map.entry("마력 증가", new double[]{5, 10, 15, 20, 30, 40, 50, 65, 80, 100}),
            Map.entry("주력 스탯 증가", new double[]{100, 200, 300, 400, 600, 800, 1000, 1300, 1600, 2000})
    );
    private Map<String, double[]> SUB_STAT = Map.ofEntries(
            Map.entry("보스 데미지 증가" , new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}),
            Map.entry("크리티컬 데미지 증가", new double[]{0.35, 0.7, 1.05, 1.4, 1.75, 2.1, 2.45, 2.8, 3.15, 3.5}),
            Map.entry("데미지 증가", new double[]{0.75, 1.5, 2.25, 3, 3.75, 4.5, 5.25, 6, 6.75, 7.5}),
            Map.entry("공격력 증가", new double[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50}),
            Map.entry("마력 증가", new double[]{5, 10, 15, 20, 25, 30, 35, 40, 45, 50}),
            Map.entry("주력 스탯 증가", new double[]{100, 200, 300, 400, 500, 600, 700, 800, 900, 1000})
    );

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
        if(optional_percent) return description + " " + MAIN_STAT.get(name)[level-1] + "%";
        else return description + " " + MAIN_STAT.get(name)[level-1];
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
                    String stat = mainStats.getFirst();
                    if(stat.equals("HP")) name = "HP";
                    description = stat;
                }
                else{
                    //TODO : 제논
                }
            }
        }
        if(optional_percent) return description + " " + SUB_STAT.get(name)[level-1] + "%";
        else return description + " " + SUB_STAT.get(name)[level-1];
    }

    public List<String> getCurrentHexa(JsonNode hexa, List<String> mainStats) {
        List<String> effects = new ArrayList<>();
        List<JsonNode> stats = List.of(
                hexa.path("character_hexa_stat_core"),
                hexa.path("character_hexa_stat_core_2"),
                hexa.path("character_hexa_stat_core_3")
        );

        for (JsonNode core : stats) {
            JsonNode stat = core.get(0);
            String main = Jsons.text(stat, "main_stat_name");
            String sub1 = Jsons.text(stat, "sub_stat_name_1");
            String sub2 = Jsons.text(stat, "sub_stat_name_2");

            System.out.println(main + ", " + sub1 + ", " + sub2);

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
