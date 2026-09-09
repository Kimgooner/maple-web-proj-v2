package org.whitedoggy.mapleweb2.analysis.service;

import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;
import org.whitedoggy.mapleweb2.domain.item.data.ItemStatLine;

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
        addSheetSummary(summaries, "otherStat", previous.getOtherStat(), current.getOtherStat(), previous, current);
        addEntryOnlySummary(summaries, "hexaCore", previous, current, solErdaFragmentDelta(previous, current));
        addSheetSummary(summaries, "unionChampion", previous.getUnionChampion(), current.getUnionChampion(), previous, current);

        return summaries.stream()
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(limit)
                .toList();
    }

    /**
     * 스탯 시트가 없는 소스. 헥사 코어 강화는 전투력에 한 톨도 안 들어가 시트 자체가 없고,
     * {@link #addSheetSummary} 는 델타가 0 이면 줄을 만들지 않는다. 이름 있는 항목이
     * 바뀐 것만으로 한 줄을 세운다.
     */
    private void addEntryOnlySummary(List<ChangeSummary> summaries, String label,
                                     DataSheet previous, DataSheet current, List<StatDelta> deltas) {
        List<EntryChange> changes = entryChanges(label, previous, current);
        if (!changes.isEmpty()) {
            summaries.add(new ChangeSummary(label, deltas, changes, weight(deltas)));
        }
    }

    /**
     * 그 구간에 6차로 쓴 솔 에르다 조각. 헥사 강화는 전투력에 안 잡혀 증감 칸이 늘 비는데,
     * 정작 사람이 궁금한 건 "얼마나 부었나"다. 스탯 자리에 조각을 넣어 그 칸을 채운다.
     */
    private List<StatDelta> solErdaFragmentDelta(DataSheet previous, DataSheet current) {
        Long before = previous.getSolErdaFragments();
        Long after = current.getSolErdaFragments();
        if (before == null || after == null || before.equals(after)) {
            return List.of();
        }
        return List.of(new StatDelta("SOL_ERDA_FRAGMENT", after - before));
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

        List<StatDelta> statDeltas = summarizeStatDelta(delta);
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
                changes.add(new EntryChange(entry.getKey(), oldValue, entry.getValue().value(),
                        entry.getValue().icon(), entry.getValue().detail(), entry.getValue().badge()));
            }
        }
        for (Map.Entry<String, SourceEntry> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                changes.add(new EntryChange(entry.getKey(), entry.getValue().value(), null,
                        entry.getValue().icon(), entry.getValue().detail(), entry.getValue().badge()));
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
            if (FLEXIBLE_ITEM_SLOTS.contains(bareSlot(slot))) {
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
            for (Map.Entry<String, ItemSnapShot> entry : itemsBySlot.entrySet()) {
                if (slot.equals(bareSlot(entry.getKey())) && entry.getValue() != null) {
                    items.add(new EquippedItem(entry.getKey(), entry.getValue()));
                }
            }
        }
        return items;
    }

    /**
     * {@code itemEquip} 의 키는 {@code "장비 - 반지1"} 처럼 접두사가 붙어 온다. 뒤쪽 부위명만 뽑는다.
     *
     * <p>화면에 내보내는 슬롯 값은 접두사가 붙은 원래 키 그대로 둔다 — 프런트가 그걸 기대한다.
     * 여기서 벗기는 것은 어느 부위인지 <b>판정</b>할 때뿐이다.
     */
    private static String bareSlot(String key) {
        int separator = key.lastIndexOf(" - ");
        return separator < 0 ? key : key.substring(separator + 3);
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
        List<StatDelta> deltas = delta.isZero() ? List.of() : summarizeStatDelta(delta);

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
                deltaGroups(displaySlot, beforeItem, afterItem),
                itemDetail(beforeItem),
                itemDetail(afterItem),
                weight(deltas)
        );
    }

    /**
     * 아이템에 붙어 있는 효과를 원문 그대로, 종류별로 묶어 준다.
     *
     * <p>교체는 증감만 보면 무엇이 빠지고 무엇이 붙었는지 알 수 없다 — 잠재 한 줄이 여러
     * 스탯으로 흩어지기 때문이다. 화면이 두 아이템을 나란히 놓고 읽을 수 있게 원문을 보낸다.
     */
    private ItemDetail itemDetail(ItemSnapShot item) {
        if (item == null) {
            return null;
        }
        return new ItemDetail(
                item.getItemName(),
                item.getItemIcon(),
                item.getStarForce(),
                item.getScrollUpgrade(),
                item.getRequiredLevel(),
                item.getP_grade(),
                item.getAp_grade(),
                item.getExpired(),
                nullToEmpty(item.getStatLines()),
                nullToEmpty(item.getDescriptionLines()),
                nullToEmpty(item.getPotentialLines()),
                nullToEmpty(item.getAdditionalPotentialLines()),
                nullToEmpty(item.getExceptionalLines())
        );
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * 변화를 옵션 / 잠재 / 익셉셔널로 나눈다. 장비 하나가 스탯 열 개를 바꾸면 한 줄로
     * 늘어놓는 것보다 무엇이 바뀐 건지가 드러난다.
     *
     * <p>옵션 몫은 따로 담고 다니지 않고 전체에서 잠재·익셉셔널을 빼서 만든다.
     * 효과 문구를 줄 단위로 더하는 파싱이라 이 뺄셈이 정확하다.
     */
    private List<StatDeltaGroup> deltaGroups(String displaySlot, ItemSnapShot beforeItem, ItemSnapShot afterItem) {
        StatSheet potential = categoryDelta(displaySlot, beforeItem, afterItem, ItemSnapShot::getPotentialStatSheet);
        StatSheet exceptional = categoryDelta(displaySlot, beforeItem, afterItem, ItemSnapShot::getExceptionalStatSheet);
        StatSheet option = statSheet(afterItem, displaySlot).minus(statSheet(beforeItem, displaySlot))
                .minus(potential)
                .minus(exceptional);

        List<StatDeltaGroup> groups = new ArrayList<>();
        addGroup(groups, "옵션", option);
        addGroup(groups, "잠재", potential);
        addGroup(groups, "익셉셔널", exceptional);
        return groups;
    }

    private StatSheet categoryDelta(
            String displaySlot,
            ItemSnapShot beforeItem,
            ItemSnapShot afterItem,
            java.util.function.Function<ItemSnapShot, StatSheet> category
    ) {
        StatSheet before = beforeItem == null ? null : category.apply(beforeItem);
        StatSheet after = afterItem == null ? null : category.apply(afterItem);
        return orEmpty(after, displaySlot).minus(orEmpty(before, displaySlot));
    }

    private static StatSheet orEmpty(StatSheet sheet, String sheetName) {
        return sheet == null ? new StatSheet(sheetName) : sheet;
    }

    private void addGroup(List<StatDeltaGroup> groups, String category, StatSheet delta) {
        if (delta.isZero()) {
            return;
        }
        groups.add(new StatDeltaGroup(category, summarizeStatDelta(delta)));
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

    /**
     * 0 이 아닌 스탯을 모두 준다. 자르지 않는다 — 무엇을 보여줄지와 어떤 순서로 놓을지는
     * 직업을 아는 화면이 정한다. 여기서 셋만 남기면 화면이 되살릴 방법이 없다.
     */
    private List<StatDelta> summarizeStatDelta(StatSheet delta) {
        List<StatDelta> deltas = new ArrayList<>();
        for (StatField statField : STAT_FIELDS) {
            double value = readNumericField(delta, statField.fieldName());
            if (Double.compare(value, 0.0) != 0) {
                deltas.add(new StatDelta(statField.label(), value));
            }
        }
        return List.copyOf(deltas);
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
            String icon,
            /** 펼쳐 봤을 때 보여줄 여러 줄 설명. 지금 상태 기준이고, 사라진 항목이면 이전 것. */
            String detail,
            /** 이름 밑에 붙일 블럭. 헥사 코어의 종류. */
            String badge
    ) {
        public EntryChange(String name, String previous, String current, String icon) {
            this(name, previous, current, icon, null, null);
        }
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
            /** 옵션 / 잠재 / 익셉셔널로 나눈 변화. 비어 있는 종류는 빠진다. */
            List<StatDeltaGroup> deltaGroups,
            /** 게임 아이템 창에 나오는 것들. 교체를 펼쳐 나란히 읽는다. 빈 자리면 null. */
            ItemDetail previousItem,
            ItemDetail currentItem,
            int weight
    ) {
    }

    /**
     * 게임 아이템 창 한 장. 잠재·에디셔널·익셉셔널은 넥슨이 준 문구 그대로다 —
     * 증감으로 바꿔 적으면 한 줄이 여러 스탯으로 흩어져 무엇이 붙어 있었는지가 사라진다.
     */
    public record ItemDetail(
            String name,
            String icon,
            Integer starForce,
            Integer scrollUpgrade,
            Integer requiredLevel,
            String potentialGrade,
            String additionalPotentialGrade,
            /** 기간이 지나 스탯이 빠졌으면 그 사유. 아직 살아 있으면 null. */
            String expired,
            List<ItemStatLine> stats,
            /** 설명문이 곧 스탯인 것들(칭호). 스탯 줄이 없을 때 이 자리가 채워진다. */
            List<String> descriptionLines,
            List<String> potentialLines,
            List<String> additionalPotentialLines,
            List<String> exceptionalLines
    ) {
    }

    /** 한 종류의 스탯 변화. {@code category} 는 화면이 그대로 제목으로 쓴다. */
    public record StatDeltaGroup(String category, List<StatDelta> deltas) {
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
