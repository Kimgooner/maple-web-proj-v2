package org.whitedoggy.mapleweb2.domain.set.parser;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import org.whitedoggy.mapleweb2.domain.set.data.CharacterEquipmentSheet;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@lombok.RequiredArgsConstructor
public class SetEffectParser {

    private final org.whitedoggy.mapleweb2.domain.common.stat.GameData gameData;
    private static final String TABLE_PATH = "set/set-effect-table.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JsonNode setEffectTable = loadTable();

    public List<String> getSetEffectByPreset(JsonNode node, JsonNode presetItems, String characterClass) {
        List<String> effects = new ArrayList<>();
        JsonNode supportedSets = setEffectTable.path("supportedSets");
        CharacterEquipmentSheet equipped = buildEquipped(presetItems, characterClass);

        appendUnsupportedSetEffects(effects, node.path("set_effect"), supportedSets);

        JsonNode lucky = resolveActiveLuckyItem(equipped, supportedSets, characterClass);
        for (JsonNode supportedSet : supportedSets) {
            for (int pieceCount : setPieceCountsByJobGroup(equipped, supportedSet, characterClass, lucky)) {
                for (String effect : resolveOptions(
                        supportedSet.path("options"), pieceCount, characterClass, equipped, supportedSet)) {
                    EffectTextSplitter.addSplit(effects, effect);
                }
            }
        }
        return effects;
    }

    public Map<String, Integer> getAppliedSetCounts(JsonNode node, JsonNode presetItems, String characterClass) {
        Map<String, Integer> appliedSets = new LinkedHashMap<>();
        JsonNode supportedSets = setEffectTable.path("supportedSets");
        CharacterEquipmentSheet equipped = buildEquipped(presetItems, characterClass);

        for (JsonNode setEffect : node.path("set_effect")) {
            String setName = Jsons.text(setEffect, "set_name");
            if (isSupportedSet(supportedSets, setName)) {
                continue;
            }
            int count = readSetCount(setEffect);
            if (count > 0) {
                appliedSets.put(setName, count);
            }
        }

        JsonNode lucky = resolveActiveLuckyItem(equipped, supportedSets, characterClass);
        for (JsonNode supportedSet : supportedSets) {
            int pieceCount = countSetPieces(equipped, supportedSet, characterClass);
            pieceCount = applyLuckyItemBonus(equipped, supportedSet, characterClass, pieceCount, lucky);
            if (pieceCount > 0) {
                appliedSets.put(Jsons.text(supportedSet, "name"), pieceCount);
            }
        }
        return appliedSets;
    }

    private JsonNode loadTable() {
        try (InputStream inputStream = SetEffectParser.class.getClassLoader().getResourceAsStream(TABLE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Set effect table not found: " + TABLE_PATH);
            }
            return OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load set effect table: " + TABLE_PATH, exception);
        }
    }

    private CharacterEquipmentSheet buildEquipped(JsonNode presetItems, String characterClass) {
        boolean multiMainStat = gameData.mainStats(characterClass).size() > 1;
        CharacterEquipmentSheet equipped = new CharacterEquipmentSheet();
        for (JsonNode item : presetItems) {
            String slot = Jsons.text(item, "item_equipment_slot");
            String name = Jsons.text(item, "item_name");
            equipped.setItem(slot, name);
            String group = gameData.setJobGroupOf(name);
            if (group == null && multiMainStat && "무기".equals(slot)) {
                group = gameData.weaponJobGroupOf(weaponJobGroupStat(item));
            }
            equipped.setJobGroup(slot, group);
        }
        return equipped;
    }

    /**
     * 무기 직업군을 가르는 기본 스탯. 도적용 무기는 운, 해적용은 힘이 붙는다.
     *
     * <p>제논은 세 스탯을 다 쓰므로 민첩은 양쪽에 붙는다. 가르는 것은 힘과 운뿐이라
     * 둘만 비교한다(예: STR0/DEX190/LUK190 이면 도적, STR100/DEX100/LUK0 이면 해적).
     */
    private String weaponJobGroupStat(JsonNode item) {
        JsonNode base = item.path("item_base_option");
        String best = null;
        int bestValue = 0;
        for (String stat : List.of("STR", "LUK")) {
            int value = base.path(stat.toLowerCase()).asInt(0);
            if (value > bestValue) {
                bestValue = value;
                best = stat;
            }
        }
        return best;
    }

