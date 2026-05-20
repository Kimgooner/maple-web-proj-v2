package org.whitedoggy.mapleweb2.domain.cash;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.analysis.data.ItemRecord;
import org.whitedoggy.mapleweb2.analysis.support.SupportMethods;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<ItemRecord> getCashEquip(JsonNode node) {
        JsonNode cashItems = node.path("cash_item_equipment_base");
        List<ItemRecord> result = new ArrayList<>();
        for (JsonNode item : cashItems) {
            List<String> effects = new ArrayList<>();
            String slot = Jsons.text(item, "cash_item_equipment_slot");
            String name = Jsons.text(item, "cash_item_name");
            JsonNode options = item.path("cash_item_option");
            for(JsonNode option : options) {
                String type = Jsons.text(option, "option_type");
                String value = Jsons.text(option, "option_value");
                EffectTextSplitter.addSplit(effects, type + " " + value);
            }
            result.add(new ItemRecord(slot, name, effects));
        }
        return result;
    }
}
