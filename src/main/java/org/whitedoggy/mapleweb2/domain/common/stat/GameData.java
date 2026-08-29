package org.whitedoggy.mapleweb2.domain.common.stat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 게임 콘텐츠 데이터. 실제 값은 {@code game-data.yml}에 있다.
 *
 * <p>신규 직업이나 무기가 추가될 때마다 코드를 고치지 않도록 설정으로 뺐다.
 * 넥슨이 쓰는 이름과 글자 하나까지 같아야 한다 — 예전에 {@code 캐논슈터}로 적혀 있어
 * 실제 값인 {@code 캐논마스터}와 어긋나 해당 직업이 통째로 실패한 적이 있다.
 */
@ConfigurationProperties(prefix = "maple.game")
public record GameData(
        Map<String, JobStat> jobStats,
        List<String> magicClasses,
        Map<String, Integer> consumableAttack,
        List<String> magicWeaponParts,
        List<String> adventurePirateJobs,
        ConversionStarforce conversionStarforce,
        Map<String, String> equipmentJobGroupStat,
        List<String> setJobGroupTokens,
        Map<String, String> weaponJobGroupByStat
) {
    /** 직업의 주스탯 / 부스탯. 부스탯이 둘인 직업(섀도어·듀얼블레이더·카데나)이 있다. */
    public record JobStat(List<String> main, List<String> sub) {
    }

    /**
     * 컨버전 스타포스. 장착 장비의 스타포스 합 {@code step}성당 올스탯이
     * {@code statPerStep}씩 오른다. 합은 {@code maxStar}에서 잘리고,
     * {@code excludedSlots}(훈장·칭호)의 스타포스는 세지 않는다.
     */
    public record ConversionStarforce(
            List<String> jobs, int statPerStep, int step, int maxStar, List<String> excludedSlots) {
        public ConversionStarforce {
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
            excludedSlots = excludedSlots == null ? List.of() : List.copyOf(excludedSlots);
        }
    }

    public GameData {
        jobStats = require(jobStats, "job-stats");
        consumableAttack = consumableAttack == null ? Map.of() : Map.copyOf(consumableAttack);
        magicClasses = magicClasses == null ? List.of() : List.copyOf(magicClasses);
        magicWeaponParts = magicWeaponParts == null ? List.of() : List.copyOf(magicWeaponParts);
        adventurePirateJobs = adventurePirateJobs == null ? List.of() : List.copyOf(adventurePirateJobs);
        equipmentJobGroupStat = equipmentJobGroupStat == null
                ? Map.of() : Map.copyOf(equipmentJobGroupStat);
        setJobGroupTokens = setJobGroupTokens == null ? List.of() : List.copyOf(setJobGroupTokens);
        weaponJobGroupByStat = weaponJobGroupByStat == null
                ? Map.of() : Map.copyOf(weaponJobGroupByStat);
        conversionStarforce = conversionStarforce == null
                ? new ConversionStarforce(List.of(), 0, 10, 0, List.of())
                : conversionStarforce;
    }

    /**
     * 장비 이름이 어느 직업군의 것인지로 세트 옵션 갈래를 고른다.
     * 해당하는 키워드가 없으면 {@code null}.
     */
    public String jobGroupStatOf(String itemName) {
        if (itemName == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : equipmentJobGroupStat.entrySet()) {
            if (itemName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 세트 장비 이름의 직업군 토큰. 없으면 {@code null} — 무기처럼 직업군을 가리지 않는
     * 부위이므로 모든 직업군 세트에 함께 센다.
     */
    public String setJobGroupOf(String itemName) {
        if (itemName == null) {
            return null;
        }
        for (String token : setJobGroupTokens) {
            if (itemName.contains(token)) {
                return token;
            }
        }
        return null;
    }

    /**
     * 무기의 기본 스탯으로 직업군을 가른다. 주스탯이 여럿인 직업(제논)의 무기는
     * 이름에 직업군 토큰이 없고, 도적용은 운·해적용은 힘이 붙는다.
     *
     * @param statName 그 무기의 기본 스탯 중 가장 큰 것 (STR/DEX/INT/LUK)
     */
    public String weaponJobGroupOf(String statName) {
        return weaponJobGroupByStat.get(statName);
    }

    /** 컨버전 스타포스를 가진 직업인가 (제논·데몬어벤져). */
    public boolean hasConversionStarforce(String characterClass) {
        return conversionStarforce.jobs().contains(characterClass);
    }

    /** 컨버전 스타포스 대상이 아닌 부위인가 (훈장·칭호). */
    public boolean isConversionExcludedSlot(String slot) {
        return conversionStarforce.excludedSlots().contains(slot);
    }

    /** 스타포스 합에서 나오는 올스탯 증가량. 합은 상한에서 잘린다. */
    public int conversionStarforceAllStat(int starSum) {
        ConversionStarforce c = conversionStarforce;
        if (c.step() <= 0) {
            return 0;
        }
        return Math.min(starSum, c.maxStar()) / c.step() * c.statPerStep();
    }

    public List<String> mainStats(String characterClass) {
        return jobStat(characterClass).main();
    }

    public List<String> subStats(String characterClass) {
        return jobStat(characterClass).sub();
    }

    /** 마력을 쓰는 직업인가. */
    public boolean isMageClass(String characterClass) {
        return magicClasses.contains(characterClass);
    }

    /** 마력 기반 무기 부위인가. 무기 추가옵션을 magic_power에서 읽을지 결정한다. */
    public boolean isMagicWeaponPart(String weaponPart) {
        return magicWeaponParts.contains(weaponPart);
    }

    /** 직업별 소모품(화살·표창 등) 공격력. 해당 없으면 0. */
    public int consumableAttackOf(String characterClass) {
        return consumableAttack.getOrDefault(characterClass, 0);
    }

    /**
     * 화살·표창·총알 같은 소모 아이템을 쓰는 직업인가.
     *
     * <p>API는 소모 아이템 칸을 주지 않으므로 이 직업들은 표의 대표값을 더한다.
     * 실제로 무엇을 끼고 있는지는 알 수 없어 대표값과 다르면 그만큼 어긋난다.
     */
    public boolean usesConsumableItem(String characterClass) {
        return consumableAttack.containsKey(characterClass);
    }

    /**
     * 파이렛 블레스를 가진 모험가 해적인가.
     *
     * <p>이 스킬을 켜면 무기·보조무기·심볼·펫장비를 뺀 장착 장비의 힘과 민첩이 바뀌어
     * 적용된다. 주스탯이 민첩인 캡틴이 힘 장비를 그대로 쓰는 이른바 '힘 캡틴' 세팅이 그것이다.
     */
    public boolean isAdventurePirate(String characterClass) {
        return adventurePirateJobs.contains(characterClass);
    }

    private JobStat jobStat(String characterClass) {
        JobStat stat = jobStats.get(characterClass);
        if (stat == null) {
            throw new IllegalArgumentException(
                    "game-data.yml의 maple.game.job-stats에 없는 직업입니다: '" + characterClass
                            + "'. 넥슨 API의 character_class 값과 정확히 일치해야 합니다.");
        }
        return stat;
    }

    private static Map<String, JobStat> require(Map<String, JobStat> values, String property) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(
                    "maple.game." + property + " 설정이 비었습니다. game-data.yml을 확인하세요.");
        }
        return Map.copyOf(values);
    }

    /** 등록된 직업 이름 전체. 검증·테스트용. */
    public Set<String> jobNames() {
        return jobStats.keySet();
    }
}