    private void appendUnsupportedSetEffects(List<String> effects, JsonNode setEffects, JsonNode supportedSets) {
        String keptCodySet = bestCashCodySet(setEffects);
        for (JsonNode setEffect : setEffects) {
            String setName = Jsons.text(setEffect, "set_name");
            if (isSupportedSet(supportedSets, setName)) {
                continue;
            }
            // 캐시 코디 세트는 둘 이상 성립해도 하나만 적용된다. 자세한 근거는 표의 cashCodySet 참고.
            if (isCashCodySet(setEffect) && !setName.equals(keptCodySet)) {
                continue;
            }
            int maxSetCount = maxSetCountOf(setName);
            for (JsonNode setInfo : setEffect.path("set_effect_info")) {
                // 존재하지 않는 단계를 API가 적용 중이라고 내려주는 세트가 있다. 표의 maxSetCount 참고.
                if (setInfo.path("set_count").asInt(0) > maxSetCount) {
                    continue;
                }
                EffectTextSplitter.addSplit(effects, Jsons.text(setInfo, "set_option"));
            }
        }
    }

    /**
     * 성립한 캐시 코디 세트 중 남길 것 하나. 세트 개수가 가장 많은 것을 고르고,
     * 같으면 이름 순으로 끊어 결과가 매번 같게 한다.
     *
     * <p>코디 세트가 하나뿐이면 그것이 곧 답이므로 동작이 바뀌지 않는다.
     */
    private String bestCashCodySet(JsonNode setEffects) {
        String best = null;
        int bestCount = -1;
        for (JsonNode setEffect : setEffects) {
            if (!isCashCodySet(setEffect)) {
                continue;
            }
            int count = readSetCount(setEffect);
            String name = Jsons.text(setEffect, "set_name");
            if (count > bestCount || (count == bestCount && best != null && name.compareTo(best) < 0)) {
                best = name;
                bestCount = count;
            }
        }
        return best;
    }

