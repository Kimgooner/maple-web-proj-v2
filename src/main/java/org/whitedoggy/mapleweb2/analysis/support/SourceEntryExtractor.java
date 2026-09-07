package org.whitedoggy.mapleweb2.analysis.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.analysis.data.PresetSelection;
import org.whitedoggy.mapleweb2.domain.set.parser.SetEffectParser;
import org.whitedoggy.mapleweb2.domain.skill.SkillParser;
import org.whitedoggy.mapleweb2.domain.union.raider.RaiderParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

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

    private final SkillParser skillParser;
    private final SetEffectParser setEffectParser;
    private final RaiderParser raiderParser;

    public Map<String, Map<String, String>> extract(
            Map<NexonEndpoint, JsonNode> documents,
            JsonNode presetItems,
            PresetSelection preset,
            String characterClass,
            String worldName
    ) {
        Map<String, Map<String, String>> entries = new LinkedHashMap<>();
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

    /** 전투력 계산에 들어가는 스킬만. 파서와 같은 기준으로 거른다. */
    private Map<String, String> skills(JsonNode skill0, String worldName) {
        Map<String, String> result = new LinkedHashMap<>();
        if (skill0 == null) return result;
        for (JsonNode skill : skill0.path("character_skill")) {
            String name = Jsons.text(skill, "skill_name");
            String effect = skill.path("skill_effect").asText("");
            if (!skillParser.isCombatRelevant(name, effect, worldName)) continue;
            int level = skill.path("skill_level").asInt(0);
            // 축복·펫 버프는 레벨이 같아도 효과 수치가 달라질 수 있어 문구를 같이 싣는다.
            String summary = effect.replaceAll("\\s+", " ").trim();
            result.put(name, summary.isEmpty() ? "Lv." + level : "Lv." + level + " · " + summary);
        }
        return result;
    }

    private Map<String, String> symbols(JsonNode symbolDoc) {
        Map<String, String> result = new LinkedHashMap<>();
        if (symbolDoc == null) return result;
        for (JsonNode symbol : symbolDoc.path("symbol")) {
            result.put(Jsons.text(symbol, "symbol_name"), "Lv." + symbol.path("symbol_level").asInt(0));
        }
        return result;
    }

    private Map<String, String> hyperStats(JsonNode hyper, int presetNo) {
        Map<String, String> result = new LinkedHashMap<>();
        if (hyper == null) return result;
        for (JsonNode stat : hyper.path("hyper_stat_preset_" + presetNo)) {
            int level = stat.path("stat_level").asInt(0);
            if (level <= 0) continue;
            result.put(Jsons.text(stat, "stat_type"), "Lv." + level);
        }
        return result;
    }

    private Map<String, String> abilities(JsonNode ability, int presetNo) {
        Map<String, String> result = new LinkedHashMap<>();
        if (ability == null) return result;
        for (JsonNode line : ability.path("ability_preset_" + presetNo).path("ability_info")) {
            result.put("어빌리티 " + Jsons.text(line, "ability_no"), Jsons.text(line, "ability_value"));
        }
        return result;
    }

    private Map<String, String> setEffects(JsonNode setEffect, JsonNode presetItems, String characterClass) {
        Map<String, String> result = new LinkedHashMap<>();
        if (setEffect == null || presetItems == null) return result;
        setEffectParser.getAppliedSetCounts(setEffect, presetItems, characterClass)
                .forEach((name, count) -> result.put(name, count + "세트"));
        return result;
    }

    private Map<String, String> artifacts(JsonNode artifact) {
        Map<String, String> result = new LinkedHashMap<>();
        if (artifact == null) return result;
        for (JsonNode effect : artifact.path("union_artifact_effect")) {
            String[] split = splitNumber(Jsons.text(effect, "name"));
            result.put(split[0], split[1] + " (Lv." + effect.path("level").asInt(0) + ")");
        }
        return result;
    }

    private Map<String, String> champions(JsonNode champion) {
        Map<String, String> result = new LinkedHashMap<>();
        if (champion == null) return result;
        for (JsonNode c : champion.path("union_champion")) {
            result.put(Jsons.text(c, "champion_name"), Jsons.text(c, "champion_grade") + " · " + Jsons.text(c, "champion_class"));
        }
        return result;
    }

    private Map<String, String> hexaStats(JsonNode hexa) {
        Map<String, String> result = new LinkedHashMap<>();
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

    private void putHexa(Map<String, String> target, int coreNo, String role, String name, int level) {
        if (name.isEmpty()) return;
        target.put("코어" + coreNo + " " + role + " " + name, "Lv." + level);
    }

    /** "LUK 100 증가" 같은 효과 문구를 이름("LUK 증가")과 값("100")으로 나눠 담는다. */
    static Map<String, String> lines(List<String> texts) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String text : texts) {
            String[] split = splitNumber(text);
            result.merge(split[0], split[1], (a, b) -> a + ", " + b);
        }
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
