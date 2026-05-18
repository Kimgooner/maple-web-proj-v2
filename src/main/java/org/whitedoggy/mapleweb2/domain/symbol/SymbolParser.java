package org.whitedoggy.mapleweb2.domain.symbol;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class SymbolParser {
    public List<String> getSymbolStatEffects(JsonNode symbolEquipment) {
        List<String> effects = new ArrayList<>();
        for (JsonNode symbol : symbolEquipment.path("symbol")) {
            append(effects, "STR", symbol.path("symbol_str").asInt(0));
            append(effects, "DEX", symbol.path("symbol_dex").asInt(0));
            append(effects, "INT", symbol.path("symbol_int").asInt(0));
            append(effects, "LUK", symbol.path("symbol_luk").asInt(0));
            append(effects, "HP", symbol.path("symbol_hp").asInt(0));
        }
        return effects;
    }

    private void append(List<String> effects, String statName, int value) {
        if (value > 0) {
            effects.add(statName + " " + value + " 증가");
        }
    }
}
