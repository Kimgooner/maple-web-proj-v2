package org.whitedoggy.mapleweb2.domain.combat.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class EffectTextSplitter {
    private static final Pattern COMBINED_OPTION_PATTERN = Pattern.compile(
            "^(?:(?:STR|DEX|INT|LUK)(?:\\s*,\\s*(?:STR|DEX|INT|LUK))+|공격력\\s*,\\s*마력)\\s*.*\\d.*$"
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
