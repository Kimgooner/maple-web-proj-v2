package org.whitedoggy.mapleweb2.analysis.service;

import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DataSheetCompareService {
    private static final List<String> RING_SLOTS = List.of("반지1", "반지2", "반지3", "반지4");
    private static final List<String> PENDANT_SLOTS = List.of("펜던트", "펜던트2");
    private static final Set<String> FLEXIBLE_ITEM_SLOTS = Set.of(
            "반지1", "반지2", "반지3", "반지4", "펜던트", "펜던트2"
    );

    private static final List<StatField> STAT_FIELDS = List.of(
            new StatField("STR", "STR"),
            new StatField("DEX", "DEX"),
            new StatField("INT", "INT"),
            new StatField("LUK", "LUK"),
            new StatField("HP", "HP"),
            new StatField("ALL_STAT", "ALL_STAT"),
            new StatField("STR_PER_LEVEL9", "STR_PER_LEVEL9"),
            new StatField("DEX_PER_LEVEL9", "DEX_PER_LEVEL9"),
            new StatField("INT_PER_LEVEL9", "INT_PER_LEVEL9"),
            new StatField("LUK_PER_LEVEL9", "LUK_PER_LEVEL9"),
            new StatField("STR_NO_PERCENT", "STR_NO_PERCENT"),
            new StatField("DEX_NO_PERCENT", "DEX_NO_PERCENT"),
            new StatField("INT_NO_PERCENT", "INT_NO_PERCENT"),
            new StatField("LUK_NO_PERCENT", "LUK_NO_PERCENT"),
            new StatField("HP_NO_PERCENT", "HP_NO_PERCENT"),
            new StatField("ALL_STAT_NO_PERCENT", "ALL_STAT_NO_PERCENT"),
            new StatField("ATTACK_POWER", "ATTACK_POWER"),
            new StatField("MAGIC_POWER", "MAGIC_POWER"),
            new StatField("STR_PERCENT", "STR_PERCENT"),
            new StatField("DEX_PERCENT", "DEX_PERCENT"),
            new StatField("INT_PERCENT", "INT_PERCENT"),
            new StatField("LUK_PERCENT", "LUK_PERCENT"),
            new StatField("HP_PERCENT", "HP_PERCENT"),
            new StatField("ALL_STAT_PERCENT", "ALL_STAT_PERCENT"),
            new StatField("ATTACK_POWER_PERCENT", "ATTACK_POWER_PERCENT"),
            new StatField("MAGIC_POWER_PERCENT", "MAGIC_POWER_PERCENT"),
            new StatField("DAMAGE", "DAMAGE"),
            new StatField("BOSS_DAMAGE", "BOSS_DAMAGE"),
            new StatField("CRITICAL_DAMAGE", "CRITICAL_DAMAGE"),
            new StatField("FINAL_DAMAGE", "FINAL_DAMAGE")
    );

    public List<CombatPresetDiff> diffCombatPresetSeries(List<DataSheet> dataSheets) {
        List<DataSheet> nonNullSheets = dataSheets.stream()
                .filter(dataSheet -> dataSheet != null)
                .toList();

        List<CombatPresetDiff> diffs = new ArrayList<>();
        for (int index = 1; index < nonNullSheets.size(); index++) {
            diffs.add(diff(nonNullSheets.get(index - 1), nonNullSheets.get(index)));
        }
        return diffs;
    }

    public CombatPresetDiff diff(DataSheet previous, DataSheet current) {
        long previousCombatPower = previous.getCombatPower() == null ? 0L : previous.getCombatPower();
        long currentCombatPower = current.getCombatPower() == null ? 0L : current.getCombatPower();

        return new CombatPresetDiff(
                null,
                null,
                previousCombatPower,
                currentCombatPower,
                currentCombatPower - previousCombatPower,
                summarizeCoreSheets(previous, current, 6),
                summarizeItemChanges(previous.getPetEquip(), current.getPetEquip(), 3),
                summarizeItemChanges(previous.getCashEquip(), current.getCashEquip(), 3),
                summarizeItemChanges(previous.getItemEquip(), current.getItemEquip(), 5)
        );
    }

    private List<ChangeSummary> summarizeCoreSheets(DataSheet previous, DataSheet current, int limit) {
        List<ChangeSummary> summaries = new ArrayList<>();
        addSheetSummary(summaries, "abilityPoint", previous.getAbilityPoint(), current.getAbilityPoint(), previous, current);
        addSheetSummary(summaries, "symbol", previous.getSymbol(), current.getSymbol(), previous, current);
        addSheetSummary(summaries, "skill", previous.getSkill(), current.getSkill(), previous, current);
        addSheetSummary(summaries, "hexaStat", previous.getHexaStat(), current.getHexaStat(), previous, current);
        addSheetSummary(summaries, "ability", previous.getAbility(), current.getAbility(), previous, current);
        addSheetSummary(summaries, "hyperStat", previous.getHyperStat(), current.getHyperStat(), previous, current);
        addSheetSummary(summaries, "setEffect", previous.getSetEffect(), current.getSetEffect(), previous, current);
        addSheetSummary(summaries, "unionOccupied", previous.getUnionOccupied(), current.getUnionOccupied(), previous, current);
        addSheetSummary(summaries, "unionRaider", previous.getUnionRaider(), current.getUnionRaider(), previous, current);
        addSheetSummary(summaries, "unionArtifact", previous.getUnionArtifact(), current.getUnionArtifact(), previous, current);
        addSheetSummary(summaries, "unionChampion", previous.getUnionChampion(), current.getUnionChampion(), previous, current);

        return summaries.stream()
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(limit)
                .toList();
    }

    private void addSheetSummary(List<ChangeSummary> summaries, String label, StatSheet before, StatSheet after,
                                 DataSheet previous, DataSheet current) {
        if (before == null || after == null) {
            return;
        }

        StatSheet delta = after.minus(before);
        if (delta.isZero()) {
            return;
        }

        List<StatDelta> statDeltas = summarizeStatDelta(delta, 3);
        summaries.add(new ChangeSummary(label, statDeltas, entryChanges(label, previous, current), weight(statDeltas)));
    }

    /** 이름 있는 항목의 변화 목록. 생김·사라짐·값 변경만 남기고, 같은 것은 뺀다. */
    List<EntryChange> entryChanges(String source, DataSheet previous, DataSheet current) {
        Map<String, SourceEntry> before = entriesOf(previous, source);
        Map<String, SourceEntry> after = entriesOf(current, source);
        List<EntryChange> changes = new ArrayList<>();
        for (Map.Entry<String, SourceEntry> entry : after.entrySet()) {
            SourceEntry old = before.get(entry.getKey());
            String oldValue = old == null ? null : old.value();
            if (!entry.getValue().value().equals(oldValue)) {
                changes.add(new EntryChange(entry.getKey(), oldValue, entry.getValue().value(), entry.getValue().icon()));
            }
        }
        for (Map.Entry<String, SourceEntry> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                changes.add(new EntryChange(entry.getKey(), entry.getValue().value(), null, entry.getValue().icon()));
            }
        }
        return changes;
    }

    private static Map<String, SourceEntry> entriesOf(DataSheet sheet, String source) {
        if (sheet == null || sheet.getSourceEntries() == null) {
            return Map.of();
        }
        return sheet.getSourceEntries().getOrDefault(source, Map.of());
    }

    private List<SlotChangeSummary> summarizeItemChanges(Map<String, ItemSnapShot> before, Map<String, ItemSnapShot> after, int limit) {
        Map<String, ItemSnapShot> beforeItems = before == null ? Map.of() : before;
        Map<String, ItemSnapShot> afterItems = after == null ? Map.of() : after;

        List<SlotChangeSummary> changes = new ArrayList<>();
        changes.addAll(summarizeFlexibleSlotGroup(beforeItems, afterItems, RING_SLOTS));
        changes.addAll(summarizeFlexibleSlotGroup(beforeItems, afterItems, PENDANT_SLOTS));
        changes.addAll(summarizeFixedSlots(beforeItems, afterItems));

        return changes.stream()
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(limit)
                .toList();
    }

    private List<SlotChangeSummary> summarizeFixedSlots(
            Map<String, ItemSnapShot> before,
            Map<String, ItemSnapShot> after
    ) {
        Map<String, ItemSnapShot> all = new LinkedHashMap<>();
        all.putAll(before);
        after.forEach(all::putIfAbsent);

        List<SlotChangeSummary> changes = new ArrayList<>();
        for (String slot : all.keySet()) {
            if (FLEXIBLE_ITEM_SLOTS.contains(slot)) {
                continue;
            }

            SlotChangeSummary change = compareMatchedItems(
                    slot,
                    slot,
                    before.get(slot),
                    after.get(slot)
            );
            if (change != null) {
                changes.add(change);
            }
        }
        return changes;
    }

    private List<SlotChangeSummary> summarizeFlexibleSlotGroup(
            Map<String, ItemSnapShot> before,
            Map<String, ItemSnapShot> after,
            List<String> slots
    ) {
        List<EquippedItem> beforeItems = equippedItems(before, slots);
        List<EquippedItem> unmatchedAfterItems = new ArrayList<>(equippedItems(after, slots));
        List<SlotChangeSummary> changes = new ArrayList<>();

        for (EquippedItem beforeItem : beforeItems) {
            EquippedItem sameItem = findSameItem(beforeItem, unmatchedAfterItems);
            if (sameItem != null) {
                unmatchedAfterItems.remove(sameItem);
                SlotChangeSummary change = compareMatchedItems(
                        beforeItem.slot(),
                        sameItem.slot(),
                        beforeItem.itemSnapShot(),
                        sameItem.itemSnapShot()
                );
                if (change != null) {
                    changes.add(change);
                }
                continue;
            }

            if (!unmatchedAfterItems.isEmpty()) {
                EquippedItem afterItem = unmatchedAfterItems.remove(0);
                changes.add(buildSlotChange(
                        beforeItem.slot(),
                        afterItem.slot(),
                        "REPLACED",
                        beforeItem.itemSnapShot(),
                        afterItem.itemSnapShot()
                ));
                continue;
            }

            changes.add(buildSlotChange(beforeItem.slot(), null, "REMOVED", beforeItem.itemSnapShot(), null));
        }

        for (EquippedItem afterItem : unmatchedAfterItems) {
            changes.add(buildSlotChange(null, afterItem.slot(), "ADDED", null, afterItem.itemSnapShot()));
        }

        return changes;
    }

    private List<EquippedItem> equippedItems(Map<String, ItemSnapShot> itemsBySlot, List<String> slots) {
        List<EquippedItem> items = new ArrayList<>();
        for (String slot : slots) {
            ItemSnapShot itemSnapShot = itemsBySlot.get(slot);
            if (itemSnapShot != null) {
                items.add(new EquippedItem(slot, itemSnapShot));
            }
        }
        return items;
    }

    private EquippedItem findSameItem(EquippedItem beforeItem, List<EquippedItem> afterItems) {
        String itemName = beforeItem.itemSnapShot().getItemName();
        if (itemName == null || itemName.isBlank()) {
            return null;
        }

        for (EquippedItem afterItem : afterItems) {
            if (itemName.equals(afterItem.itemSnapShot().getItemName())) {
                return afterItem;
            }
        }
        return null;
    }

    private SlotChangeSummary compareMatchedItems(
            String previousSlot,
            String currentSlot,
            ItemSnapShot beforeItem,
            ItemSnapShot afterItem
    ) {
        if (beforeItem == null && afterItem == null) {
            return null;
        }

        StatSheet delta = statSheet(afterItem, displaySlot(previousSlot, currentSlot) + " after")
                .minus(statSheet(beforeItem, displaySlot(previousSlot, currentSlot) + " before"));
        if (sameItemName(beforeItem, afterItem) && delta.isZero()) {
            return null;
        }

        return buildSlotChange(previousSlot, currentSlot, changeType(beforeItem, afterItem), beforeItem, afterItem);
    }

    private SlotChangeSummary buildSlotChange(
            String previousSlot,
            String currentSlot,
            String changeType,
            ItemSnapShot beforeItem,
            ItemSnapShot afterItem
    ) {
        String displaySlot = displaySlot(previousSlot, currentSlot);
        StatSheet delta = statSheet(afterItem, displaySlot + " after").minus(statSheet(beforeItem, displaySlot + " before"));
        List<StatDelta> deltas = delta.isZero() ? List.of() : summarizeStatDelta(delta, 15);

        return new SlotChangeSummary(
                displaySlot,
                previousSlot,
                currentSlot,
                changeType,
                itemName(beforeItem),
                itemName(afterItem),
                itemIcon(beforeItem),
                itemIcon(afterItem),
                deltas,
                weight(deltas)
        );
    }

    private String changeType(ItemSnapShot beforeItem, ItemSnapShot afterItem) {
        if (beforeItem == null) {
            return "ADDED";
        }
        if (afterItem == null) {
            return "REMOVED";
        }
        if (!sameItemName(beforeItem, afterItem)) {
            return "REPLACED";
        }
        return "STAT_CHANGED";
    }

    private StatSheet statSheet(ItemSnapShot itemSnapShot, String sheetName) {
        if (itemSnapShot == null || itemSnapShot.getStatSheet() == null) {
            return new StatSheet(sheetName);
        }
        return itemSnapShot.getStatSheet();
    }

    private boolean sameItemName(ItemSnapShot beforeItem, ItemSnapShot afterItem) {
        if (beforeItem == null || afterItem == null) {
            return false;
        }
        String beforeName = beforeItem.getItemName();
        String afterName = afterItem.getItemName();
        return beforeName != null && beforeName.equals(afterName);
    }

    private String itemName(ItemSnapShot itemSnapShot) {
        return itemSnapShot == null ? null : itemSnapShot.getItemName();
    }

    private String itemIcon(ItemSnapShot itemSnapShot) {
        return itemSnapShot == null ? null : itemSnapShot.getItemIcon();
    }

    private String displaySlot(String previousSlot, String currentSlot) {
        if (previousSlot == null) {
            return currentSlot;
        }
        if (currentSlot == null || previousSlot.equals(currentSlot)) {
            return previousSlot;
        }
        return previousSlot + " -> " + currentSlot;
    }

    private List<StatDelta> summarizeStatDelta(StatSheet delta, int limit) {
        List<StatDelta> deltas = new ArrayList<>();
        for (StatField statField : STAT_FIELDS) {
            double value = readNumericField(delta, statField.fieldName());
            if (Double.compare(value, 0.0) != 0) {
                deltas.add(new StatDelta(statField.label(), value));
            }
        }

        return deltas.stream()
                .sorted((left, right) -> Double.compare(Math.abs(right.delta()), Math.abs(left.delta())))
                .limit(limit)
                .toList();
    }

    private int weight(List<StatDelta> deltas) {
        return deltas.stream()
                .mapToInt(statDelta -> (int) Math.floor(Math.abs(statDelta.delta())))
                .sum();
    }

    private double readNumericField(StatSheet sheet, String fieldName) {
        try {
            Field field = StatSheet.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(sheet);
            if (value instanceof Integer integer) {
                return integer.doubleValue();
            }
            if (value instanceof Double doubleValue) {
                return doubleValue;
            }
            return 0.0;
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            return 0.0;
        }
    }

    public record CombatPresetDiff(
            LocalDate previousDate,
            LocalDate currentDate,
            long previousCombatPower,
            long currentCombatPower,
            long deltaCombatPower,
            List<ChangeSummary> coreChanges,
            List<SlotChangeSummary> petChanges,
            List<SlotChangeSummary> cashChanges,
            List<SlotChangeSummary> itemChanges
    ) {
    }

    public record ChangeSummary(
            String source,
            List<StatDelta> deltas,
            List<EntryChange> entries,
            int weight
    ) {
    }

    /** 이름 있는 항목 하나의 변화. 없던 것은 previous 가 null, 사라진 것은 current 가 null. */
    public record EntryChange(
            String name,
            String previous,
            String current,
            String icon
    ) {
    }

    public record SlotChangeSummary(
            String slot,
            String previousSlot,
            String currentSlot,
            String changeType,
            String previousItemName,
            String currentItemName,
            String previousItemIcon,
            String currentItemIcon,
            List<StatDelta> deltas,
            int weight
    ) {
    }

    public record StatDelta(
            String statName,
            double delta
    ) {
    }

    private record StatField(
            String fieldName,
            String label
    ) {
    }

    private record EquippedItem(
            String slot,
            ItemSnapShot itemSnapShot
    ) {
    }
}
