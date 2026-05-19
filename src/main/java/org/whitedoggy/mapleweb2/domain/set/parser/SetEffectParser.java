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
public class SetEffectParser {
    private static final String TABLE_PATH = "set/set-effect-table.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JsonNode setEffectTable = loadTable();

    public List<String> getSetEffectByPreset(JsonNode node, JsonNode presetItems, String characterClass) {
        List<String> effects = new ArrayList<>();
        JsonNode supportedSets = setEffectTable.path("supportedSets");
        CharacterEquipmentSheet equipped = buildEquipped(presetItems);

        appendUnsupportedSetEffects(effects, node.path("set_effect"), supportedSets);

        for (JsonNode supportedSet : supportedSets) {
            int pieceCount = countSetPieces(equipped, supportedSet, characterClass);
            pieceCount = applyLuckyItemBonus(equipped, supportedSet.path("pieces"), pieceCount);
            for (String effect : resolveOptions(supportedSet.path("options"), pieceCount)) {
                EffectTextSplitter.addSplit(effects, effect);
            }
        }
        return effects;
    }

    public Map<String, Integer> getAppliedSetCounts(JsonNode node, JsonNode presetItems, String characterClass) {
        Map<String, Integer> appliedSets = new LinkedHashMap<>();
        JsonNode supportedSets = setEffectTable.path("supportedSets");
        CharacterEquipmentSheet equipped = buildEquipped(presetItems);

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

        for (JsonNode supportedSet : supportedSets) {
            int pieceCount = countSetPieces(equipped, supportedSet, characterClass);
            pieceCount = applyLuckyItemBonus(equipped, supportedSet.path("pieces"), pieceCount);
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

    private CharacterEquipmentSheet buildEquipped(JsonNode presetItems) {
        CharacterEquipmentSheet equipped = new CharacterEquipmentSheet();
        for (JsonNode item : presetItems) {
            equipped.setItem(Jsons.text(item, "item_equipment_slot"), Jsons.text(item, "item_name"));
        }
        return equipped;
    }

    private void appendUnsupportedSetEffects(List<String> effects, JsonNode setEffects, JsonNode supportedSets) {
        for (JsonNode setEffect : setEffects) {
            if (isSupportedSet(supportedSets, Jsons.text(setEffect, "set_name"))) {
                continue;
            }
            for (JsonNode setInfo : setEffect.path("set_effect_info")) {
                EffectTextSplitter.addSplit(effects, Jsons.text(setInfo, "set_option"));
            }
        }
    }

    private boolean isSupportedSet(JsonNode supportedSets, String setName) {
        for (JsonNode supportedSet : supportedSets) {
            if (matchesSetAliases(setName, supportedSet.path("aliases"))) {
                return true;
            }
        }
        return false;
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
        return isZeroRootAbyssWeaponHeuristic(slot, piece.path("aliases"), supportedSet, equipped, characterClass);
    }

    private boolean isZeroRootAbyssWeaponHeuristic(
            String slot,
            JsonNode aliases,
            JsonNode supportedSet,
            CharacterEquipmentSheet equipped,
            String characterClass
    ) {
        if (!"제로".equals(characterClass) || !"무기".equals(slot)) {
            return false;
        }
        if (!matchesSetAliases(Jsons.text(supportedSet, "name"), supportedSet.path("aliases"))
                || !matchesSetAliases("루타비스", supportedSet.path("aliases"))) {
            return false;
        }

        boolean hasTop = matchesAnyItemAlias(equipped.itemsForSlot("상의"), textAliases("이글아이"));
        boolean hasBottom = matchesAnyItemAlias(equipped.itemsForSlot("하의"), textAliases("트릭스터"));
        return hasTop && hasBottom && matchesItemAliases("파프니르", aliases);
    }

    private JsonNode textAliases(String alias) {
        return OBJECT_MAPPER.createArrayNode().add(alias);
    }

    private int applyLuckyItemBonus(CharacterEquipmentSheet equipped, JsonNode pieces, int pieceCount) {
        if (pieceCount < 3) {
            return pieceCount;
        }

        for (JsonNode luckyItem : setEffectTable.path("luckyItems")) {
            String luckySlot = Jsons.text(luckyItem, "slot");
            if (!matchesAnyItemAlias(equipped.itemsForSlot(luckySlot), luckyItem.path("aliases"))) {
                continue;
            }

            boolean setUsesLuckySlot = false;
            boolean alreadyMatched = false;
            for (JsonNode piece : pieces) {
                if (!luckySlot.equals(Jsons.text(piece, "slot"))) {
                    continue;
                }
                setUsesLuckySlot = true;
                if (matchesAnyItemAlias(equipped.itemsForSlot(luckySlot), piece.path("aliases"))) {
                    alreadyMatched = true;
                    break;
                }
            }

            if (setUsesLuckySlot && !alreadyMatched) {
                return Math.min(pieceCount + 1, pieces.size());
            }
        }
        return pieceCount;
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

    private List<String> resolveOptions(JsonNode options, int pieceCount) {
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
                .forEach(threshold -> effects.addAll(readOptionTexts(options.path(String.valueOf(threshold)))));
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
