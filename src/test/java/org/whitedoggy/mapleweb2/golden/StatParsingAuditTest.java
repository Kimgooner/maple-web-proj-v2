package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 옵션 텍스트 파싱 감사.
 *
 * <p>같은 스탯이 여러 문구로 오기 때문에("보스 몬스터 데미지 30%" / "보스 몬스터 공격 시 데미지 30%")
 * 놓치는 표현이 없는지, 반대로 엉뚱한 문구를 잡아채지 않는지를 실제 픽스처로 확인한다.
 *
 * <p>픽스처에서 파서로 흘러드는 옵션 문자열을 모두 모아 하나씩 {@link StatSheetParser}에 넣고
 * 결과를 세 갈래로 분류해 리포트로 남긴다.
 * <ul>
 *   <li><b>미인식</b> — 전투 스탯 낱말이 있는데 아무 값도 안 나온 문구. 누락된 동의어 후보다.</li>
 *   <li><b>오인식</b> — 전투력과 무관한 문구인데 값이 잡힌 경우. 예: "일반 몬스터 공격 시 데미지".</li>
 *   <li>정상 인식 — 어떤 스탯으로 잡혔는지 함께 기록한다.</li>
 * </ul>
 */
@SpringBootTest
class StatParsingAuditTest {

    private static final Path REPORT_PATH = Path.of("build/reports/golden/stat-parsing-audit.txt");

    /** 파서로 실제 흘러드는 텍스트 필드만 본다. 구조화된 숫자 옵션은 대상이 아니다. */
    private static final Set<String> TEXT_FIELDS = Set.of(
            "potential_option_1", "potential_option_2", "potential_option_3",
            "additional_potential_option_1", "additional_potential_option_2", "additional_potential_option_3",
            "item_description", "soul_option", "title_description",
            "set_option", "ability_value", "stat_increase",
            "crystal_option_name_1", "crystal_option_name_2", "crystal_option_name_3",
            "union_artifact_effect", "cash_item_option", "option_value"
    );

    /** 배열 자체가 옵션 문자열 목록인 경우. */
    private static final Set<String> TEXT_ARRAY_FIELDS = Set.of(
            "union_state_stat", "union_raider_stat", "union_occupied_stat"
    );

    /** 전투력에 실제로 쓰이는 스탯 낱말. 이게 들어간 문구가 미인식이면 의심해야 한다. */
    private static final List<String> COMBAT_WORDS = List.of(
            "공격력", "마력", "올스탯", "모든 능력치", "데미지", "STR", "DEX", "INT", "LUK",
            "힘", "민첩", "지능", "행운"
    );

    /** 전투력에 들어가면 안 되는 문구. 값이 잡히면 오인식이다. */
    private static final List<String> MUST_NOT_PARSE = List.of(
            "일반 몬스터", "메소", "드롭", "경험치", "방어율 무시", "크리티컬 확률",
            "재사용", "버프 지속", "이동속도", "점프력", "스탠스", "소환수", "아케인포스", "어센틱포스"
    );

