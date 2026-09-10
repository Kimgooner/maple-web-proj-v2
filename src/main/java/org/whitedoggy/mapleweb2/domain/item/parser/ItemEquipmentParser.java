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

    /**
     * 그 프리셋의 칭호. <b>칭호도 장비 프리셋을 따라간다</b>(게임 안에서 확인). API 가
     * {@code title_preset1~3} 으로 준다 — 필드 이름에 밑줄이 없어서, 장비 쪽
     * {@code item_equipment_preset_1} 을 흉내 내 만들면 안 잡힌다.
     *
     * <p>{@code title} 은 지금 착용 중인 것이라, 우리가 고른 보스 프리셋과 다를 수 있다.
     * 그걸 그대로 쓰면 장비는 2번인데 칭호만 1번 것이 섞인 값이 나온다.
     *
     * <p>그 자리가 비어 있으면 착용 중인 것으로 돌아간다. 칭호 프리셋을 따로 안 걸어 둔
     * 캐릭터가 흔하다 — 골든 479개 중 145개가 슬롯 1 에만 있고 2·3 이 비어 있었다.
     * 그런 캐릭터는 어느 프리셋을 껴도 같은 칭호가 붙는다.
     *
     * <p>슬롯에 <b>다른</b> 칭호가 들어 있어 착용 중인 것과 갈리는 경우는 표본 879명(골든
     * 479 + 실측 400) 중 한 명뿐이었다. 그 한 명(은월-14)은 넥슨 데이터 자체가 섞여 있다 —
     * {@code title} 의 이름은 슬롯 1 과 같은데 만료일은 슬롯 2 와 같다. 그래서 그 표본만
     * 넥슨 전투력과 어긋나는데, 규칙이 아니라 그쪽 데이터가 잘못된 것으로 본다.
     */
    public JsonNode getTitleItem(JsonNode itemEquipment, int presetNo) {
        JsonNode byPreset = itemEquipment.path("title_preset" + presetNo);
        return byPreset.isObject() && byPreset.hasNonNull("title_name")
                ? byPreset
                : getTitleItem(itemEquipment);
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
