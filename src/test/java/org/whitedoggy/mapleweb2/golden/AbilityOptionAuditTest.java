package org.whitedoggy.mapleweb2.golden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheet;
import org.whitedoggy.mapleweb2.domain.common.support.EffectTextSplitter;
import java.util.ArrayList;
import org.whitedoggy.mapleweb2.domain.common.stat.StatSheetParser;
import org.whitedoggy.mapleweb2.external.nexon.config.NexonEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 어빌리티 옵션 문구를 하나씩 파서에 넣어 <b>실제로 무엇이 잡히는지</b> 찍는다.
 *
 * <p>{@code StatParsingAuditTest}의 "미인식" 목록은 전투 낱말 유무로 거르기 때문에
 * 어빌리티처럼 {@code parseNoPercentStat}로 들어가는 경로를 정확히 반영하지 못한다.
 * 여기서는 실제 경로(`parseNoPercentStat`)를 그대로 태운다.
 *
 * <p>결과: {@code build/reports/golden/ability-options.txt}
 */
@SpringBootTest
class AbilityOptionAuditTest {

    private static final Path REPORT = Path.of("build/reports/golden/ability-options.txt");

    @Autowired private StatSheetParser statSheetParser;
    @Autowired private ObjectMapper mapper;

    @Test
    void dumpAbilityOptions() throws Exception {
        // 문구 → (보유 캐릭터 수, 예시 보유자)
        Map<String, int[]> count = new TreeMap<>();
        Map<String, String> sample = new TreeMap<>();

        FixtureLoader.forEach(mapper, fixture -> {
            JsonNode ability = fixture.snapshot().document(NexonEndpoint.ABILITY);
            for (JsonNode info : ability.path("ability_info")) {
                String value = info.path("ability_value").asText("").trim();
                if (value.isEmpty()) {
                    continue;
                }
                count.computeIfAbsent(value, k -> new int[1])[0]++;
                sample.putIfAbsent(value, fixture.job() + " " + fixture.characterName());
            }
        });

        StringBuilder out = new StringBuilder();
        out.append("어빌리티 옵션 → 파싱 결과 (실제 경로: EffectTextSplitter + parseNoPercentStat)\n");
        out.append("고유 문구 ").append(count.size()).append("종\n\n");
        count.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(entry -> {
                    // 실제 경로: AbilityParser가 EffectTextSplitter로 쪼갠 뒤 parseNoPercentStat.
                    List<String> split = new ArrayList<>();
                    EffectTextSplitter.addSplit(split, entry.getKey());
                    StatSheet sheet = statSheetParser.parseNoPercentStat(split, "probe");
                    String parsed = describe(sheet);
                    out.append(String.format("%-5d %-6s %-52s %s%n",
                            entry.getValue()[0],
                            parsed.isEmpty() ? "미반영" : "반영",
                            entry.getKey(),
                            parsed.isEmpty() ? "(예: " + sample.get(entry.getKey()) + ")" : parsed));
                });
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, out.toString());
        System.out.println("[diagnostic] " + REPORT.toAbsolutePath());
    }

    /** StatSheet에서 0이 아닌 항목만 문자열로. */
    private String describe(StatSheet s) {
        StringBuilder b = new StringBuilder();
        add(b, "STR", s.getSTR()); add(b, "DEX", s.getDEX());
        add(b, "INT", s.getINT()); add(b, "LUK", s.getLUK());
        add(b, "올스탯", s.getALL_STAT());
        add(b, "STR%", s.getSTR_PERCENT()); add(b, "DEX%", s.getDEX_PERCENT());
        add(b, "INT%", s.getINT_PERCENT()); add(b, "LUK%", s.getLUK_PERCENT());
        add(b, "올스탯%", s.getALL_STAT_PERCENT());
        add(b, "STR고정", s.getSTR_NO_PERCENT()); add(b, "DEX고정", s.getDEX_NO_PERCENT());
        add(b, "INT고정", s.getINT_NO_PERCENT()); add(b, "LUK고정", s.getLUK_NO_PERCENT());
        add(b, "올스탯고정", s.getALL_STAT_NO_PERCENT());
        add(b, "공격력", s.getATTACK_POWER()); add(b, "마력", s.getMAGIC_POWER());
        add(b, "공격력%", s.getATTACK_POWER_PERCENT()); add(b, "마력%", s.getMAGIC_POWER_PERCENT());
        add(b, "데미지", s.getDAMAGE()); add(b, "보스", s.getBOSS_DAMAGE());
        add(b, "크뎀", s.getCRITICAL_DAMAGE()); add(b, "최종뎀", s.getFINAL_DAMAGE());
        return b.toString().trim();
    }

    private void add(StringBuilder b, String name, double value) {
        if (value != 0) {
            b.append(name).append(' ').append(value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value)).append("  ");
        }
    }
}
