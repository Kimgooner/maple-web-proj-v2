package org.whitedoggy.mapleweb2.domain.item.parser;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ItemEquipmentParser {
    public Optional<Integer> getCurrentPresetItemEquipment(JsonNode itemEquipment) {
        return Jsons.optionalInt(itemEquipment, "preset_no");
    }

    public JsonNode getCurrentItemEquipment(JsonNode itemEquipment) {
        return itemEquipment.path("item_equipment");
    }

    public JsonNode getItemEquipmentByPreset(JsonNode itemEquipment, Integer presetNo) {
        if (presetNo == 1) {
            return itemEquipment.path("item_equipment_preset_1");
        }
        if (presetNo == 2) {
            return itemEquipment.path("item_equipment_preset_2");
        }
        if (presetNo == 3) {
            return itemEquipment.path("item_equipment_preset_3");
        }
        return getCurrentItemEquipment(itemEquipment);
    }

    public JsonNode getTitleItem(JsonNode itemEquipment) {
        return itemEquipment.path("title");
    }
    public JsonNode getDragonItem(JsonNode itemEquipment) { return itemEquipment.path("dragon_equipment"); }
    public JsonNode getMechanicItem(JsonNode itemEquipment) { return itemEquipment.path("mechanic_equipment"); }

    public List<Integer> availablePresets(JsonNode itemEquipment) {
        List<Integer> presets = new ArrayList<>();
        for (int presetNo = 1; presetNo <= 3; presetNo++) {
            JsonNode preset = getItemEquipmentByPreset(itemEquipment, presetNo);
            if (preset.isArray() && !preset.isEmpty()) {
                presets.add(presetNo);
            }
        }
        if (presets.isEmpty()) {
            presets.add(getCurrentPresetItemEquipment(itemEquipment).orElse(1));
        }
        return presets;
    }

    public int scorePreset(JsonNode presetItems) {
        if (!presetItems.isArray()) {
            return Integer.MIN_VALUE;
        }

        boolean hasSeedRing = false;
        boolean hasHalfEarring = false;
        boolean hasSpiritPendant = false;
        boolean hasGlasses = false;
        boolean hasSymbolFaceAccessory = false;

        for (JsonNode item : presetItems) {
            String slot = Jsons.text(item, "item_equipment_slot");
            String name = Jsons.text(item, "item_name");

            if (slot.startsWith("반지") && isSeedRing(name)) {
                hasSeedRing = true;
            }
            if (name.contains("하프 이어링")) {
                hasHalfEarring = true;
            }
            if (name.contains("정령의 펜던트")) {
                hasSpiritPendant = true;
            }
            if ("눈장식".equals(slot) && name.contains("안경")) {
                hasGlasses = true;
            }
            if ("얼굴장식".equals(slot) && (name.contains("심볼") || name.contains("상징"))) {
                hasSymbolFaceAccessory = true;
            }
        }

        int score = 0;
        if (hasSeedRing) {
            score += 100;
        }
        if (!hasHalfEarring) {
            score += 20;
        }
        if (!hasSpiritPendant) {
            score += 20;
        }
        if (!hasGlasses) {
            score += 10;
        }
        if (!hasSymbolFaceAccessory) {
            score += 10;
        }
        return score;
    }

    private boolean isSeedRing(String name) {
        return name.contains("리스트레인트 링")
                || name.contains("컨티뉴어스 링")
                || name.contains("웨폰퍼프")
                || name.contains("리밋 링")
                || name.contains("크리데미지 링")
                || name.contains("마나웨이브 링");
    }
}
