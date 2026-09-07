package org.whitedoggy.mapleweb2.analysis.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.analysis.data.SourceEntry;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.domain.skill.SkillParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 핵심 소스(스킬·심볼·하이퍼스탯·어빌리티·세트·유니온·헥사)의 "이름 있는 항목"을 원본 문서에서 뽑는다.
 *
 * <p>스탯 시트는 합계만 남겨 "스킬 +30 마력"까지만 말할 수 있다. 두 시점을 비교할 때
 * 어떤 스킬·심볼이 바뀌었는지 이름으로 보여주려면 항목 단위 값이 있어야 한다.
 * 계산에는 쓰지 않고, {@code DataSheet.sourceEntries} 로만 실린다.
 *
 * <p>키는 {@code DataSheet} 의 시트 이름과 같다(skill, symbol, hyperStat, ability, setEffect,
 * unionRaider, unionOccupied, unionArtifact, unionChampion, hexaStat).
 */
@Component
@RequiredArgsConstructor
public class SourceEntryExtractor {
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?%?");
    private static final List<String> HEXA_CORE_FIELDS = List.of(
            "character_hexa_stat_core", "character_hexa_stat_core_2", "character_hexa_stat_core_3",
            "character_hexa_stat_core_4", "character_hexa_stat_core_5", "character_hexa_stat_core_6");

    /** 파싱된 스탯을 사람이 읽게 줄일 때의 순서와 이름. 0 이 아닌 것만 쓴다. */
    private static final List<String[]> STAT_LABELS = List.of(
            new String[]{"ATTACK_POWER", "공격력"}, new String[]{"MAGIC_POWER", "마력"},
            new String[]{"STR", "STR"}, new String[]{"DEX", "DEX"}, new String[]{"INT", "INT"}, new String[]{"LUK", "LUK"},
            new String[]{"HP", "HP"}, new String[]{"ALL_STAT", "올스탯"},
            new String[]{"STR_NO_PERCENT", "STR(고정)"}, new String[]{"DEX_NO_PERCENT", "DEX(고정)"},
            new String[]{"INT_NO_PERCENT", "INT(고정)"}, new String[]{"LUK_NO_PERCENT", "LUK(고정)"},
            new String[]{"HP_NO_PERCENT", "HP(고정)"}, new String[]{"ALL_STAT_NO_PERCENT", "올스탯(고정)"},
            new String[]{"STR_PERCENT", "STR%"}, new String[]{"DEX_PERCENT", "DEX%"}, new String[]{"INT_PERCENT", "INT%"},
            new String[]{"LUK_PERCENT", "LUK%"}, new String[]{"HP_PERCENT", "HP%"}, new String[]{"ALL_STAT_PERCENT", "올스탯%"},
            new String[]{"ATTACK_POWER_PERCENT", "공격력%"}, new String[]{"MAGIC_POWER_PERCENT", "마력%"},
            new String[]{"DAMAGE", "데미지%"}, new String[]{"BOSS_DAMAGE", "보공%"},
            new String[]{"CRITICAL_DAMAGE", "크뎀%"}, new String[]{"FINAL_DAMAGE", "최종뎀%"});

    private final SkillParser skillParser;
    private final SetEffectParser setEffectParser;
    private final RaiderParser raiderParser;
    private final StatSheetParser statSheetParser;

    public Map<String, Map<String, SourceEntry>> extract(
            Map<NexonEndpoint, JsonNode> documents,
            JsonNode presetItems,
            PresetSelection preset,
            String characterClass,
            String worldName
    ) {
        Map<String, Map<String, SourceEntry>> entries = new LinkedHashMap<>();
        entries.put("skill", skills(documents.get(NexonEndpoint.SKILL_0), worldName));
        entries.put("symbol", symbols(documents.get(NexonEndpoint.SYMBOL_EQUIPMENT)));
        entries.put("hyperStat", hyperStats(documents.get(NexonEndpoint.HYPER_STAT), preset.hyperStatPreset()));
        entries.put("ability", abilities(documents.get(NexonEndpoint.ABILITY), preset.abilityPreset()));
        entries.put("setEffect", setEffects(documents.get(NexonEndpoint.SET_EFFECT), presetItems, characterClass));
        JsonNode raider = documents.get(NexonEndpoint.UNION_RAIDER);
        entries.put("unionRaider", lines(raiderParser.getUnionRaiderStatByPreset(raider, preset.unionRaiderPreset())));
        entries.put("unionOccupied", lines(raiderParser.getUnionOccupiedStatByPreset(raider, preset.unionRaiderPreset())));
        entries.put("unionArtifact", artifacts(documents.get(NexonEndpoint.UNION_ARTIFACT)));
        entries.put("unionChampion", champions(documents.get(NexonEndpoint.UNION_CHAMPION)));
        entries.put("hexaStat", hexaStats(documents.get(NexonEndpoint.HEXA_MATRIX_STAT)));
        return entries;
    }

