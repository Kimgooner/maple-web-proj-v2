package org.whitedoggy.mapleweb2.domain.combat.parser;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StatParser {
    public String currentCombatPower(JsonNode stat) {
        JsonNode finalStats = stat.path("final_stat");
        if (!finalStats.isArray()) {
            return null;
        }

        for (JsonNode node : finalStats) {
            if ("전투력".equals(Jsons.text(node, "stat_name"))) {
                return Jsons.text(node, "stat_value");
            }
        }
        return null;
    }

    public Map<String, String> finalStats(JsonNode stat) {
        Map<String, String> values = new LinkedHashMap<>();
        JsonNode finalStats = stat.path("final_stat");
        if (!finalStats.isArray()) {
            return values;
        }

        for (JsonNode node : finalStats) {
            values.put(Jsons.text(node, "stat_name"), Jsons.text(node, "stat_value"));
        }
        return values;
    }
}
