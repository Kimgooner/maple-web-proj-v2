package org.whitedoggy.mapleweb2.domain.combat.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class EffectTextSplitter {
    private static final Pattern OPTION_START_PATTERN = Pattern.compile(
            "^(?:STR|DEX|INT|LUK|HP|최대 HP|최대 HP/MP|힘|민첩|지능|행운|올스탯|모든 능력치|공격력/마력|공격력과 마력|공격력|마력|데미지|보스 몬스터 공격 시 데미지|크리티컬 데미지|최종 데미지)"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*\\d.*");

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

        String current = line;
        while (true) {
            int splitIndex = findSplitIndex(current);
            if (splitIndex < 0) {
                target.add(current.trim());
                return;
            }

            String left = current.substring(0, splitIndex).trim();
            if (!left.isBlank()) {
                target.add(left);
            }
            current = current.substring(splitIndex + 1).trim();
        }
    }

    private static int findSplitIndex(String text) {
        int index = -1;
        while (true) {
            index = text.indexOf(',', index + 1);
            if (index < 0) {
                return -1;
            }

            String left = text.substring(0, index).trim();
            String right = text.substring(index + 1).trim();
            if (NUMBER_PATTERN.matcher(left).matches() && OPTION_START_PATTERN.matcher(right).find()) {
                return index;
            }
        }
    }
}
