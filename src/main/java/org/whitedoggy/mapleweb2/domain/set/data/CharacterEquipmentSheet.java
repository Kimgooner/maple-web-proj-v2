package org.whitedoggy.mapleweb2.domain.set.data;

import java.util.*;

public class CharacterEquipmentSheet {
    private final Map<String, String> equipped = new HashMap<>();

    private static final List<String> RING_SLOTS = List.of("반지1", "반지2", "반지3", "반지4");
    private static final List<String> PENDANT_SLOTS = List.of("펜던트", "펜던트2");

    public CharacterEquipmentSheet() {
        this.equipped.put("모자", null);
        this.equipped.put("얼굴장식", null);
        this.equipped.put("눈장식", null);
        this.equipped.put("귀고리", null);
        this.equipped.put("상의", null);
        this.equipped.put("하의", null);
        this.equipped.put("한벌옷", null);
        this.equipped.put("신발", null);
        this.equipped.put("장갑", null);
        this.equipped.put("망토", null);
        this.equipped.put("보조무기", null);
        this.equipped.put("무기", null);
        this.equipped.put("반지1", null);
        this.equipped.put("반지2", null);
        this.equipped.put("반지3", null);
        this.equipped.put("반지4", null);
        this.equipped.put("펜던트", null);
        this.equipped.put("펜던트2", null);
        this.equipped.put("훈장", null);
        this.equipped.put("벨트", null);
        this.equipped.put("어깨장식", null);
        this.equipped.put("포켓 아이템", null);
        this.equipped.put("기계 심장", null);
        this.equipped.put("뱃지", null);
        this.equipped.put("엠블렘", null);
    }

    public String getItem(String slot) {
        return equipped.get(slot);
    }

    public void setItem(String slot, String item) {
        equipped.put(slot, item);
    }

    public boolean hasSlot(String slot) {
        return equipped.containsKey(slot);
    }

    public Set<String> slots() {
        return equipped.keySet();
    }

    public List<String> ringItems() {
        return itemsForSlots(RING_SLOTS);
    }

    public List<String> itemsForSlot(String slot) {
        return switch (slot) {
            case "반지" -> ringItems();
            case "펜던트" -> itemsForSlots(PENDANT_SLOTS);
            default -> itemAsList(slot);
        };
    }

    private List<String> itemsForSlots(List<String> slots) {
        List<String> items = new ArrayList<>();
        for (String slot : slots) {
            String item = equipped.get(slot);
            if (item != null && !item.isBlank()) {
                items.add(item);
            }
        }
        return items;
    }

    private List<String> itemAsList(String slot) {
        String item = equipped.get(slot);
        if (item == null || item.isBlank()) {
            return List.of();
        }
        return List.of(item);
    }
}
