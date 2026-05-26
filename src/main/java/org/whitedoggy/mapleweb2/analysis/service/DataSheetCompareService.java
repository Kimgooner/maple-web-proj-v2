package org.whitedoggy.mapleweb2.analysis.service;

import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSnapShot;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataSheetCompareService {
    private static final String ITEM_INFO_DELIMITER = "|||";

    private static final List<StatField> STAT_FIELDS = List.of(
            new StatField("STR", "STR"),
            new StatField("DEX", "DEX"),
            new StatField("INT", "INT"),
            new StatField("LUK", "LUK"),
            new StatField("HP", "HP"),
            new StatField("ALL_STAT", "ALL_STAT"),
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
        addSheetSummary(summaries, "abilityPoint", previous.getAbilityPoint(), current.getAbilityPoint());
        addSheetSummary(summaries, "symbol", previous.getSymbol(), current.getSymbol());
        addSheetSummary(summaries, "skill", previous.getSkill(), current.getSkill());
        addSheetSummary(summaries, "hexaStat", previous.getHexaStat(), current.getHexaStat());
        addSheetSummary(summaries, "ability", previous.getAbility(), current.getAbility());
        addSheetSummary(summaries, "hyperStat", previous.getHyperStat(), current.getHyperStat());
        addSheetSummary(summaries, "setEffect", previous.getSetEffect(), current.getSetEffect());
        addSheetSummary(summaries, "unionOccupied", previous.getUnionOccupied(), current.getUnionOccupied());
        addSheetSummary(summaries, "unionRaider", previous.getUnionRaider(), current.getUnionRaider());
        addSheetSummary(summaries, "unionArtifact", previous.getUnionArtifact(), current.getUnionArtifact());
        addSheetSummary(summaries, "unionChampion", previous.getUnionChampion(), current.getUnionChampion());

        return summaries.stream()
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(limit)
                .toList();
    }

    private void addSheetSummary(List<ChangeSummary> summaries, String label, StatSheet before, StatSheet after) {
        if (before == null || after == null) {
            return;
        }

        StatSheet delta = after.minus(before);
        List<StatDelta> statDeltas = summarizeStatDelta(delta, 3);
        if (statDeltas.isEmpty()) {
            return;
        }

        summaries.add(new ChangeSummary(label, statDeltas, weight(statDeltas)));
    }

    private List<SlotChangeSummary> summarizeItemChanges(Map<String, ItemSnapShot> before, Map<String, ItemSnapShot> after, int limit) {
        if (before == null || after == null) {
            return List.of();
        }

        Map<String, ItemSnapShot> all = new LinkedHashMap<>();
        all.putAll(before);
        after.forEach(all::putIfAbsent);

        List<SlotChangeSummary> changes = new ArrayList<>();
        for (String slot : all.keySet()) {
            ItemSnapShot beforeItem = before.get(slot);
            ItemSnapShot afterItem = after.get(slot);
            StatSheet beforeSheet = beforeItem == null ? new StatSheet(slot + " before") : beforeItem.getStatSheet();
            StatSheet afterSheet = afterItem == null ? new StatSheet(slot + " after") : afterItem.getStatSheet();
            List<StatDelta> deltas = summarizeStatDelta(afterSheet.minus(beforeSheet), 15);

            if (deltas.isEmpty()) {
                continue;
            }

            changes.add(new SlotChangeSummary(
                    slot,
                    changeType(beforeItem, afterItem),
                    encodeItemInfo(beforeItem),
                    encodeItemInfo(afterItem),
                    deltas,
                    weight(deltas)
            ));
        }

        return changes.stream()
                .sorted((left, right) -> Integer.compare(right.weight(), left.weight()))
                .limit(limit)
                .toList();
    }

    private String changeType(ItemSnapShot beforeItem, ItemSnapShot afterItem) {
        if (beforeItem == null) {
            return "ADDED";
        }
        if (afterItem == null) {
            return "REMOVED";
        }
        return "CHANGED";
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

    private String encodeItemInfo(ItemSnapShot itemSnapShot) {
        if (itemSnapShot == null || itemSnapShot.getItemSheet() == null) {
            return ITEM_INFO_DELIMITER;
        }

        ItemSheet itemSheet = itemSnapShot.getItemSheet();
        String name = itemSheet.getItemName() == null ? "" : itemSheet.getItemName();
        String icon = itemSheet.getItemIcon() == null ? "" : itemSheet.getItemIcon();
        return name + ITEM_INFO_DELIMITER + icon;
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
            int weight
    ) {
    }

    public record SlotChangeSummary(
            String slot,
            String changeType,
            String previousItemName,
            String currentItemName,
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
}
