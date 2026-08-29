package org.whitedoggy.mapleweb2.domain.pet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.common.support.ExpiryDates;
import org.whitedoggy.mapleweb2.domain.item.data.ItemRecord;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PetParser {
    private final StatSheetParser statSheetParser;

    /**
     * 슬롯 하나의 펫 장비. 펫 본체 만료일과 장비 노드를 함께 들고 있다.
     *
     * <p>어느 쪽 장비를 쓰는지는 {@code pet_activate_flag}가 정한다. 이 값이 1이면 세 슬롯이
     * <b>월드 공유 펫</b>이고 장비는 {@code world_share_pet_N_equipment}가 실제 착용분이다.
     * 이때 {@code pet_N_equipment}에도 값이 남아 있을 수 있지만 그것은 적용되지 않는다.
     *
     * <p>둘 다 보고 값이 있는 쪽을 쓰면 공유 펫인데도 옛 장비가 섞여 들어간다. 실제로
     * 제논 표본 250명 중 둘이 이 때문에 공격력이 7·8 모자랐다(후닝 142 대신 150,
     * 곡곡 128 대신 135). 나머지 248명은 어느 규칙이든 값이 같다.
     */
    private record PetSlot(String petExpire, JsonNode equipment) {
        boolean isEmpty() {
            return equipment.path("item_option").isEmpty();
        }
    }

    private PetSlot slot(JsonNode node, int index) {
        String prefix = usesWorldSharePet(node) ? "world_share_pet_" : "pet_";
        return new PetSlot(
                Jsons.text(node, prefix + index + "_date_expire"),
                node.path(prefix + index + "_equipment"));
    }

    /** 월드 공유 펫을 쓰는 캐릭터인가. */
    private boolean usesWorldSharePet(JsonNode node) {
        return "1".equals(Jsons.text(node, "pet_activate_flag"));
    }

    /**
     * 펫 장비 스탯. <b>펫 장비는 펫이 살아 있어야 적용된다</b> —
     * 펫 본체({@code pet_N_date_expire})가 만료되면 장비를 낀 채로도 스탯이 빠지므로
     * 장비 자체의 기간과 함께 본다.
     *
     * <p>{@code item_date_expire == "-1"}은 만료가 아니라 <b>장비를 끼지 않은 빈 슬롯</b>이다.
     */
    public List<ItemRecord> getItemSnapShot(JsonNode node, LocalDate referenceDate) {
        List<ItemRecord> itemRecords = new ArrayList<>();
        for(int i = 1; i <= 3; i++) {
            PetSlot slot = slot(node, i);
            if (slot.isEmpty()) continue;
            JsonNode equipment = slot.equipment();
            String equipExpire = Jsons.text(equipment, "item_date_expire");
            JsonNode options = equipment.path("item_option");
            String itemName = Jsons.text(equipment, "item_name");
            String itemIcon = Jsons.text(equipment, "item_icon");

            ItemSnapShot snapShot = new ItemSnapShot(itemName, itemIcon);
            StatSheet statSheet = new StatSheet(itemName);

            List<String> effects = new ArrayList<>();
            for (JsonNode option : options) {
                String type = Jsons.text(option, "option_type");
                String value = Jsons.text(option, "option_value");
                EffectTextSplitter.addSplit(effects, type + " " + value);
            }
            StatSheet parsed = statSheetParser.parse(effects);

            boolean expired = ExpiryDates.isAnyExpired(referenceDate, slot.petExpire(), equipExpire);
            if (expired) {
                if (!parsed.isZero()) {
                    snapShot.setExpired("펫/펫 장비 기간 만료");
                }
            } else {
                statSheet.merge(parsed);
            }
            snapShot.setStatSheet(statSheet);
            itemRecords.add(new ItemRecord("펫 장비 " + i, snapShot));
        }
        return itemRecords;
    }

    public List<String> getPetEquipmentEffects(JsonNode petEquipment, LocalDate referenceDate) {
        List<String> effects = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            PetSlot slot = slot(petEquipment, i);
            if (ExpiryDates.isAnyExpired(referenceDate,
                    slot.petExpire(), Jsons.text(slot.equipment(), "item_date_expire"))) {
                continue;
            }
            for (JsonNode option : slot.equipment().path("item_option")) {
                String type = option.path("option_type").asText("");
                int value = option.path("option_value").asInt(0);
                if (value > 0 && !type.isBlank()) {
                    EffectTextSplitter.addSplit(effects, type + " " + value);
                }
            }
        }
        return effects;
    }

    public List<String> getPetEquip(JsonNode petEquip, Integer index, LocalDate referenceDate) {
        List<String> effects = new ArrayList<>();
        PetSlot slot = slot(petEquip, index);
        String petEquipExpired = Jsons.text(slot.equipment(), "item_date_expire");
        if (!ExpiryDates.isAnyExpired(referenceDate, slot.petExpire(), petEquipExpired)) {
            if(!slot.isEmpty()) {
                JsonNode options = slot.equipment().path("item_option");
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
