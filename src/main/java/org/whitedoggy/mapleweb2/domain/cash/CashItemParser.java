package org.whitedoggy.mapleweb2.domain.cash;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class CashItemParser {
    public List<String> getCashStatEffect(JsonNode cash) {
        List<String> effects = new ArrayList<>();
        JsonNode cashItems = cash.path("cash_item_equipment_base");
        for (JsonNode item : cashItems) {
            JsonNode options = item.path("cash_item_option");
            for (JsonNode option : options) {
                String type = Jsons.text(option, "option_type");
                String value = Jsons.text(option, "option_value");
                effects.add(type + " " + value);
            }
        }
        return effects;
    }
}
