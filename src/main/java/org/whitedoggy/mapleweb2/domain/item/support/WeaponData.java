package org.whitedoggy.mapleweb2.domain.item.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 무기 정규화 데이터. 실제 값은 {@code game-data.yml}의 {@code maple.game.weapon}에 있다.
 *
 * <p>전투력은 무기 종류에 상관없이 <b>활 기준으로 정규화한 공격력</b>을 쓴다. 그래서 무기 자체의
 * 공격력은 버리고 (세트, 스타포스, 추가옵션)으로 다시 만든다.
 *
 * <p>주의: {@code star-force} 표에 주문서 작이 포함된 세트와 아닌 세트가 섞여 있다.
 * 아케인셰이드는 포함(→ scroll 0), 제네시스·데스티니는 미포함(→ scroll 72)이다.
 * 실측으로 확인한 값이며 세트를 추가할 때 반드시 같은 방식으로 검증해야 한다.
 *
 * <p>표가 전제하는 작 횟수는 {@code assumed-scroll}에 적는다. 아이템이 실제로 몇 번
 * 작했는지는 {@code scroll_upgrade}로 오므로, 다르면 {@link #scrollAttack}이 보정한다.
 */
@ConfigurationProperties(prefix = "maple.game.weapon")
public record WeaponData(
        String defaultSet,
        List<WeaponSet> sets,
        Map<String, List<Integer>> starForce,
        Map<String, Integer> bowBaseAttack,
        Map<String, Integer> bowItemLevel,
        Map<String, List<Integer>> starForce16plus,
        StarForceFormula starForceFormula,
        Map<String, Map<String, List<Integer>>> addOption,
        Map<String, List<Integer>> zeroBowAddOption,
        Map<String, Integer> zeroBaseAttack
) {
    /**
     * 무기 이름에 keywords 중 하나가 있으면 이 세트로 본다.
     *
     * @param minStar 이 세트에 존재하는 가장 낮은 스타포스. 제네시스는 1차 해방 때
     *                22성으로 지급되어 그 아래가 게임에 없다. 더 낮은 값이 오면 표에
     *                답이 없다는 뜻이라 정규화 실패로 본다. 없으면 제한하지 않는다.
     */
    public record WeaponSet(String name, List<String> keywords, int scroll, int assumedScroll,
                            Integer minStar) {
    }

    /**
     * 1~15성 스타포스 공격력 계산 상수. 성마다
     * {@code (현재 공격력 / divisor) + bonus}를 더한다(내림).
     */
    public record StarForceFormula(int divisor, int bonus, int baseMaxStar) {
    }

    /** 이 세트에 존재할 수 있는 스타포스인가. 표에 값이 있다고 믿을 수 있는지를 뜻한다. */
    public boolean starForceInRange(WeaponSet set, Integer starForce) {
        return set.minStar() == null
                || (starForce == null ? 0 : starForce) >= set.minStar();
    }

    /** 주문서 1회가 올리는 기본 공격력. 표의 {@code assumed-scroll}이 전제하는 값이다. */
    private static final int ATTACK_PER_SCROLL_BASE = 9;

    /**
     * 작 공격력 1당 무기 합계가 오르는 양. 주문서가 올린 공격력만큼 스타포스 옵션도
     * 따라 오르기 때문에 1보다 크다(9 -> 12, 즉 4/3).
     */
    private static final double ATTACK_PER_SCROLL_ATTACK = 4.0 / 3.0;

    private static final String BOW_PART = "활";

    /** 제로의 라즐리·라피스 부위. 활 환산 대응이 다른 무기와 다르다. */
    private static final List<String> ZERO_PARTS = List.of("태도", "대검");

    public WeaponData {
        sets = sets == null ? List.of() : List.copyOf(sets);
        starForce = starForce == null ? Map.of() : Map.copyOf(starForce);
        bowBaseAttack = bowBaseAttack == null ? Map.of() : Map.copyOf(bowBaseAttack);
        bowItemLevel = bowItemLevel == null ? Map.of() : Map.copyOf(bowItemLevel);
        starForce16plus = starForce16plus == null ? Map.of() : Map.copyOf(starForce16plus);
        starForceFormula = starForceFormula == null
                ? new StarForceFormula(50, 1, 15) : starForceFormula;
        addOption = addOption == null ? Map.of() : Map.copyOf(addOption);
        zeroBowAddOption = zeroBowAddOption == null ? Map.of() : Map.copyOf(zeroBowAddOption);
        zeroBaseAttack = zeroBaseAttack == null ? Map.of() : Map.copyOf(zeroBaseAttack);
    }

    /** 무기 이름으로 세트를 고른다. 뒤에 맞는 것이 이기고, 없으면 기본 세트(작 없음). */
    public WeaponSet resolveSet(String weaponName) {
        WeaponSet matched = null;
        for (WeaponSet set : sets) {
            for (String keyword : set.keywords()) {
                if (weaponName != null && weaponName.contains(keyword)) {
                    matched = set;
                    break;
                }
            }
        }
        if (matched != null) {
            return matched;
        }
        return new WeaponSet(defaultSet, List.of(), 0, 0, null);
    }

    /**
     * 주문서 작이 더하는 공격력. 표가 전제한 값과의 차이만 보정한다.
     *
     * <p><b>작 횟수가 아니라 실제 작 공격력({@code item_etc_option})으로 잰다.</b>
     * 주문서 종류에 따라 1회당 공격력이 달라서 횟수만으로는 알 수 없다 — 실측한
     * 아케인셰이드 보우 15성 8작은 작 공격력이 72(8x9)가 아니라 56이었고, 그 활의
     * 실제 합은 455였다(횟수 기준으로 계산하면 478이 나온다).
     *
     * <p>표는 {@code assumedScroll}회 완작(1회당 9)을 전제하므로 그 기준과의 차이에
     * {@link #ATTACK_PER_SCROLL_ATTACK}을 곱한다. 주문서가 올린 공격력만큼 스타포스
     * 옵션도 함께 오르기 때문에 1보다 크다.
     *
     * <p>실측 60건 기준으로 정확히 맞는 건수가 17건에서 29건으로 늘고 평균 오차가
     * 30에서 9.8로 줄었다. 성마다 배율이 조금씩 달라(13성 1.29 ~ 18성 1.36) 아직
     * 전부 맞지는 않는다.
     *
     * @param scrollAttackValue 아이템의 실제 작 공격력({@code item_etc_option}의 공격력/마력)
     */
    public int scrollAttack(WeaponSet set, Integer scrollAttackValue) {
        if (scrollAttackValue == null) {
            return set.scroll();
        }
        int assumed = set.assumedScroll() * ATTACK_PER_SCROLL_BASE;
        return set.scroll()
                + (int) Math.round((scrollAttackValue - assumed) * ATTACK_PER_SCROLL_ATTACK);
    }

    /**
     * 활의 총 공격력. 스타포스 규칙으로 직접 계산한다.
     *
     * <p>기본 공격력에 주문서 작 공격력을 더한 값에서 시작해, 1~15성은 성마다
     * {@code (현재 공격력 / 50) + 1}을 더하고(내림, 누적 반영), 16성 이상은 아이템
     * 레벨별 누적치를 더한다.
     *
     * <p>표를 쓰던 방식은 작이 기준(9작 81)과 다르면 어긋났다. 주문서 종류마다
     * 1회당 공격력이 9/7/5/3으로 달라 작 횟수로는 알 수 없고, 스타포스 증가량 자체가
     * 작 공격력에 비례하기 때문이다. 실측 58건이 이 규칙과 100% 일치한다.
     *
     * @param scrollAttackValue 아이템의 실제 작 공격력({@code item_etc_option})
     * @return 계산할 수 없으면 {@code null}(등록 안 된 세트, 표에 없는 스타포스)
     */
    public Integer bowAttack(String setName, int starForce, int scrollAttackValue) {
        Integer base = bowBaseAttack.get(setName);
        Integer level = bowItemLevel.get(setName);
        if (base == null || level == null) {
            return null;
        }
        StarForceFormula formula = starForceFormula;
        int attack = base + Math.max(0, scrollAttackValue);
        for (int star = 1; star <= Math.min(starForce, formula.baseMaxStar()); star++) {
            attack += attack / formula.divisor() + formula.bonus();
        }
        if (starForce <= formula.baseMaxStar()) {
            return attack;
        }
        List<Integer> plus = starForce16plus.get(String.valueOf(level));
        int index = starForce - formula.baseMaxStar() - 1;
        if (plus == null || index >= plus.size()) {
            return null;
        }
        return attack + plus.get(index);
    }

    /** 세트·스타포스에 해당하는 공격력. 표를 벗어나면 마지막 값으로 고정한다. */
    public int starForceAttack(String setName, int starForce) {
        List<Integer> table = this.starForce.get(setName);
        if (table == null || table.isEmpty()) {
            throw new IllegalStateException(
                    "game-data.yml의 maple.game.weapon.star-force에 없는 세트입니다: " + setName);
        }
        int index = Math.max(0, Math.min(starForce, table.size() - 1));
        return table.get(index);
    }

    /**
     * 무기의 추가옵션 수치가 몇 추인지 역산한다. 표에 없으면 {@code null}.
     * 공백은 무시하고 비교한다 — API는 {@code "듀얼 보우건"}, 표는 {@code "듀얼보우건"}이다.
     */
    public Integer findStage(String setName, String weaponPart, Integer addOptionValue) {
        if (addOptionValue == null || addOptionValue <= 0) {
            return null;
        }
        List<Integer> stages = stagesOf(setName, weaponPart);
        if (stages == null) {
            return null;
        }
        for (int index = 0; index < stages.size(); index++) {
            if (stages.get(index).intValue() == addOptionValue.intValue()) {
                return index + 1;
            }
        }
        return null;
    }

    /** 같은 단계일 때 활이 받는 추가옵션 수치. 정규화의 기준이다. */
    public int bowAddOption(String setName, Integer stage) {
        if (stage == null) {
            return 0;
        }
        List<Integer> stages = stagesOf(setName, BOW_PART);
        if (stages == null || stage < 1 || stage > stages.size()) {
            return 0;
        }
        return stages.get(stage - 1);
    }

    /** 제로 전용 무기의 기준 공격력. 등록되지 않은 무기면 {@code null}. */
    public Integer zeroBaseAttackOf(String weaponName) {
        return zeroBaseAttack.get(weaponName);
    }

    /** 태도·대검인가. 이 부위만 활 환산에 전용 표를 쓴다. */
    public boolean usesZeroBowAddOption(String weaponPart) {
        String normalized = normalize(weaponPart);
        return ZERO_PARTS.contains(normalized);
    }

    /**
     * 태도·대검의 활 환산 추가옵션. 표본이 없어 비워 둔 단계면 {@code null}을 준다 —
     * 호출부가 {@code weaponNormalizationFailed}를 세우도록 하기 위함이다.
     */
    public Integer zeroBowAddOptionOf(String setName, Integer stage) {
        if (stage == null) {
            return null;
        }
        List<Integer> values = zeroBowAddOption.get(setName);
        if (values == null || stage < 1 || stage > values.size()) {
            return null;
        }
        return values.get(stage - 1);
    }

    private List<Integer> stagesOf(String setName, String weaponPart) {
        Map<String, List<Integer>> parts = addOption.get(setName);
        if (parts == null) {
            return null;
        }
        String normalized = normalize(weaponPart);
        for (Map.Entry<String, List<Integer>> entry : parts.entrySet()) {
            if (normalize(entry.getKey()).equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace(" ", "").trim();
    }
}
