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

    /** 이 세트에 존재할 수 있는 스타포스인가. 표에 값이 있다고 믿을 수 있는지를 뜻한다. */
    public boolean starForceInRange(WeaponSet set, Integer starForce) {
        return set.minStar() == null
                || (starForce == null ? 0 : starForce) >= set.minStar();
    }

    /**
     * 주문서 작 1회가 무기 공격력에 더하는 값. 주문서 자체가 9, 그만큼 올라간 기본
     * 공격력 때문에 스타포스 옵션이 다시 3 오른다.
     *
     * <p>실측(데스티니 스태프 22성): 8작 base 445 / sf 310 / etc 72,
     * 9작 base 445 / sf 313 / etc 81.
     */
    private static final int ATTACK_PER_SCROLL = 12;

    private static final String BOW_PART = "활";

    /** 제로의 라즐리·라피스 부위. 활 환산 대응이 다른 무기와 다르다. */
    private static final List<String> ZERO_PARTS = List.of("태도", "대검");

    public WeaponData {
        sets = sets == null ? List.of() : List.copyOf(sets);
        starForce = starForce == null ? Map.of() : Map.copyOf(starForce);
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
     * 주문서 작이 더하는 공격력. 표가 전제한 작 횟수와의 차이만 보정한다.
     *
     * @param scrollUpgrade 아이템의 실제 작 횟수({@code scroll_upgrade})
     */
    public int scrollAttack(WeaponSet set, Integer scrollUpgrade) {
        if (scrollUpgrade == null) {
            return set.scroll();
        }
        return set.scroll() + ATTACK_PER_SCROLL * (scrollUpgrade - set.assumedScroll());
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
