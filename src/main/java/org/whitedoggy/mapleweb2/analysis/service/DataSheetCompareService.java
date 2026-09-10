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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            new StatField("FINAL_DAMAGE", "FINAL_DAMAGE"),
            // 전투력에 안 들어가는 값이라 증감 칸 맨 뒤에 붙는다(화면의 정렬 규칙이 뒤로 민다).
            new StatField("COOLDOWN_SECOND", "COOLDOWN_SECOND"),
            new StatField("COOLDOWN_SKIP_PERCENT", "COOLDOWN_SKIP_PERCENT")
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
                summarizeCoreSheets(previous, current),
                summarizeItemChanges(previous.getPetEquip(), current.getPetEquip()),
                summarizeItemChanges(previous.getCashEquip(), current.getCashEquip()),
                summarizeItemChanges(previous.getItemEquip(), current.getItemEquip())
        );
    }

    /**
     * 스탯 시트가 있는 소스들의 변화. <b>부르는 차례가 곧 화면에 놓이는 차례다.</b>
     *
     * <p>전에는 증감의 절댓값 합으로 정렬해 큰 것부터 내놨는데, 두 가지가 나빴다. 하나는
     * 그 자가 {@code 보공 +5} 와 {@code STR +720} 을 같은 무게로 재서 고정 스탯이 큰 항목이
     * 무조건 위로 온 것이다 — 전투력 기여도와 상관이 없다. 다른 하나는 구간마다 순서가
     * 바뀌어, 같은 캐릭터를 여러 구간 넘겨 볼 때 눈이 자리를 다시 찾아야 했던 것이다.
     *
     * <p>이제 중요한 것부터 고정된 차례로 놓는다. 화면의 탭 차례와 같다.
     */
    private List<ChangeSummary> summarizeCoreSheets(DataSheet previous, DataSheet current) {
        List<ChangeSummary> summaries = new ArrayList<>();
        addSheetSummary(summaries, "setEffect", previous.getSetEffect(), current.getSetEffect(), previous, current);
        addSheetSummary(summaries, "skill", previous.getSkill(), current.getSkill(), previous, current);
        addSheetSummary(summaries, "hexaStat", previous.getHexaStat(), current.getHexaStat(), previous, current);
        addEntryOnlySummary(summaries, "hexaCore", previous, current, solErdaFragmentDelta(previous, current));
        addSheetSummary(summaries, "symbol", previous.getSymbol(), current.getSymbol(), previous, current);
        addSheetSummary(summaries, "hyperStat", previous.getHyperStat(), current.getHyperStat(), previous, current);
        addSheetSummary(summaries, "ability", previous.getAbility(), current.getAbility(), previous, current);
        addSheetSummary(summaries, "unionRaider", previous.getUnionRaider(), current.getUnionRaider(), previous, current);
        addSheetSummary(summaries, "unionOccupied", previous.getUnionOccupied(), current.getUnionOccupied(), previous, current);
        addSheetSummary(summaries, "unionArtifact", previous.getUnionArtifact(), current.getUnionArtifact(), previous, current);
        addSheetSummary(summaries, "unionChampion", previous.getUnionChampion(), current.getUnionChampion(), previous, current);
        addSheetSummary(summaries, "abilityPoint", previous.getAbilityPoint(), current.getAbilityPoint(), previous, current);
        addSheetSummary(summaries, "otherStat", previous.getOtherStat(), current.getOtherStat(), previous, current);
        return List.copyOf(summaries);
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
            summaries.add(new ChangeSummary(
                    label, deltas, changes, notice(label, previous, current)));
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

    /** 넥슨 유니온 문서가 덜 와서 생긴 증감에 붙이는 말. 성장으로 읽히면 안 된다. */
    private static final String UNION_DATA_NOTICE = "유니온 업데이트 반영 X";

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
        summaries.add(new ChangeSummary(
                label, statDeltas, entryChanges(label, previous, current),
                notice(label, previous, current)));
    }

    /**
     * 이 변화가 캐릭터가 아니라 넥슨 데이터에서 온 것일 때 붙이는 말.
     *
     * <p>유니온 공격대 문서가 통째로 비어 오는 날이 있다. 그러면 공격대·점령 효과가 한꺼번에
     * 사라져 전투력이 20~30% 떨어진 것처럼 보이는데, 게임 안에서는 아무 일도 없었다.
     * 반대로 다시 채워져 오는 날은 같은 폭으로 오른 것처럼 보인다. 어느 쪽이든 성장으로
     * 읽히면 안 되므로 줄에 표를 달아 둔다. 두 소스가 같은 문서에서 나오므로 함께 본다.
     */
    private String notice(String source, DataSheet previous, DataSheet current) {
        boolean fromRaiderDocument = "unionRaider".equals(source) || "unionOccupied".equals(source);
        if (fromRaiderDocument
                && (previous.isUnionRaiderDataMissing() || current.isUnionRaiderDataMissing())) {
            return UNION_DATA_NOTICE;
        }
        // 챔피언은 반대 방향이다 - 명단이 비어 오면 배지를 못 걷어내 하루만 솟는다.
        if ("unionChampion".equals(source)
                && (previous.isUnionChampionUnverified() || current.isUnionChampionUnverified())) {
            return UNION_DATA_NOTICE;
        }
        return null;
    }

    /** 이름 있는 항목의 변화 목록. 생김·사라짐·값 변경만 남기고, 같은 것은 뺀다. */
    List<EntryChange> entryChanges(String source, DataSheet previous, DataSheet current) {
        Map<String, SourceEntry> before = entriesOf(previous, source);
        Map<String, SourceEntry> after = entriesOf(current, source);
        List<EntryChange> changes = new ArrayList<>();
        for (Map.Entry<String, SourceEntry> entry : after.entrySet()) {
            SourceEntry old = before.get(entry.getKey());
            String oldValue = old == null ? null : old.value();
            if (!entry.getValue().value().equals(oldValue)
                    && !sameSubstance(source, old, entry.getValue())) {
                changes.add(new EntryChange(entry.getKey(), oldValue, entry.getValue().value(),
                        entry.getValue().icon(),
                        old == null ? null : old.detail(), entry.getValue().detail(),
                        entry.getValue().badge()));
            }
        }
        for (Map.Entry<String, SourceEntry> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                changes.add(new EntryChange(entry.getKey(), entry.getValue().value(), null,
                        entry.getValue().icon(), entry.getValue().detail(), null,
                        entry.getValue().badge()));
            }
        }
        return changes;
    }

    /**
     * 값은 달라졌지만 실제로 붙는 것은 그대로인가.
     *
     * <p>세트효과가 그렇다. 보스 장신구처럼 두 개마다 한 단계씩 오르는 세트는 3세트 → 4세트가
     * 되어도 새로 붙는 옵션이 없다. 그때 "3세트 → 4세트" 만 적으면 전투력이 하나도 안 움직인
     * 줄이 하나 더 생긴다. 붙는 옵션 문구({@code detail})가 같으면 뺀다.
     *
     * <p>세트에만 건다. 다른 소스의 {@code detail} 은 설명이라 값과 함께 움직이지 않는다 —
     * 거기까지 넓히면 진짜 변화를 지우게 된다.
     */
    private boolean sameSubstance(String source, SourceEntry before, SourceEntry after) {
        if (!"setEffect".equals(source) || before == null) {
            return false;
        }
        // 붙는 옵션을 모르는 세트는 판단하지 않는다. 우리 표로 직접 세는 세트(앱솔랩스·아케인
        // 셰이드처럼 직업 접미사가 붙어 오는 것들)는 넥슨 문서에서 이름이 안 맞아 문구가 늘
        // null 이다. 그걸 "같다"로 보면 진짜 단계 상승까지 통째로 지워진다.
        if (before.detail() == null || after.detail() == null) {
            return false;
        }
        return before.detail().equals(after.detail());
    }

    private static Map<String, SourceEntry> entriesOf(DataSheet sheet, String source) {
        if (sheet == null || sheet.getSourceEntries() == null) {
            return Map.of();
        }
        return sheet.getSourceEntries().getOrDefault(source, Map.of());
    }

    /**
     * 장비·캐시·펫의 슬롯 변화. 게임 장비창을 훑는 차례대로 놓는다 —
     * 무기 · 보조무기 · 엠블렘 → 방어구 → 장신구 → 나머지.
     *
     * <p>정렬을 증감 크기에서 슬롯 차례로 바꾸면서 개수 제한도 뺐다. 큰 순으로 자를 때는
     * 잘려도 "작은 것이 잘렸다"였지만, 슬롯 차례로 자르면 망토가 늘 사라지고 모자만 남는다.
     * 줄은 접힌 상태로 나가므로 열 줄이 되어도 화면이 길어지지 않는다.
     */
    private List<SlotChangeSummary> summarizeItemChanges(
            Map<String, ItemSnapShot> before, Map<String, ItemSnapShot> after) {
        Map<String, ItemSnapShot> beforeItems = before == null ? Map.of() : before;
        Map<String, ItemSnapShot> afterItems = after == null ? Map.of() : after;

        List<SlotChangeSummary> changes = new ArrayList<>();
        changes.addAll(summarizeFlexibleSlotGroup(beforeItems, afterItems, RING_SLOTS));
        changes.addAll(summarizeFlexibleSlotGroup(beforeItems, afterItems, PENDANT_SLOTS));
        changes.addAll(summarizeFixedSlots(beforeItems, afterItems));

        return changes.stream()
                .sorted(Comparator.comparingInt(change -> slotRank(change.slot())))
                .toList();
    }

    /**
     * 슬롯이 놓이는 차례. 목록에 없는 슬롯(캐시·펫 슬롯, 새로 생기는 칸)은 맨 뒤에
     * 받은 차례대로 붙는다.
     */
    private static final List<String> SLOT_ORDER = List.of(
            "무기", "보조무기", "엠블렘",
            "모자", "상의", "하의", "한벌옷", "어깨장식", "장갑", "신발", "망토",
            "얼굴장식", "눈장식", "귀고리", "펜던트", "펜던트2",
            "반지1", "반지2", "반지3", "반지4", "벨트",
            "훈장", "뱃지", "칭호", "포켓 아이템", "기계 심장");

    private int slotRank(String displaySlot) {
        String bare = displaySlot == null ? "" : displaySlot.substring(displaySlot.indexOf('-') + 1).trim();
        int at = SLOT_ORDER.indexOf(bare);
        return at < 0 ? SLOT_ORDER.size() : at;
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
                itemDetail(afterItem)
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
            /** 캐릭터가 아니라 넥슨 데이터가 바뀐 것일 때 화면에 대신 적을 말. 아니면 null. */
            String notice
    ) {
    }

    /**
     * 이름 있는 항목 하나의 변화. 없던 것은 previous 가 null, 사라진 것은 current 가 null.
     *
     * @param previousDetail 이전 쪽 설명, {@code detail} 은 이후 쪽 설명이다. 세트는 구성 수만
     *                       적어서는 무엇이 어떻게 바뀌었는지 알 수 없어, 양쪽 문구를 각각 준다.
     *                       한쪽에만 있던 항목은 없는 쪽이 null 이다.
     * @param badge          이름 밑에 붙일 블럭. 헥사 코어의 종류.
     */
    public record EntryChange(
            String name,
            String previous,
            String current,
            String icon,
            String previousDetail,
            String detail,
            String badge
    ) {
        public EntryChange(String name, String previous, String current, String icon) {
            this(name, previous, current, icon, null, null, null);
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
            ItemDetail currentItem
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
