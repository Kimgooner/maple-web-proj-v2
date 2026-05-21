package org.whitedoggy.mapleweb2.domain.pet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PetParser {
    private final StatSheetParser statSheetParser;

    public List<ItemRecord> getItemSnapShot(JsonNode node) {
        List<ItemRecord> itemRecords = new ArrayList<>();
        for(int i = 1; i <= 3; i++) {
            JsonNode equipment = node.path("pet_" + i + "_equipment");
            String expired = Jsons.text(equipment, "item_date_expire");
            JsonNode options = equipment.path("item_option");

            if(expired.equals("-1")) continue;
            String itemName = Jsons.text(options, "item_name");
            String itemIcon = Jsons.text(options, "item_icon");

            ItemSheet itemSheet = new ItemSheet(itemName);
            StatSheet statSheet = new StatSheet(itemName);

            List<String> effects = new ArrayList<>();
            for (JsonNode option : options) {
                String type = Jsons.text(option, "option_type");
                String value = Jsons.text(option, "option_value");
                EffectTextSplitter.addSplit(effects, type + " " + value);
            }
            statSheet.merge(statSheetParser.parse(effects));
            itemRecords.add(new ItemRecord("펫 장비 " + i, new ItemSnapShot(itemSheet, statSheet)));
        }
        return itemRecords;
    }



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
