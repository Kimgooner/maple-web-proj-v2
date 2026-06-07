package org.whitedoggy.mapleweb2.domain.common.stat;

import org.springframework.stereotype.Component;
import org.whitedoggy.mapleweb2.global.Jsons;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StatSheetParser {
    private static final String NUMBER = "([+-]?\\d+(?:\\.\\d+)?)";
    private static final String OPTIONAL_PERCENT = "\\s*(%)?";
    private static final String END = "\\s*(?:증가)?\\s*$";

    private final List<PatternRule> rules = List.of(
            new PatternRule(Pattern.compile("^캐릭터 기준\\s*9레벨\\s*당\\s*(STR|DEX|INT|LUK)\\s*\\+?\\s*" + NUMBER + END), this::applyStatPerLevel9),
            new PatternRule(Pattern.compile("^(공격력/마력|공격력과 마력)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyAttackMagic),
            new PatternRule(Pattern.compile("^((?:STR|DEX|INT|LUK)(?:\\s*,\\s*(?:STR|DEX|INT|LUK))+?)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyCombinedStat),
            new PatternRule(Pattern.compile("^(STR|DEX|INT|LUK)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyNamedStat),
            new PatternRule(Pattern.compile("^(힘|민첩|민첩성|지능|행운|지력|운)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyKoreanStat),
            new PatternRule(Pattern.compile("^최대 HP/MP\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyHpMp),
            new PatternRule(Pattern.compile("^(최대 HP|HP)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyHp),
            new PatternRule(Pattern.compile("^(올스탯|모든 능력치)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyAllStat),
            new PatternRule(Pattern.compile("^공격력\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyAttack),
            new PatternRule(Pattern.compile("^마력\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyMagic),
            new PatternRule(Pattern.compile("^(?:보스 몬스터 공격 시 데미지|보스 몬스터 데미지)\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyBossDamage),
            new PatternRule(Pattern.compile("^크리티컬 데미지\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyCriticalDamage),
            new PatternRule(Pattern.compile("^최종 데미지\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyFinalDamage),
            new PatternRule(Pattern.compile("^데미지\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyDamage),
            new PatternRule(Pattern.compile("^(?:영구적으로\\s*)?최종 데미지\\s*" + NUMBER + OPTIONAL_PERCENT + END), this::applyFinalDamage)
    );

    public StatSheet parse(List<String> options){
        return parse(options, false,null);
    }

    public StatSheet parseNoPercentStat(List<String> options){
        return parse(options, true,null);
    }

    public StatSheet parse(List<String> options, String sheetName) {
        return parse(options, false, sheetName);
    }

    public StatSheet parseNoPercentStat(List<String> options, String sheetName) {
        return parse(options, true, sheetName);
    }

    public StatSheet parse(List<String> options, boolean flatStatAsNoPercent, String sheetName) {
        StatSheet sheet = new StatSheet(sheetName);
        apply(options, sheet, flatStatAsNoPercent);
        return sheet;
    }

    public void apply(List<String> options, StatSheet sheet) {
        apply(options, sheet, false);
    }

    public void apply(List<String> options, StatSheet sheet, boolean flatStatAsNoPercent) {
        if (options == null || sheet == null) {
            return;
        }

        ParseContext context = new ParseContext(sheet, flatStatAsNoPercent);
        //System.out.println(sheet.getSheetName() + " -------------------------------------------");
        for (String option : options) {
            //System.out.print(option);
            String normalized = normalize(option);
            if (normalized.isBlank() || shouldSkip(normalized)) {
                //System.out.print(" -> skipped");
                //System.out.println();
                continue;
            }
            //System.out.print(" -> " + normalized);
            //System.out.println();
            applyNormalized(context, normalized);
        }
    }

    private void applyNormalized(ParseContext context, String normalized) {
        for (PatternRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(normalized);
            if (matcher.matches()) {
                rule.applier().accept(context, matcher);
                return;
            }
        }
    }

    private String normalize(String option) {
        if (option == null) {
            return "";
        }

        return option
                .replaceFirst("^[\\-•]\\s*", "")
                .replace("증가", "")
                .replace("상승", "")
                .replace(":", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean shouldSkip(String option) {
        return containsAny(option, Set.of(
                "메소",
                "드롭",
                "경험치",
                "방어율 무시",
                "상태 이상",
                "버프 지속",
                "재사용",
                "소환수 지속",
                "최대 이동속도",
                "이동속도",
                "회복",
                "스킬 사용 가능",
                "획득 영역",
                "솔 에르다",
                "몬스터파크",
                "일반 몬스터 공격 시 데미지",
                "일반 몬스터",
                "아케인포스",
                "어센틱포스",
                "일정 시간",
                "스킬 사용 시",
                "크리티컬 확률",
                "다수 공격 스킬",
                "피격 시",
                "확률로 데미지",
                "획득 심볼",
                "주간 드래곤"
        ));
    }

    private boolean containsAny(String text, Set<String> tokens) {
        return tokens.stream().anyMatch(text::contains);
    }

    private void applyAttackMagic(ParseContext context, Matcher matcher) {
        int value = intValue(matcher, 2);
        boolean percent = isPercent(matcher, 3);
        addAttack(context.sheet(), value, percent);
        addMagic(context.sheet(), value, percent);
    }

    private void applyCombinedStat(ParseContext context, Matcher matcher) {
        int value = intValue(matcher, 2);
        boolean percent = isPercent(matcher, 3);
        for (String statName : matcher.group(1).split(",")) {
            addNamedStat(context, statName.trim(), value, percent);
        }
    }

    private void applyNamedStat(ParseContext context, Matcher matcher) {
        addNamedStat(context, matcher.group(1), intValue(matcher, 2), isPercent(matcher, 3));
    }

    private void applyKoreanStat(ParseContext context, Matcher matcher) {
        String statName = switch (matcher.group(1)) {
            case "힘" -> "STR";
            case "민첩" -> "DEX";
            case "민첩성" -> "DEX";
            case "지능" -> "INT";
            case "지력" -> "INT";
            case "행운" -> "LUK";
            case "운" -> "LUK";
            default -> "";
        };
        addNamedStat(context, statName, intValue(matcher, 2), isPercent(matcher, 3));
    }

    private void applyHpMp(ParseContext context, Matcher matcher) {
        addHp(context.sheet(), intValue(matcher, 1), isPercent(matcher, 2), context.flatStatAsNoPercent());
    }

    private void applyHp(ParseContext context, Matcher matcher) {
        addHp(context.sheet(), intValue(matcher, 2), isPercent(matcher, 3), context.flatStatAsNoPercent());
    }

    private void applyAllStat(ParseContext context, Matcher matcher) {
        addAllStat(context.sheet(), intValue(matcher, 2), isPercent(matcher, 3), context.flatStatAsNoPercent());
    }

    private void applyAttack(ParseContext context, Matcher matcher) {
        addAttack(context.sheet(), intValue(matcher, 1), isPercent(matcher, 2));
    }

    private void applyMagic(ParseContext context, Matcher matcher) {
        addMagic(context.sheet(), intValue(matcher, 1), isPercent(matcher, 2));
    }

    private void applyBossDamage(ParseContext context, Matcher matcher) {
        context.sheet().setBOSS_DAMAGE(context.sheet().getBOSS_DAMAGE() + doubleValue(matcher, 1));
    }

    private void applyCriticalDamage(ParseContext context, Matcher matcher) {
        context.sheet().setCRITICAL_DAMAGE(context.sheet().getCRITICAL_DAMAGE() + doubleValue(matcher, 1));
    }

    private void applyFinalDamage(ParseContext context, Matcher matcher) {
        context.sheet().setFINAL_DAMAGE(context.sheet().getFINAL_DAMAGE() + doubleValue(matcher, 1));
    }

    private void applyDamage(ParseContext context, Matcher matcher) {
        context.sheet().setDAMAGE(context.sheet().getDAMAGE() + doubleValue(matcher, 1));
    }

    private void applyStatPerLevel9(ParseContext context, Matcher matcher) {
        String statName = matcher.group(1);
        int value = Integer.parseInt(matcher.group(2));

        switch (statName) {
            case "STR" -> context.sheet().setSTR_PER_LEVEL9(context.sheet().getSTR_PER_LEVEL9() + value);
            case "DEX" -> context.sheet().setDEX_PER_LEVEL9(context.sheet().getDEX_PER_LEVEL9() + value);
            case "INT" -> context.sheet().setINT_PER_LEVEL9(context.sheet().getINT_PER_LEVEL9() + value);
            case "LUK" -> context.sheet().setLUK_PER_LEVEL9(context.sheet().getLUK_PER_LEVEL9() + value);
        }
    }

    private int intValue(Matcher matcher, int group) {
        return (int) Math.floor(Jsons.parseDouble(matcher.group(group)));
    }

    private double doubleValue(Matcher matcher, int group) {
        return Jsons.parseDouble(matcher.group(group));
    }

    private boolean isPercent(Matcher matcher, int group) {
        return matcher.group(group) != null;
    }

    private void addNamedStat(ParseContext context, String statName, int value, boolean percent) {
        if (percent) {
            switch (statName) {
                case "STR" -> context.sheet().setSTR_PERCENT(context.sheet().getSTR_PERCENT() + value);
                case "DEX" -> context.sheet().setDEX_PERCENT(context.sheet().getDEX_PERCENT() + value);
                case "INT" -> context.sheet().setINT_PERCENT(context.sheet().getINT_PERCENT() + value);
                case "LUK" -> context.sheet().setLUK_PERCENT(context.sheet().getLUK_PERCENT() + value);
                default -> {
                }
            }
            return;
        }

        addFlatStat(context.sheet(), statName, value, context.flatStatAsNoPercent());
    }

    private void addFlatStat(StatSheet sheet, String statName, int value, boolean flatStatAsNoPercent) {
        if (flatStatAsNoPercent) {
            switch (statName) {
                case "STR" -> sheet.setSTR_NO_PERCENT(sheet.getSTR_NO_PERCENT() + value);
                case "DEX" -> sheet.setDEX_NO_PERCENT(sheet.getDEX_NO_PERCENT() + value);
                case "INT" -> sheet.setINT_NO_PERCENT(sheet.getINT_NO_PERCENT() + value);
                case "LUK" -> sheet.setLUK_NO_PERCENT(sheet.getLUK_NO_PERCENT() + value);
                default -> {
                }
            }
            return;
        }

        switch (statName) {
            case "STR" -> sheet.setSTR(sheet.getSTR() + value);
            case "DEX" -> sheet.setDEX(sheet.getDEX() + value);
            case "INT" -> sheet.setINT(sheet.getINT() + value);
            case "LUK" -> sheet.setLUK(sheet.getLUK() + value);
            default -> {
            }
        }
    }

    private void addAllStat(StatSheet sheet, int value, boolean percent, boolean flatStatAsNoPercent) {
        if (percent) {
            sheet.setALL_STAT_PERCENT(sheet.getALL_STAT_PERCENT() + value);
            return;
        }
        if (flatStatAsNoPercent) {
            sheet.setALL_STAT_NO_PERCENT(sheet.getALL_STAT_NO_PERCENT() + value);
            return;
        }
        sheet.setALL_STAT(sheet.getALL_STAT() + value);
    }

    private void addHp(StatSheet sheet, int value, boolean percent, boolean flatStatAsNoPercent) {
        if (percent) {
            sheet.setHP_PERCENT(sheet.getHP_PERCENT() + value);
            return;
        }
        if (flatStatAsNoPercent) {
            sheet.setHP_NO_PERCENT(sheet.getHP_NO_PERCENT() + value);
            return;
        }
        sheet.setHP(sheet.getHP() + value);
    }

    private void addAttack(StatSheet sheet, int value, boolean percent) {
        if (percent) {
            sheet.setATTACK_POWER_PERCENT(sheet.getATTACK_POWER_PERCENT() + value);
            return;
        }
        sheet.setATTACK_POWER(sheet.getATTACK_POWER() + value);
    }

    private void addMagic(StatSheet sheet, int value, boolean percent) {
        if (percent) {
            sheet.setMAGIC_POWER_PERCENT(sheet.getMAGIC_POWER_PERCENT() + value);
            return;
        }
        sheet.setMAGIC_POWER(sheet.getMAGIC_POWER() + value);
    }

    private record ParseContext(StatSheet sheet, boolean flatStatAsNoPercent) {
    }

    private record PatternRule(Pattern pattern, BiConsumer<ParseContext, Matcher> applier) {
    }
}
