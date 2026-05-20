package org.whitedoggy.mapleweb2.analysis.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.domain.ability.AbilityParser;
import org.whitedoggy.mapleweb2.domain.hyper.HyperStatParser;
import org.whitedoggy.mapleweb2.domain.item.parser.ItemEquipmentParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import tools.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PresetSelector {
    private final ItemEquipmentParser itemEquipmentParser;
    private final AbilityParser abilityParser;
    private final HyperStatParser hyperStatParser;
    private final RaiderParser raiderParser;

    public int chooseItemPreset(JsonNode itemEquipment) {
        return itemEquipmentParser.availablePresets(itemEquipment).stream()
                .max(Comparator.<Integer>comparingInt(preset -> itemEquipmentParser.scorePreset(itemEquipmentParser.getItemEquipmentByPreset(itemEquipment, preset)))
                        .thenComparingInt(preset -> preset == itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(-1) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(itemEquipmentParser.getCurrentPresetItemEquipment(itemEquipment).orElse(1));
    }

    public int chooseAbilityPreset(JsonNode ability, String characterClass) {
        return abilityParser.availablePresets(ability).stream()
                .max(Comparator.<Integer>comparingInt(preset -> abilityParser.scorePreset(ability, preset, characterClass))
                        .thenComparingInt(preset -> preset == abilityParser.getCurrentPresetAbility(ability) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(abilityParser.getCurrentPresetAbility(ability));
    }

    public int chooseHyperPreset(JsonNode hyperStat) {
        return hyperStatParser.availablePresets(hyperStat).stream()
                .max(Comparator.<Integer>comparingInt(preset -> hyperStatParser.scorePreset(hyperStat, preset))
                        .thenComparingInt(preset -> preset == hyperStatParser.getCurrentPresetNo(hyperStat) ? 1 : 0)
                        .thenComparingInt(preset -> -preset))
                .orElse(hyperStatParser.getCurrentPresetNo(hyperStat));
    }

    public int chooseUnionPreset(JsonNode unionRaider) {
        return raiderParser.availablePresets(unionRaider).stream()
                .max(Comparator.<Integer>comparingInt(preset -> raiderParser.scorePreset(unionRaider, preset))
                        .thenComparingInt(preset -> -preset))
                .orElse(1);
    }

    public int chooseCurrentUnionPreset(JsonNode unionRaider) {
        List<String> currentRaiderStats = raiderParser.getUnionRaiderStat(unionRaider);
        List<String> currentOccupiedStats = raiderParser.getUnionOccupiedStat(unionRaider);

        return raiderParser.availablePresets(unionRaider).stream()
                .filter(preset -> currentRaiderStats.equals(raiderParser.getUnionRaiderStatByPreset(unionRaider, preset)))
                .filter(preset -> currentOccupiedStats.equals(raiderParser.getUnionOccupiedStatByPreset(unionRaider, preset)))
                .findFirst()
                .orElse(raiderParser.availablePresets(unionRaider).stream().min(Integer::compareTo).orElse(1));
    }
}
