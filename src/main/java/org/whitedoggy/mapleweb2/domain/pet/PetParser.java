package org.whitedoggy.mapleweb2.domain.pet;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class PetParser {
    public List<String> getPetEquipmentEffects(JsonNode petEquipment) {
        List<String> effects = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            JsonNode equipment = petEquipment.path("pet_" + i + "_equipment");
            for (JsonNode option : equipment.path("item_option")) {
                String type = option.path("option_type").asText("");
                int value = option.path("option_value").asInt(0);
                if (value > 0 && !type.isBlank()) {
                    EffectTextSplitter.addSplit(effects, type + " " + value);
                }
            }
        }
        return effects;
    }
}
