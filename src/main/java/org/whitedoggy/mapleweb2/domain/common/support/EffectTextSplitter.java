package org.whitedoggy.mapleweb2.domain.common.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class EffectTextSplitter {
    /**
     * 쉼표로 가르면 안 되는 한 줄 옵션. "STR, DEX 10" 같은 복합 스탯과 "공격력, 마력 5",
     * 그리고 칭호의 "최대 HP,MP+300"(킹 오브 루타비스) — 이건 갈라지면 "최대 HP"가 숫자 없이
     * 남아 HP 300 이 통째로 사라진다.
     */
    private static final Pattern COMBINED_OPTION_PATTERN = Pattern.compile(
            "^(?:(?:STR|DEX|INT|LUK)(?:\\s*,\\s*(?:STR|DEX|INT|LUK))+|공격력\\s*,\\s*마력|최대 HP\\s*,\\s*(?:최대\\s*)?MP)\\s*.*\\d.*$"
    );

    private EffectTextSplitter() {
    }

    public static void addSplit(List<String> target, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        for (String line : text.split("\\R")) {
            addCommaSplit(target, line.trim());
        }
    }

    public static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        addSplit(parts, text);
        return parts;
    }

    private static void addCommaSplit(List<String> target, String line) {
        if (line.isBlank()) {
            return;
        }

        if (!line.contains(",") || COMBINED_OPTION_PATTERN.matcher(line).matches()) {
            target.add(line);
            return;
        }

        for (String part : line.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                target.add(trimmed);
            }
        }
    }
}