    @Autowired
    private StatSheetParser statSheetParser;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void 옵션_문구_파싱을_감사한다() throws Exception {
        Set<String> options = new TreeSet<>();
        FixtureLoader.forEach(mapper, fixture -> {
            for (JsonNode document : fixture.snapshot().documents().values()) {
                collect(document, options);
            }
        });

        List<String> unrecognized = new ArrayList<>();
        List<String> misrecognized = new ArrayList<>();
        Map<String, List<String>> recognized = new TreeMap<>();

        for (String option : options) {
            StatSheet sheet = statSheetParser.parse(List.of(option), "probe");
            String changed = changedStats(sheet);
            boolean parsed = !sheet.isZero();

            if (parsed && containsAny(option, MUST_NOT_PARSE)) {
                misrecognized.add(option + "   → " + changed);
            } else if (!parsed && containsAny(option, COMBAT_WORDS) && !containsAny(option, MUST_NOT_PARSE)) {
                unrecognized.add(option);
            } else if (parsed) {
                recognized.computeIfAbsent(changed, key -> new ArrayList<>()).add(option);
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("옵션 문구 파싱 감사\n");
        report.append("픽스처에서 수집한 고유 문구 ").append(options.size()).append("개\n\n");

        report.append("■ 오인식 — 전투력에 들어가면 안 되는데 값이 잡힘 (").append(misrecognized.size()).append("건)\n");
        misrecognized.forEach(line -> report.append("   ").append(line).append('\n'));

        report.append("\n■ 미인식 — 전투 스탯 낱말이 있는데 값이 안 잡힘 (").append(unrecognized.size()).append("건)\n");
        unrecognized.forEach(line -> report.append("   ").append(line).append('\n'));

        report.append("\n■ 정상 인식 (스탯별)\n");
        recognized.forEach((stat, list) -> {
            report.append("   [").append(stat).append("] ").append(list.size()).append("건\n");
            list.stream().limit(6).forEach(line -> report.append("      ").append(line).append('\n'));
        });

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report.toString());
        System.out.println(report);
    }

    private void collect(JsonNode node, Set<String> target) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, target);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.forEachEntry((name, value) -> {
            if (TEXT_ARRAY_FIELDS.contains(name) && value.isArray()) {
                for (JsonNode item : value) {
                    addSplit(item.asText(""), target);
                }
            } else if (TEXT_FIELDS.contains(name) && value.isString()) {
                addSplit(value.asText(""), target);
            } else {
                collect(value, target);
            }
        });
    }

    private void addSplit(String text, Set<String> target) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String part : EffectTextSplitter.split(text)) {
            if (!part.isBlank()) {
                target.add(part.trim());
            }
        }
    }

    private boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }

    /** 이 문구로 어떤 스탯이 몇 만큼 잡혔는지. */
    private String changedStats(StatSheet sheet) {
        Map<String, Number> values = new LinkedHashMap<>();
        values.put("STR", sheet.getSTR());
        values.put("DEX", sheet.getDEX());
        values.put("INT", sheet.getINT());
        values.put("LUK", sheet.getLUK());
        values.put("HP", sheet.getHP());
        values.put("ALL_STAT", sheet.getALL_STAT());
        values.put("STR%", sheet.getSTR_PERCENT());
        values.put("DEX%", sheet.getDEX_PERCENT());
        values.put("INT%", sheet.getINT_PERCENT());
        values.put("LUK%", sheet.getLUK_PERCENT());
        values.put("ALL_STAT%", sheet.getALL_STAT_PERCENT());
        values.put("ATT", sheet.getATTACK_POWER());
        values.put("MAG", sheet.getMAGIC_POWER());
        values.put("ATT%", sheet.getATTACK_POWER_PERCENT());
        values.put("MAG%", sheet.getMAGIC_POWER_PERCENT());
        values.put("DMG", sheet.getDAMAGE());
        values.put("BOSS", sheet.getBOSS_DAMAGE());
        values.put("CRIT_DMG", sheet.getCRITICAL_DAMAGE());
        values.put("FINAL_DMG", sheet.getFINAL_DAMAGE());
        values.put("STR_NP", sheet.getSTR_NO_PERCENT());
        values.put("DEX_NP", sheet.getDEX_NO_PERCENT());
        values.put("INT_NP", sheet.getINT_NO_PERCENT());
        values.put("LUK_NP", sheet.getLUK_NO_PERCENT());
        values.put("ALL_NP", sheet.getALL_STAT_NO_PERCENT());
        values.put("HP_NP", sheet.getHP_NO_PERCENT());
        values.put("STR/9", sheet.getSTR_PER_LEVEL9());
        values.put("DEX/9", sheet.getDEX_PER_LEVEL9());
        values.put("INT/9", sheet.getINT_PER_LEVEL9());
        values.put("LUK/9", sheet.getLUK_PER_LEVEL9());

        List<String> parts = new ArrayList<>();
        values.forEach((name, value) -> {
            if (value.doubleValue() != 0.0) {
                parts.add(name + "=" + value);
            }
        });
        return String.join(", ", parts);
    }
}
