package org.whitedoggy.mapleweb2.domain.pet;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
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

    public List<String> getPetEquip(JsonNode petEquip, Integer index) {
        List<String> effects = new ArrayList<>();
        String petExpired = Jsons.text(petEquip, "pet_" + index + "_date_expire");
        if(!petExpired.equals("expired")) {
            JsonNode equipment = petEquip.path("pet_" + index + "_equipment");
            String petEquipExpired = Jsons.text(equipment, "item_date_expire");
            if(!petEquipExpired.equals("-1")) {
                JsonNode options = equipment.path("item_option");
                for (JsonNode option : options) {
                    String type = Jsons.text(option, "option_type");
                    int value = Integer.parseInt(Jsons.text(option, "option_value"));
                    EffectTextSplitter.addSplit(effects, type + " " + value);
                }
            }
        }
        return effects;
    }
}