    /**
     * 전투력 계산에 들어가는 스킬만. 값은 넥슨 설명문이 아니라 파서가 스탯으로 바꾼 결과다
     * ("공격력 +20 · 마력 +20"). 축복·펫 버프는 레벨이 같아도 수치가 달라질 수 있어 값이 곧 비교 기준이다.
     */
    private Map<String, SourceEntry> skills(JsonNode skill0, String worldName) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (skill0 == null) return result;
        for (JsonNode skill : skill0.path("character_skill")) {
            String name = Jsons.text(skill, "skill_name");
            String effect = skill.path("skill_effect").asText("");
            List<String> effects = skillParser.parsedEffectsOf(name, effect, worldName);
            if (effects.isEmpty()) continue;
            String parsed = summarize(statSheetParser.parse(effects));
            int level = skill.path("skill_level").asInt(0);
            result.put(name, new SourceEntry(parsed.isEmpty() ? "Lv." + level : parsed, iconOf(skill, "skill_icon")));
        }
        return result;
    }

    /** 0 이 아닌 스탯만 "공격력 +20 · 보공% +40" 꼴로 줄인다. */
    static String summarize(StatSheet sheet) {
        StringBuilder out = new StringBuilder();
        for (String[] label : STAT_LABELS) {
            double value = read(sheet, label[0]);
            if (value == 0) continue;
            if (!out.isEmpty()) out.append(" · ");
            String number = value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
            out.append(label[1]).append(' ').append(value > 0 ? "+" : "").append(number);
        }
        return out.toString();
    }

    private static double read(StatSheet sheet, String fieldName) {
        try {
            Field field = StatSheet.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((Number) field.get(sheet)).doubleValue();
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    private static String iconOf(JsonNode node, String field) {
        String icon = node.path(field).asText("");
        return icon.isEmpty() ? null : icon;
    }

    private Map<String, SourceEntry> symbols(JsonNode symbolDoc) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (symbolDoc == null) return result;
        for (JsonNode symbol : symbolDoc.path("symbol")) {
            result.put(Jsons.text(symbol, "symbol_name"),
                    new SourceEntry("Lv." + symbol.path("symbol_level").asInt(0), iconOf(symbol, "symbol_icon")));
        }
        return result;
    }

    private Map<String, SourceEntry> hyperStats(JsonNode hyper, int presetNo) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (hyper == null) return result;
        for (JsonNode stat : hyper.path("hyper_stat_preset_" + presetNo)) {
            int level = stat.path("stat_level").asInt(0);
            if (level <= 0) continue;
            result.put(Jsons.text(stat, "stat_type"), SourceEntry.of("Lv." + level));
        }
        return result;
    }

    private Map<String, SourceEntry> abilities(JsonNode ability, int presetNo) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (ability == null) return result;
        for (JsonNode line : ability.path("ability_preset_" + presetNo).path("ability_info")) {
            result.put("어빌리티 " + Jsons.text(line, "ability_no"), SourceEntry.of(Jsons.text(line, "ability_value")));
        }
        return result;
    }

    private Map<String, SourceEntry> setEffects(JsonNode setEffect, JsonNode presetItems, String characterClass) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (setEffect == null || presetItems == null) return result;
        setEffectParser.getAppliedSetCounts(setEffect, presetItems, characterClass)
                .forEach((name, count) -> result.put(name, SourceEntry.of(count + "세트")));
        return result;
    }

    private Map<String, SourceEntry> artifacts(JsonNode artifact) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (artifact == null) return result;
        for (JsonNode effect : artifact.path("union_artifact_effect")) {
            String[] split = splitNumber(Jsons.text(effect, "name"));
            result.put(split[0], SourceEntry.of(split[1] + " (Lv." + effect.path("level").asInt(0) + ")"));
        }
        return result;
    }

    private Map<String, SourceEntry> champions(JsonNode champion) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (champion == null) return result;
        for (JsonNode c : champion.path("union_champion")) {
            result.put(Jsons.text(c, "champion_name"), SourceEntry.of(Jsons.text(c, "champion_grade") + " · " + Jsons.text(c, "champion_class")));
        }
        return result;
    }

    private Map<String, SourceEntry> hexaStats(JsonNode hexa) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (hexa == null) return result;
        int coreNo = 0;
        for (String field : HEXA_CORE_FIELDS) {
            JsonNode core = hexa.path(field);
            if (core.isEmpty()) continue;
            coreNo += 1;
            JsonNode stat = core.get(0);
            putHexa(result, coreNo, "주", Jsons.text(stat, "main_stat_name"), stat.path("main_stat_level").asInt(0));
            putHexa(result, coreNo, "부1", Jsons.text(stat, "sub_stat_name_1"), stat.path("sub_stat_level_1").asInt(0));
            putHexa(result, coreNo, "부2", Jsons.text(stat, "sub_stat_name_2"), stat.path("sub_stat_level_2").asInt(0));
        }
        return result;
    }

    private void putHexa(Map<String, SourceEntry> target, int coreNo, String role, String name, int level) {
        if (name.isEmpty()) return;
        target.put("코어" + coreNo + " " + role + " " + name, SourceEntry.of("Lv." + level));
    }

    /** "LUK 100 증가" 같은 효과 문구를 이름("LUK 증가")과 값("100")으로 나눠 담는다. */
    static Map<String, SourceEntry> lines(List<String> texts) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String text : texts) {
            String[] split = splitNumber(text);
            values.merge(split[0], split[1], (a, b) -> a + ", " + b);
        }
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name, SourceEntry.of(value)));
        return result;
    }

    static String[] splitNumber(String text) {
        Matcher matcher = NUMBER.matcher(text);
        StringBuilder values = new StringBuilder();
        while (matcher.find()) {
            if (!values.isEmpty()) values.append(' ');
            values.append(matcher.group());
        }
        String name = NUMBER.matcher(text).replaceAll("").replaceAll("\\s+", " ").trim();
        return new String[]{name.isEmpty() ? text : name, values.toString()};
    }
}