    /** 캐시 코디 세트인가. 3세트 효과 문구가 28종에서 동일해 그것으로 가른다. */
    private boolean isCashCodySet(JsonNode setEffect) {
        String signature = compact(setEffectTable.path("cashCodySet").path("threeSetOption").asText(""));
        if (signature.isEmpty()) {
            return false;
        }
        for (JsonNode setInfo : setEffect.path("set_effect_info")) {
            if (compact(Jsons.text(setInfo, "set_option")).contains(signature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 이 세트에서 실제로 존재하는 마지막 단계. 표에 없는 세트는 상한을 두지 않는다.
     *
     * <p>세트명에 직업이 붙어 오므로({@code "도전자의 장비 세트(마법사)"}) 접두사로 맞춘다.
     */
    private int maxSetCountOf(String setName) {
        for (JsonNode limit : setEffectTable.path("maxSetCount")) {
            if (setName.startsWith(Jsons.text(limit, "namePrefix"))) {
                return limit.path("max").asInt(Integer.MAX_VALUE);
            }
        }
        return Integer.MAX_VALUE;
    }

    /** 공백을 모두 지운 형태. API는 {@code "올스탯  +5"}처럼 공백을 둘씩 넣는다. */
    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private boolean isSupportedSet(JsonNode supportedSets, String setName) {
        for (JsonNode supportedSet : supportedSets) {
            if (matchesSetAliases(setName, supportedSet.path("aliases"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 세트 개수를 직업군별로 나눠 세고 럭키 아이템까지 반영한다.
     *
     * <p>같은 세트라도 직업군이 다르면 게임은 별개 세트로 취급한다. 보통은 한 직업군
     * 장비만 끼므로 그룹이 하나뿐이라 결과가 종전과 같지만, 제논은 도적용과 해적용을
     * 함께 낄 수 있어 두 세트가 동시에 성립한다.
     *
     * <p>럭키 아이템(제네시스·데스티니 무기)은 <b>세트 전체가 3개 이상</b>일 때 활성화되고,
     * 그러면 무기 부위가 빈 <b>모든</b> 그룹을 하나씩 채운다. 무기는 자기 직업군 그룹에는
     * 정식 부위로 들어가므로 그 그룹은 럭키를 다시 받지 않는다.
     *
     * <p>실측(에테르넬):
     * <ul>
     *   <li>파이렛3 + 시프1 + 해적무기 → 해적 4 / 도적 1+럭키 2 (전체 5)</li>
     *   <li>시프1 + 해적무기 → 전체 2라 럭키가 없어 도적 1 / 해적 1 (세트 효과 없음)</li>
     * </ul>
     *
     * @return 그룹별 세트 개수. 성립한 장비가 없으면 {@code [0]}.
     */
    private List<Integer> setPieceCountsByJobGroup(
            CharacterEquipmentSheet equipped, JsonNode supportedSet,
            String characterClass, JsonNode luckyItem) {
        Map<String, Integer> byGroup = new LinkedHashMap<>();
        Map<String, Boolean> luckySlotFilled = new LinkedHashMap<>();
        String luckySlot = luckyItem == null ? null : Jsons.text(luckyItem, "slot");
        int shared = 0;
        boolean sharedFillsLuckySlot = false;
        boolean weaponPieceMatched = false;

        for (JsonNode piece : supportedSet.path("pieces")) {
            String slot = Jsons.text(piece, "slot");
            if (matchedItemName(equipped, piece, supportedSet, characterClass) == null) {
                continue;
            }
            if ("무기".equals(slot)) {
                weaponPieceMatched = true;
            }
            String group = equipped.jobGroup(slot);
            if (group == null) {
                shared++;
                if (slot.equals(luckySlot)) {
                    sharedFillsLuckySlot = true;
                }
            } else {
                byGroup.merge(group, 1, Integer::sum);
                if (slot.equals(luckySlot)) {
                    luckySlotFilled.put(group, true);
                }
            }
        }
        // 럭키 아이템은 3개 이상 성립한 묶음에만 붙는다. 묶음은 직업군별로 센다.
        boolean luckyUsable = luckyItem != null
                && setUsesSlot(supportedSet, luckySlot) && !sharedFillsLuckySlot;
        boolean zeroWeaponFiller = zeroRootAbyssWeaponFills(supportedSet, equipped, characterClass)
                && !weaponPieceMatched;
        int maxPieces = supportedSet.path("pieces").size();

        // 직업군 토큰이 하나도 없는 세트(장신구 세트 등)는 종전처럼 하나로 센다.
        if (byGroup.isEmpty()) {
            boolean fill = shared >= 3 && (luckyUsable || zeroWeaponFiller);
            return List.of(Math.min(fill ? shared + 1 : shared, maxPieces));
        }

        List<Integer> counts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : byGroup.entrySet()) {
            int count = entry.getValue() + shared;
            boolean luckyFits = luckyUsable
                    && !luckySlotFilled.getOrDefault(entry.getKey(), false);
            // 무기 부위를 채우는 두 경로는 같은 자리를 두고 겹치므로 하나만 센다.
            if (count >= 3 && (luckyFits || zeroWeaponFiller)) {
                count++;
            }
            counts.add(Math.min(count, maxPieces));
        }
        return counts;
    }

    /**
     * 제로의 루타비스 무기 부위가 채워지는가.
     *
     * <p>제로는 루타비스 무기를 끼지 않아도 이글아이 상의와 트릭스터 하의를 갖추면
     * API가 무기 부위를 채운 개수를 준다. <b>단, 럭키 아이템과 같이 세트가 3개 이상
     * 성립했을 때만이다.</b> 실측(제로 638명):
     * <ul>
     *   <li>모자·상의·하의 3개 + 무기 없음 → API 4 (corona4836, 노이쿤)</li>
     *   <li>상의·하의 2개만 → API 2 (바다N, 제로은). 임계값을 빼면 4가 되어 +9~11% 과대</li>
     * </ul>
     */
    private boolean zeroRootAbyssWeaponFills(
            JsonNode supportedSet, CharacterEquipmentSheet equipped, String characterClass) {
        if (!"제로".equals(characterClass)
                || !matchesSetAliases("루타비스", supportedSet.path("aliases"))) {
            return false;
        }
        return matchesAnyItemAlias(equipped.itemsForSlot("상의"), textAliases("이글아이"))
                && matchesAnyItemAlias(equipped.itemsForSlot("하의"), textAliases("트릭스터"));
    }

    private JsonNode textAliases(String alias) {
        return OBJECT_MAPPER.createArrayNode().add(alias);
    }

    /** 이 세트를 이루는 장비들의 직업군 집합. 직업군을 가리지 않는 부위는 빠진다. */
    private java.util.Set<String> jobGroupsOf(
            CharacterEquipmentSheet equipped, JsonNode supportedSet, String characterClass) {
        java.util.Set<String> groups = new java.util.LinkedHashSet<>();
        for (JsonNode piece : supportedSet.path("pieces")) {
            String slot = Jsons.text(piece, "slot");
            if (matchedItemName(equipped, piece, supportedSet, characterClass) == null) {
                continue;
            }
            String group = equipped.jobGroup(slot);
            if (group != null) {
                groups.add(group);
            }
        }
        return groups;
    }

    /** 이 세트가 그 부위를 쓰는가. */
    private boolean setUsesSlot(JsonNode supportedSet, String slot) {
        if (slot == null) {
            return false;
        }
        for (JsonNode piece : supportedSet.path("pieces")) {
            if (slot.equals(Jsons.text(piece, "slot"))) {
                return true;
            }
        }
        return false;
    }

    /** 이 부위를 채운 장비 이름. 직업군을 못 가리는 휴리스틱 매치는 빈 문자열로 준다. */
    private String matchedItemName(CharacterEquipmentSheet equipped, JsonNode piece,
                                   JsonNode supportedSet, String characterClass) {
        String slot = Jsons.text(piece, "slot");
        for (String itemName : equipped.itemsForSlot(slot)) {
            if (matchesItemAliases(itemName, piece.path("aliases"))) {
                return itemName;
            }
        }
        return null;
    }

    private int countSetPieces(CharacterEquipmentSheet equipped, JsonNode supportedSet, String characterClass) {
        int count = 0;
        for (JsonNode piece : supportedSet.path("pieces")) {
            if (matchesSetPiece(equipped, piece, supportedSet, characterClass)) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesSetPiece(CharacterEquipmentSheet equipped, JsonNode piece, JsonNode supportedSet, String characterClass) {
        String slot = Jsons.text(piece, "slot");
        if (matchesAnyItemAlias(equipped.itemsForSlot(slot), piece.path("aliases"))) {
            return true;
        }
        return false;
    }



    /**
     * 이번 캐릭터에서 실제로 효력을 갖는 럭키 아이템 하나를 고른다.
     *
     * <p>럭키 아이템은 <b>2개 이상 착용해도 1개만 적용</b>된다. 우선순위는
     * 4카뚝 &gt; 스칼렛 이어링 &gt; 스칼렛·제네시스 무기 &gt; 스칼렛 링 &gt; 스칼렛 견장이며
     * 표의 {@code priority}에 들어 있다.
     *
     * <p>단, 우선순위가 높아도 <b>채울 빈 부위가 없으면 효력이 없다</b>. 예를 들어 데스티니 무기는
     * 에테르넬 세트에서 고정 부위라 럭키로 소모되지 않고, 다른 세트에 무기 부위가 없으면
     * 다음 순위(스칼렛 견장 등)가 대신 적용된다.
     */
    private JsonNode resolveActiveLuckyItem(
            CharacterEquipmentSheet equipped, JsonNode supportedSets, String characterClass) {
        JsonNode best = null;
        int bestPriority = Integer.MAX_VALUE;

        for (JsonNode luckyItem : setEffectTable.path("luckyItems")) {
            int priority = luckyItem.path("priority").asInt(Integer.MAX_VALUE);
            if (priority >= bestPriority) {
                continue;
            }
            if (!matchesAnyItemAlias(equipped.itemsForSlot(Jsons.text(luckyItem, "slot")),
                    luckyItem.path("aliases"))) {
                continue;
            }
            if (!fillsAnyGap(equipped, supportedSets, characterClass, luckyItem)) {
                continue;
            }
            best = luckyItem;
            bestPriority = priority;
        }
        return best;
    }

    /** 이 럭키 아이템이 채울 수 있는 빈 부위가 한 세트라도 있는가. */
    private boolean fillsAnyGap(
            CharacterEquipmentSheet equipped, JsonNode supportedSets,
            String characterClass, JsonNode luckyItem) {
        for (JsonNode supportedSet : supportedSets) {
            int pieceCount = countSetPieces(equipped, supportedSet, characterClass);
            if (pieceCount >= 3 && hasUnfilledLuckySlot(equipped, supportedSet, characterClass, luckyItem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 럭키 아이템이 채울 빈 부위가 이 세트에 있는가.
     *
     * <p>부위가 이미 찼는지는 {@link #matchesSetPiece}로 판단한다. 이름 비교만 하면
     * 제로의 루타비스 무기 휴리스틱처럼 이름 없이 채워지는 부위를 비어 있다고 보아,
     * 세트가 한 번 더 세어진다.
     */
    private boolean hasUnfilledLuckySlot(
            CharacterEquipmentSheet equipped, JsonNode supportedSet,
            String characterClass, JsonNode luckyItem) {
        String luckySlot = Jsons.text(luckyItem, "slot");
        boolean setUsesLuckySlot = false;
        for (JsonNode piece : supportedSet.path("pieces")) {
            if (!luckySlot.equals(Jsons.text(piece, "slot"))) {
                continue;
            }
            setUsesLuckySlot = true;
            if (!matchesSetPiece(equipped, piece, supportedSet, characterClass)) {
                continue;
            }
            // 그 부위를 채운 장비가 특정 직업군의 것이고 이 세트에 다른 직업군 그룹도
            // 있다면, 그쪽 그룹에는 여전히 빈 자리다(제논). 그 밖에는 이미 찬 것으로 본다.
            if (equipped.jobGroup(luckySlot) == null
                    || jobGroupsOf(equipped, supportedSet, characterClass).size() <= 1) {
                return false;
            }
        }
        return setUsesLuckySlot;
    }

    /** 3개 이상 착용 중인 세트의 빈 부위 하나를 럭키 아이템이 대신 채운다. */
    private int applyLuckyItemBonus(
            CharacterEquipmentSheet equipped, JsonNode supportedSet,
            String characterClass, int pieceCount, JsonNode luckyItem) {
        if (luckyItem == null || pieceCount < 3) {
            return pieceCount;
        }
        if (!hasUnfilledLuckySlot(equipped, supportedSet, characterClass, luckyItem)) {
            return pieceCount;
        }
        return Math.min(pieceCount + 1, supportedSet.path("pieces").size());
    }

    private boolean matchesAnyItemAlias(List<String> itemNames, JsonNode aliases) {
        for (String itemName : itemNames) {
            if (matchesItemAliases(itemName, aliases)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSetAliases(String text, JsonNode aliases) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalizedText = normalize(text);
        for (JsonNode aliasNode : aliases) {
            String alias = aliasNode.asText("");
            if (alias.isBlank()) {
                continue;
            }

            String normalizedAlias = normalize(alias);
            if (normalizedText.equals(normalizedAlias) || normalizedText.contains(normalizedAlias)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesItemAliases(String text, JsonNode aliases) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalizedText = normalize(text);
        for (JsonNode aliasNode : aliases) {
            String alias = aliasNode.asText("");
            if (alias.isBlank()) {
                continue;
            }

            String normalizedAlias = normalize(alias);
            if (normalizedText.equals(normalizedAlias) || normalizedText.startsWith(normalizedAlias)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value.replace(" ", "").trim();
    }

    private int readSetCount(JsonNode setEffect) {
        return Jsons.optionalInt(setEffect, "total_set_count")
                .or(() -> Jsons.optionalInt(setEffect, "set_count"))
                .or(() -> Jsons.optionalInt(setEffect, "current_set_count"))
                .orElse(0);
    }

    /**
     * 직업군마다 옵션이 갈리는 단계를 주스탯으로 고른다.
     *
     * <p>루타비스 2세트는 직업의 주스탯+부스탯을 주므로 도적(DEX+LUK)·마법사(INT+LUK)·
     * 그 외(STR+DEX)가 다르다. 표에 {@code {"INT": [...], "LUK": [...], "default": [...]}}
     * 형태로 두고 여기서 고른다. 배열이면 직업 분기가 없는 것이므로 그대로 쓴다.
     */
    private JsonNode selectByMainStat(JsonNode option, String characterClass,
                                      CharacterEquipmentSheet equipped, JsonNode supportedSet) {
        if (!option.isObject()) {
            return option;
        }
        List<String> mains = gameData.mainStats(characterClass);
        String main = mains.size() == 1
                ? mains.getFirst()
                : jobGroupStat(equipped, supportedSet, mains);
        if (option.has(main)) {
            return option.path(main);
        }
        return option.path("default");
    }

    /**
     * 주스탯이 여럿인 직업(제논)에서 갈래를 고른다.
     *
     * <p>제논은 STR/DEX/LUK이 모두 주스탯이라 직업으로는 정할 수 없다. 도적용(어새신)과
     * 해적용(원더러) 장비를 모두 낄 수 있고 실제로 착용한 쪽의 직업군 옵션을 받으므로,
     * 그 세트를 이루는 장비 이름으로 판단한다. 못 찾으면 첫 주스탯으로 되돌아간다.
     */
    private String jobGroupStat(CharacterEquipmentSheet equipped, JsonNode supportedSet,
                                List<String> mains) {
        for (JsonNode piece : supportedSet.path("pieces")) {
            for (String itemName : equipped.itemsForSlot(Jsons.text(piece, "slot"))) {
                String stat = gameData.jobGroupStatOf(itemName);
                if (stat != null) {
                    return stat;
                }
            }
        }
        return mains.isEmpty() ? "" : mains.getFirst();
    }

    private List<String> resolveOptions(JsonNode options, int pieceCount, String characterClass,
                                        CharacterEquipmentSheet equipped, JsonNode supportedSet) {
        if (!(options instanceof ObjectNode objectNode) || pieceCount <= 0) {
            return List.of();
        }

        List<String> effects = new ArrayList<>();
        List<Integer> thresholds = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
            String field = entry.getKey();
            try {
                int threshold = Integer.parseInt(field);
                if (threshold <= pieceCount) {
                    thresholds.add(threshold);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        thresholds.stream()
                .sorted(Comparator.naturalOrder())
                .forEach(threshold -> effects.addAll(
                        readOptionTexts(selectByMainStat(
                                options.path(String.valueOf(threshold)), characterClass,
                                equipped, supportedSet))));
        return effects;
    }

    private List<String> readOptionTexts(JsonNode node) {
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        if (node.isArray()) {
            List<String> effects = new ArrayList<>();
            for (JsonNode child : node) {
                if (child.isTextual()) {
                    effects.add(child.asText());
                } else if (child.isObject()) {
                    String option = Jsons.text(child, "set_option");
                    if (!option.isBlank()) {
                        effects.add(option);
                    }
                }
            }
            return effects;
        }
        if (node.isObject()) {
            String option = Jsons.text(node, "set_option");
            if (!option.isBlank()) {
                return List.of(option);
            }
            JsonNode options = node.path("set_options");
            if (options.isArray()) {
                return readOptionTexts(options);
            }
        }
        return List.of();
    }
}
