package org.whitedoggy.mapleweb2.analysis.support;

import org.whitedoggy.mapleweb2.domain.basic.BasicParser;
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
import org.whitedoggy.mapleweb2.domain.otherstat.OtherStatParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.whitedoggy.mapleweb2.domain.common.support.ExpiryDates;

import java.time.LocalDate;

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

    private final BasicParser basicParser;
    private final SkillParser skillParser;
    private final SetEffectParser setEffectParser;
    private final RaiderParser raiderParser;
    private final StatSheetParser statSheetParser;
    private final HexaCoreParser hexaCoreParser;
    private final OtherStatParser otherStatParser;

    /** 전투력에 반영되지 않는 기타 능력치 그룹. OtherStatParser 와 같은 기준이어야 한다. */
    private static final String OTHER_STAT_EXCLUDED_GROUP = "[제네시스 패스]";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Map<String, SourceEntry>> extract(
            Map<NexonEndpoint, JsonNode> documents,
            JsonNode presetItems,
            PresetSelection preset,
            String characterClass,
            String worldName,
            LocalDate referenceDate
    ) {
        Map<String, Map<String, SourceEntry>> entries = new LinkedHashMap<>();
        entries.put("abilityPoint", levelOf(documents.get(NexonEndpoint.BASIC)));
        entries.put("skill", skills(documents.get(NexonEndpoint.SKILL_0), worldName));
        entries.put("symbol", symbols(documents.get(NexonEndpoint.SYMBOL_EQUIPMENT)));
        entries.put("hyperStat", hyperStats(documents.get(NexonEndpoint.HYPER_STAT), preset.hyperStatPreset()));
        entries.put("ability", abilities(documents.get(NexonEndpoint.ABILITY), preset.abilityPreset()));
        entries.put("setEffect", setEffects(documents.get(NexonEndpoint.SET_EFFECT), presetItems, characterClass));
        JsonNode raider = documents.get(NexonEndpoint.UNION_RAIDER);
        entries.put("unionRaider", lines(raiderParser.getUnionRaiderStatByPreset(raider, preset.unionRaiderPreset())));
        entries.put("unionOccupied", lines(raiderParser.getUnionOccupiedStatByPreset(raider, preset.unionRaiderPreset())));
        entries.put("unionArtifact", artifacts(documents.get(NexonEndpoint.UNION_ARTIFACT), referenceDate));
        entries.put("unionChampion", champions(documents.get(NexonEndpoint.UNION_CHAMPION)));
        entries.put("hexaStat", hexaStats(documents.get(NexonEndpoint.HEXA_MATRIX_STAT)));
        entries.put("otherStat", otherStats(
                documents.get(NexonEndpoint.OTHER_STAT), documents.get(NexonEndpoint.SKILL_0)));
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

    /**
     * 심볼. 아케인 → 어센틱, 각각 지역이 열린 순서로 놓는다.
     *
     * <p>API 가 주는 차례는 지역 순이 아니다(소멸의 여로가 레헬른·아르카나 뒤에 오기도 한다).
     * 진행한 순서대로 놓아야 어디까지 올렸는지가 한눈에 읽힌다.
     *
     * <p><b>그랜드 어센틱은 뺀다.</b> 스탯을 주지 않아 전투력이 한 톨도 안 움직이는데, 값이
     * "Lv.11" 처럼 적혀 있어 레벨만 올려도 변경 항목으로 잡힌다 — 전투력이 그대로인 줄이
     * 하나 더 생길 뿐이다.
     */
    private static final List<String> SYMBOL_ORDER = List.of(
            "아케인심볼 : 소멸의 여로", "아케인심볼 : 츄츄 아일랜드", "아케인심볼 : 레헬른",
            "아케인심볼 : 아르카나", "아케인심볼 : 모라스", "아케인심볼 : 에스페라",
            "어센틱심볼 : 세르니움", "어센틱심볼 : 아르크스", "어센틱심볼 : 오디움",
            "어센틱심볼 : 도원경", "어센틱심볼 : 아르테리아", "어센틱심볼 : 카르시온");

    private static final String EXCLUDED_SYMBOL_PREFIX = "그랜드 어센틱";

    private Map<String, SourceEntry> symbols(JsonNode symbolDoc) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (symbolDoc == null) return result;

        Map<String, SourceEntry> found = new LinkedHashMap<>();
        for (JsonNode symbol : symbolDoc.path("symbol")) {
            String name = Jsons.text(symbol, "symbol_name");
            if (name.startsWith(EXCLUDED_SYMBOL_PREFIX)) {
                continue;
            }
            found.put(name, new SourceEntry(
                    "Lv." + symbol.path("symbol_level").asInt(0), iconOf(symbol, "symbol_icon")));
        }
        // 아는 지역부터 순서대로, 목록에 없는 것(새 지역이 열리면)은 받은 차례대로 뒤에 붙인다.
        for (String name : SYMBOL_ORDER) {
            SourceEntry entry = found.remove(name);
            if (entry != null) {
                result.put(name, entry);
            }
        }
        result.putAll(found);
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
        JsonNode set = findSet(setEffect, setName);
        if (set == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode info : set.path("set_effect_info")) {
            int tier = info.path("set_count").asInt(0);
            if (tier > 0 && tier <= count) {
                lines.add(tier + "세트 " + Jsons.text(info, "set_option").replaceAll("\\s+", " ").trim());
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /**
     * 문구를 담고 있는 넥슨 쪽 세트를 찾는다.
     *
     * <p>넥슨은 직업 접미사를 붙여 준다 — {@code 앱솔랩스 세트(해적)}. 우리 표로 직접 세는
     * 세트는 접미사 없는 이름({@code 앱솔랩스 세트})이라 이름이 딱 맞지 않는다. 그대로 두면
     * 그 세트만 옵션 문구가 통째로 비어, 펼쳐도 무엇이 붙는지 알 수 없다.
     *
     * <p>한 캐릭터에 같은 세트의 다른 직업판이 함께 오지는 않으므로 접두사로 찾아도 엇갈리지 않는다.
     */
    private JsonNode findSet(JsonNode setEffect, String setName) {
        JsonNode prefixMatch = null;
        for (JsonNode set : setEffect.path("set_effect")) {
            String name = Jsons.text(set, "set_name");
            if (setName.equals(name)) {
                return set;
            }
            if (prefixMatch == null && name.startsWith(setName)) {
                prefixMatch = set;
            }
        }
        return prefixMatch;
    }

    /**
     * 유니온 아티팩트. 효과는 넥슨이 합쳐 준 목록을 그대로 쓰고, 기간이 지난 크리스탈은
     * 줄을 하나 더 만들어 알린다.
     *
     * <p>만료된 크리스탈은 게임에는 그대로 꽂혀 보이지만 계산에서는 빠진다. 효과 목록에도
     * 안 나타나 아무 흔적이 없으므로, 여기서 세어 두지 않으면 "왜 갑자기 전투력이 줄었나"에
     * 답할 자리가 사라진다.
     */
    private Map<String, SourceEntry> artifacts(JsonNode artifact, LocalDate referenceDate) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (artifact == null) return result;
        for (JsonNode effect : artifact.path("union_artifact_effect")) {
            String[] split = splitNumber(Jsons.text(effect, "name"));
            result.put(split[0], SourceEntry.of(split[1] + " (Lv." + effect.path("level").asInt(0) + ")"));
        }
        int expired = expiredCrystals(artifact, referenceDate);
        if (expired > 0) {
            result.put("만료된 크리스탈", new SourceEntry(expired + "개", null, null, "만료"));
        }
        return result;
    }

    /** ArtifactParser 가 계산에서 빼는 것과 같은 기준이어야 한다. */
    private int expiredCrystals(JsonNode artifact, LocalDate referenceDate) {
        int expired = 0;
        for (JsonNode crystal : artifact.path("union_artifact_crystal")) {
            if ("1".equals(Jsons.text(crystal, "validity_flag"))
                    || ExpiryDates.isExpired(Jsons.text(crystal, "date_expire"), referenceDate)) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * AP 는 레벨이 오를 때만 늘어난다. 스탯 증감만 적으면 "AP 가 왜 늘었나"에 답이 없어,
     * 그 줄에 레벨을 담아 이전 → 이후로 읽히게 한다.
     */
    private Map<String, SourceEntry> levelOf(JsonNode basic) {
        Integer level = basicParser.characterLevel(basic);
        return level == null ? Map.of() : Map.of("레벨", SourceEntry.of("Lv." + level));
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
     * 기타 능력치 영향 요소. 챌린저스 월드의 의문의 결계, 마스터라벨 플러스가 여기로 온다.
     *
     * <p>계산이 세는 것과 같은 것만 보여준다 — 제네시스 패스는 전투력에 안 들어가므로
     * ({@link OtherStatParser} 가 통째로 건너뛴다) 여기서도 뺀다. 화면과 계산이 다르면
     * "이건 왜 안 오르지"를 설명할 길이 없어진다.
     *
     * <p>아이콘은 이 문서에 없다. 이름이 스킬에도 있는 것(마스터라벨 전투 플러스)만
     * 거기서 찾아 붙이고, 의문의 결계처럼 어디에도 없는 것은 아이콘 없이 둔다.
     */
    private Map<String, SourceEntry> otherStats(JsonNode otherStat, JsonNode skill0) {
        Map<String, SourceEntry> result = new LinkedHashMap<>();
        if (otherStat == null) {
            return result;
        }
        Map<String, String> icons = skillIcons(skill0);
        for (JsonNode group : Jsons.array(otherStat, "other_stat")) {
            String type = Jsons.text(group, "other_stat_type");
            if (type.isEmpty() || type.startsWith(OTHER_STAT_EXCLUDED_GROUP)) {
                continue;
            }
            List<String> effects = new ArrayList<>();
            for (JsonNode info : Jsons.array(group, "stat_info")) {
                effects.add(Jsons.text(info, "stat_name") + " " + Jsons.text(info, "stat_value"));
            }
            // 경험치뿐인 그룹(성장 플러스)은 파싱하면 비어 스스로 빠진다.
            String value = summarize(statSheetParser.parse(otherStatParser.getOtherStatEffects(
                    onlyGroup(group))));
            if (value.isEmpty()) {
                continue;
            }
            result.put(type, new SourceEntry(value, iconOfOtherStat(type, icons)));
        }
        return result;
    }

    /** 그룹 하나만 담은 문서 모양. 파서가 문서 단위로만 읽어서 감싸 준다. */
    private JsonNode onlyGroup(JsonNode group) {
        tools.jackson.databind.node.ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.putArray("other_stat").add(group);
        return wrapper;
    }

    /**
     * 아이콘을 빌려 오는 이름. 넥슨은 의문의 결계에 아이콘을 주지 않고, 그 이름의 스킬도 없다
     * (챌린저스 캐릭터의 0차 스킬 23개를 훑어 확인). 같은 결계 계열인 "결계의 핵 소환" 것을
     * 대신 쓴다 — 다른 스킬의 그림이지만 빈 자리로 두는 것보다 낫다고 봤다.
     */
    private static final Map<String, String> OTHER_STAT_ICON_ALIAS = Map.of("의문의 결계", "결계의 핵 소환");

    /**
     * 빌려 쓰기로 정한 이름을 먼저 보고, 없으면
     * {@code [마스터라벨 플러스] 전투 플러스} 의 뒷부분이 스킬 이름 안에 들어 있는지로 찾는다.
     */
    private String iconOfOtherStat(String type, Map<String, String> icons) {
        String bare = type.substring(type.indexOf(']') + 1).trim();
        if (bare.isEmpty()) {
            return null;
        }
        String alias = OTHER_STAT_ICON_ALIAS.get(bare);
        if (alias != null && icons.containsKey(alias)) {
            return icons.get(alias);
        }
        String packed = bare.replace(" ", "");
        for (Map.Entry<String, String> icon : icons.entrySet()) {
            if (icon.getKey().replace(" ", "").contains(packed)) {
                return icon.getValue();
            }
        }
        return null;
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
