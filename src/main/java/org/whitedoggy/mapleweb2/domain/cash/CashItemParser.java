package org.whitedoggy.mapleweb2.domain.cash;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CashItemParser {
    private final StatSheetParser statSheetParser;

    public ItemRecord getItemSnapShot(JsonNode item){
        String itemName = Jsons.text(item, "cash_item_name");
        String itemSlot = Jsons.text(item, "cash_item_equipment_slot");
        String itemIcon = Jsons.text(item, "cash_item_icon");
        String expired = Jsons.text(item, "date_option_expire");
        JsonNode options = item.get("cash_item_option");

        ItemSheet itemSheet = new ItemSheet(itemName);
        StatSheet statSheet = new StatSheet(itemSlot);

        itemSheet.setItemIcon(itemIcon);
        if(!expired.equals("expired")) {
            List<String> effects = new ArrayList<>();
            for (JsonNode option : options) {
                String type = Jsons.text(option, "option_type");
                String value = Jsons.text(option, "option_value");
                EffectTextSplitter.addSplit(effects, type + " " + value);
            }
            statSheet.merge(statSheetParser.parse(effects));
        }
        else{
            itemSheet.setExpired("옵션 기간 만료");
        }
        return new ItemRecord(itemSlot, new ItemSnapShot(itemSheet, statSheet));
    }

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

    /*
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
     */
}
