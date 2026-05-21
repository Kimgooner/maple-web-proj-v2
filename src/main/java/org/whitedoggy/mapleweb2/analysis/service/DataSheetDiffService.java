package org.whitedoggy.mapleweb2.analysis.service;

import org.springframework.stereotype.Service;
import org.whitedoggy.mapleweb2.analysis.data.DataSheet;
import org.whitedoggy.mapleweb2.domain.item.data.ItemSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataSheetDiffService {
    private static final List<StatField> STAT_FIELDS = List.of(
            new StatField("STR", "STR"),
            new StatField("DEX", "DEX"),
            new StatField("INT", "INT"),
            new StatField("LUK", "LUK"),
            new StatField("HP", "HP"),
            new StatField("ALL_STAT", "올스탯"),
            new StatField("STR_NO_PERCENT", "STR 고정"),
            new StatField("DEX_NO_PERCENT", "DEX 고정"),
            new StatField("INT_NO_PERCENT", "INT 고정"),
            new StatField("LUK_NO_PERCENT", "LUK 고정"),
            new StatField("HP_NO_PERCENT", "HP 고정"),
            new StatField("ALL_STAT_NO_PERCENT", "올스탯 고정"),
            new StatField("ATTACK_POWER", "공격력"),
            new StatField("MAGIC_POWER", "마력"),
            new StatField("STR_PERCENT", "STR%"),
            new StatField("DEX_PERCENT", "DEX%"),
            new StatField("INT_PERCENT", "INT%"),
            new StatField("LUK_PERCENT", "LUK%"),
            new StatField("HP_PERCENT", "HP%"),
            new StatField("ALL_STAT_PERCENT", "올스탯%"),
            new StatField("ATTACK_POWER_PERCENT", "공격력%"),
            new StatField("MAGIC_POWER_PERCENT", "마력%"),
            new StatField("DAMAGE", "데미지%"),
            new StatField("BOSS_DAMAGE", "보스 데미지%"),
            new StatField("CRITICAL_DAMAGE", "크리티컬 데미지%"),
            new StatField("FINAL_DAMAGE", "최종 데미지%")
    );

    public List<CombatPresetDiff> diffCombatPresetSeries(List<DataSheet> dataSheets) {
        List<DataSheet> sorted = dataSheets.stream()
                .filter(dataSheet -> dataSheet != null)
                .sorted(Comparator.comparing(DataSheet::getDate))
                .toList();

        List<CombatPresetDiff> diffs = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            diffs.add(diff(sorted.get(i - 1), sorted.get(i)));
        }
        return diffs;
    }

    public CombatPresetDiff diff(DataSheet previous, DataSheet current) {
        long previousCombatPower = previous.getCurrentCombatPower();
        long currentCombatPower = current.getCurrentCombatPower();
        long deltaCombatPower = currentCombatPower - previousCombatPower;

        return new CombatPresetDiff(
                previous.getDate(),
                current.getDate(),
                previousCombatPower,
                currentCombatPower,
                deltaCombatPower,
                summarizeCoreSheets(previous, current, 6),
                summarizeItemChanges(previous.getPetEquip(), current.getPetEquip(), 3),
                summarizeItemChanges(previous.getCashEquip(), current.getCashEquip(), 3),
                summarizeItemChanges(previous.getItemEquip(), current.getItemEquip(), 5)
        );
    }

    private List<ChangeSummary> summarizeCoreSheets(DataSheet previous, DataSheet current, int limit) {
        List<ChangeSummary> summaries = new ArrayList<>();
        addSheetSummary(summaries, "어빌리티 포인트", previous.getAbilityPoint(), current.getAbilityPoint());
        addSheetSummary(summaries, "심볼", previous.getSymbol(), current.getSymbol());
        addSheetSummary(summaries, "스킬", previous.getSkill(), current.getSkill());
        addSheetSummary(summaries, "헥사 스탯", previous.getHexaStat(), current.getHexaStat());
        addSheetSummary(summaries, "어빌리티", previous.getAbility(), current.getAbility());
        addSheetSummary(summaries, "하이퍼 스탯", previous.getHyperStat(), current.getHyperStat());
        addSheetSummary(summaries, "세트 효과", previous.getSetEffect(), current.getSetEffect());
        addSheetSummary(summaries, "유니온 점령 효과", previous.getUnionOccupied(), current.getUnionOccupied());
        addSheetSummary(summaries, "유니온 공격대원 효과", previous.getUnionRaider(), current.getUnionRaider());
        addSheetSummary(summaries, "유니온 아티팩트", previous.getUnionArtifact(), current.getUnionArtifact());
        addSheetSummary(summaries, "유니온 챔피언", previous.getUnionChampion(), current.getUnionChampion());

        return summaries.stream()
                .sorted(Comparator.comparingInt(ChangeSummary::weight).reversed())
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

        summaries.add(new ChangeSummary(
                label,
                statDeltas,
                statDeltas.stream().mapToInt(statDelta -> (int) Math.floor(Math.abs(statDelta.delta()))).sum()
        ));
    }

    private List<SlotChangeSummary> summarizeItemChanges(Map<String, ItemSheet> before, Map<String, ItemSheet> after, int limit) {
        if (before == null || after == null) {
            return List.of();
        }

        Map<String, ItemSheet> all = new LinkedHashMap<>();
        all.putAll(before);
        after.forEach(all::putIfAbsent);

        List<SlotChangeSummary> changes = new ArrayList<>();
        for (String slot : all.keySet()) {
            ItemSheet beforeItem = before.get(slot);
            ItemSheet afterItem = after.get(slot);

            if (beforeItem == null && afterItem != null) {
                changes.add(new SlotChangeSummary(slot, "새 장착", null, afterItem.getItemName(), List.of(), 1000));
                continue;
            }
            if (beforeItem != null && afterItem == null) {
                changes.add(new SlotChangeSummary(slot, "장착 해제", beforeItem.getItemName(), null, List.of(), 1000));
                continue;
            }
            if (beforeItem == null) {
                continue;
            }

            String beforeName = beforeItem.getItemName();
            String afterName = afterItem.getItemName();
            if (!safeEquals(beforeName, afterName)) {
                List<StatDelta> deltas = summarizeStatDelta(afterItem.getStatSheet().minus(beforeItem.getStatSheet()), 3);
                changes.add(new SlotChangeSummary(slot, "아이템 교체", beforeName, afterName, deltas, 1000 + weight(deltas)));
                continue;
            }

            List<StatDelta> deltas = summarizeStatDelta(afterItem.getStatSheet().minus(beforeItem.getStatSheet()), 2);
            if (!deltas.isEmpty()) {
                changes.add(new SlotChangeSummary(slot, "수치 변화", beforeName, afterName, deltas, weight(deltas)));
            }
        }

        return changes.stream()
                .sorted(Comparator.comparingInt(SlotChangeSummary::weight).reversed())
                .limit(limit)
                .toList();
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
                .sorted(Comparator.comparingDouble((StatDelta statDelta) -> Math.abs(statDelta.delta())).reversed())
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

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
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
