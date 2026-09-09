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
import org.whitedoggy.mapleweb2.domain.hexa.HexaCoreParser;
import org.whitedoggy.mapleweb2.global.Jsons;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
    private final HexaCoreParser hexaCoreParser;

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
        entries.put("hexaCore", hexaCores(
                documents.get(NexonEndpoint.HEXA_MATRIX), documents.get(NexonEndpoint.SKILL_6)));
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
                .forEach((name, count) -> result.put(
                        name, new SourceEntry(count + "세트", null, setOptions(setEffect, name, count))));
        return result;
    }

    /**
     * 지금 적용 중인 단계까지의 세트 효과 문구. 세트는 낮은 단계 효과가 그대로 쌓이므로
     * {@code count} 이하 단계를 모두 모은다. 값이 아니라 설명이라 변화 판정에는 쓰이지 않는다.
     */
    private String setOptions(JsonNode setEffect, String setName, int count) {
        List<String> lines = new ArrayList<>();
        for (JsonNode set : setEffect.path("set_effect")) {
            if (!setName.equals(Jsons.text(set, "set_name"))) {
                continue;
            }
            for (JsonNode info : set.path("set_effect_info")) {
                int tier = info.path("set_count").asInt(0);
                if (tier > 0 && tier <= count) {
                    lines.add(tier + "세트 " + Jsons.text(info, "set_option").replaceAll("\\s+", " ").trim());
                }
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
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

    /**
     * 헥사 코어(스킬 강화). 코어 하나가 한 항목이고, 값은 종류·레벨·누적 조각이다.
     *
     * <p>헥사 강화는 전투력에 잡히지 않아 증감 칸이 늘 빈다. 대신 조각을 값에 넣어
     * 그 구간에 6차로 무엇을 얼마나 올렸는지가 이전·이후 표에서 바로 읽히게 한다.
     *
     * <p>아이콘은 코어 문서에 없다. 6차 스킬 문서에서 걸린 스킬 이름으로 찾아 붙인다.
     */
    private Map<String, SourceEntry> hexaCores(JsonNode hexa, JsonNode skill6) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        Map<String, String> icons = skillIcons(skill6);
        for (HexaCoreParser.Core core : hexaCoreParser.cores(hexa)) {
            String value = "Lv." + core.level()
                    + (core.spent() > 0 ? " · 조각 " + String.format("%,d", core.spent()) : "");
            result.put(core.name(), new SourceEntry(value, iconOfCore(core, icons), null, core.type()));
        }
        return result;
    }

    private Map<String, String> skillIcons(JsonNode skill6) {
        Map<String, String> icons = new LinkedHashMap<>();
        if (skill6 == null) {
            return icons;
        }
        for (JsonNode skill : skill6.path("character_skill")) {
            String icon = Jsons.text(skill, "skill_icon");
            if (!icon.isEmpty()) {
                icons.put(Jsons.text(skill, "skill_name"), icon);
            }
        }
        return icons;
    }

    /** 마스터리 코어는 이름이 "A/B/C" 로 오므로 걸린 스킬 쪽에서 먼저 찾는다. */
    private String iconOfCore(HexaCoreParser.Core core, Map<String, String> icons) {
        for (String skill : core.skills()) {
            String icon = icons.get(skill);
            if (icon != null) {
                return icon;
            }
        }
        return icons.get(core.name());
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
            List<String> lines = new ArrayList<>();
            addHexaLine(lines, "주옵션", Jsons.text(stat, "main_stat_name"), stat.path("main_stat_level").asInt(0));
            // 부옵션은 1·2 를 가르지 않는다. 자리 번호는 코어가 무엇인지와 아무 상관이 없다.
            addHexaLine(lines, "부옵션", Jsons.text(stat, "sub_stat_name_1"), stat.path("sub_stat_level_1").asInt(0));
            addHexaLine(lines, "부옵션", Jsons.text(stat, "sub_stat_name_2"), stat.path("sub_stat_level_2").asInt(0));
            if (!lines.isEmpty()) {
                result.put("헥사 스탯" + roman(coreNo), SourceEntry.of(String.join("\n", lines)));
            }
        }
        return result;
    }

    /** 헥사 스탯 코어는 게임에서 로마 숫자로 부른다. 표 밖으로 나가면 아라비아 숫자로 되돌린다. */
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI"};

    private String roman(int coreNo) {
        return coreNo >= 1 && coreNo <= ROMAN.length ? ROMAN[coreNo - 1] : String.valueOf(coreNo);
    }

    /**
     * 한 코어의 주·부옵션을 한 항목에 줄로 담는다.
     *
     * <p>따로 두면 코어 하나를 갈아 끼웠을 때 세 줄이 각각 바뀐 것처럼 나온다.
     * 값에 줄바꿈이 들어가므로 변화 판정도 코어 단위로 한 번에 이뤄진다.
     */
    private void addHexaLine(List<String> lines, String role, String name, int level) {
        if (!name.isEmpty()) {
            lines.add(role + " " + name + " Lv." + level);
        }
    }

    /**
     * "LUK 100 증가" 같은 효과 문구를 이름("LUK 증가")과 값("100")으로 나눠 담는다.
     *
     * <p>유니온 공격대는 같은 효과가 여러 번 온다 — 한 캐릭터에서 "INT 100 증가"가 셋,
     * "INT 80 증가"가 하나 오는 식이다. 그대로 늘어놓으면 읽을 수 없어 합친다.
     */
    static Map<String, SourceEntry> lines(List<String> texts) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String text : texts) {
            String[] split = splitNumber(text);
            grouped.computeIfAbsent(split[0], name -> new ArrayList<>()).add(split[1]);
        }
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        grouped.forEach((name, values) -> result.put(name, SourceEntry.of(combine(values))));
        return result;
    }

    /**
     * 같은 이름으로 묶인 값들을 하나로. 더할 수 있는 것끼리 더한다.
     *
     * <p>%와 고정값은 따로 더한다 — "최대 HP 5% 증가"와 "최대 HP 2000 증가"는 숫자를
     * 지우면 이름이 같아지지만 더하면 거짓말이 되므로 {@code "5%, 4000"} 처럼 나눠 적는다.
     * 한 줄에 숫자가 둘 이상인 문구("공격 시 20%의 확률로 데미지 20% 증가")는 무엇을
     * 더할지 알 수 없어 손대지 않고 그대로 늘어놓는다.
     */
    private static String combine(List<String> values) {
        if (values.size() == 1) {
            return values.get(0);
        }
        List<String> order = new ArrayList<>();
        Map<Boolean, Double> sums = new LinkedHashMap<>();
        for (String value : values) {
            if (value.isBlank() || value.contains(" ")) {
                return String.join(", ", values);
            }
            boolean percent = value.endsWith("%");
            double number;
            try {
                number = Double.parseDouble(percent ? value.substring(0, value.length() - 1) : value);
            } catch (NumberFormatException ignored) {
                return String.join(", ", values);
            }
            String kind = percent ? "%" : "";
            if (!order.contains(kind)) order.add(kind);
            sums.merge(percent, number, Double::sum);
        }
        StringBuilder combined = new StringBuilder();
        for (String kind : order) {
            if (!combined.isEmpty()) combined.append(", ");
            combined.append(trimZero(sums.get("%".equals(kind)))).append(kind);
        }
        return combined.toString();
    }

    /** 정수면 소수점을 붙이지 않는다. 유니온은 대개 정수다. */
    private static String trimZero(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
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
