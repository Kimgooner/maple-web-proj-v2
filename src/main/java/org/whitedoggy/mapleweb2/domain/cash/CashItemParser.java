package org.whitedoggy.mapleweb2.domain.cash;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.common.support.ExpiryDates;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CashItemParser {
    private final StatSheetParser statSheetParser;

    /**
     * 캐시 장비 하나를 읽는다.
     *
     * <p>본체 기간({@code date_expire})이나 옵션 기간({@code date_option_expire})이 지났으면
     * 스탯을 반영하지 않는다. 만료 표시는 <b>실제로 스탯이 있던 장비</b>에만 남긴다 —
     * 대부분의 캐시 장비는 옵션이 없어 만료돼도 전투력과 무관하기 때문이다.
     */
    public ItemRecord getItemSnapShot(JsonNode item, LocalDate referenceDate){
        String itemName = Jsons.text(item, "cash_item_name");
        String itemSlot = Jsons.text(item, "cash_item_equipment_slot");
        String itemIcon = Jsons.text(item, "cash_item_icon");
        JsonNode options = item.get("cash_item_option");

        ItemSnapShot snapShot = new ItemSnapShot(itemName, itemIcon);
        StatSheet statSheet = new StatSheet(itemSlot);

        List<String> effects = new ArrayList<>();
        for (JsonNode option : options) {
            String type = Jsons.text(option, "option_type");
            String value = Jsons.text(option, "option_value");
            EffectTextSplitter.addSplit(effects, type + " " + value);
        }
        StatSheet parsed = statSheetParser.parse(effects);

        boolean expired = ExpiryDates.isAnyExpired(referenceDate,
                Jsons.text(item, "date_expire"), Jsons.text(item, "date_option_expire"));
        if (expired) {
            if (!parsed.isZero()) {
                snapShot.setExpired("기간 만료");
            }
        } else {
            statSheet.merge(parsed);
        }
        snapShot.setStatSheet(statSheet);
        return new ItemRecord(itemSlot, snapShot);
    }

    public List<String> getCashStatEffect(JsonNode cash, LocalDate referenceDate) {
        List<String> effects = new ArrayList<>();
        JsonNode cashItems = cash.path("cash_item_equipment_base");
        for (JsonNode item : cashItems) {
            if (ExpiryDates.isAnyExpired(referenceDate,
                    Jsons.text(item, "date_expire"), Jsons.text(item, "date_option_expire"))) {
                continue;
            }
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
