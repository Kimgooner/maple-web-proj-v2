package org.whitedoggy.mapleweb2.domain.union.champion;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChampionParser {
    public List<String> getChampionStats(JsonNode champion) {
        List<String> effects = new ArrayList<>();
        for (JsonNode effect : champion.path("champion_badge_total_info")) {
            EffectTextSplitter.addSplit(effects, Jsons.text(effect, "stat"));
        }
        return effects;
    }
}
