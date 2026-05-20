package org.whitedoggy.mapleweb2.domain.union.artifact;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArtifactParser {
    public List<String> getArtifactEffects(JsonNode artifact) {
        List<String> effects = new ArrayList<>();
        for (JsonNode effect : artifact.path("union_artifact_effect")) {
            EffectTextSplitter.addSplit(effects, Jsons.text(effect, "name"));
        }
        return effects;
    }

    public List<String> getArtifactEffectsFromCrystal(JsonNode crystal) {
        List<String> effects = new ArrayList<>();
        JsonNode artifacts = crystal.path("union_artifact_crystal");
        for(JsonNode artifact : artifacts){
            String flag = Jsons.text(artifact, "validity_flag");
            if(flag.equals("1")) continue;
            Integer level = Integer.parseInt(Jsons.text(artifact, "level"));
            String option1 = Jsons.text(artifact, "crystal_option_name_1");
            String option2 = Jsons.text(artifact, "crystal_option_name_2");
            String option3 = Jsons.text(artifact, "crystal_option_name_3");
            EffectTextSplitter.addSplit(effects, getEffectString(option1, level));
            EffectTextSplitter.addSplit(effects, getEffectString(option2, level));
            EffectTextSplitter.addSplit(effects, getEffectString(option3, level));
        }
        return effects;
    }

    private String getEffectString(String option, Integer level){
        if(option.equals("보스 몬스터 공격 시 데미지 증가")) return "보스 몬스터 공격 시 데미지 " + (1.5 * (double) level) + "% 증가";
        if(option.equals("크리티컬 데미지 증가")) return "크리티컬 데미지 " + (0.4 * (double) level) + "% 증가";
        if(option.equals("데미지 증가")) return "데미지 " + (1.5 * (double) level) + "% 증가";
        if(option.equals("올스탯 증가")) return "올스탯 " + (15 * level) + " 증가";
        if(option.equals("공격력/마력 증가")) return "공격력 " + (3 * level) + ", 마력 " + (3 * level) + " 증가";
        if(option.equals("최대 HP/MP 증가")) return "최대 HP " + (750 * level) + ", 최대 MP " + (750 * level) + " 증가";
        return "";
    }
}
